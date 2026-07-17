// jobs.js - deep jobs system (Miner / Farmer / Lumberjack), paid in SERP money.
// Not "break block -> coin" spam: base pay is small, the real game is job XP,
// perk milestones and DAILY CONTRACTS.
//   Lv5  +15% pay          Lv10 10% double-drop
//   Lv15 work buff procs    Lv20 3rd daily contract
//   Lv25 MASTER: +50% pay, server broadcast, unlocks "job level" title rules
// Anti-abuse: blocks YOU placed pay nothing; crops must be fully grown.
import { world, system, ItemStack } from "@minecraft/server";
import { actionMenu, confirmForm, dayNumber, strHash } from "./forms.js";
import { addCoins, fmt } from "./economy.js";
import { boostMult } from "./events.js";

const PROP = "sl:job"; // { cur, xp: {miner,farmer,lumber}, c: {d, list:[{i,need,done,claimed}]} }
const MAXLVL = 25;

// block -> [payTier(1-4), xp]
const JOBS = {
  miner: {
    name: "Miner", icon: "textures/items/iron_pickaxe", buff: "minecraft:haste",
    blocks: {
      "minecraft:coal_ore": [1, 4], "minecraft:deepslate_coal_ore": [1, 4],
      "minecraft:copper_ore": [1, 4], "minecraft:deepslate_copper_ore": [1, 4],
      "minecraft:iron_ore": [2, 6], "minecraft:deepslate_iron_ore": [2, 6],
      "minecraft:gold_ore": [2, 8], "minecraft:deepslate_gold_ore": [2, 8],
      "minecraft:redstone_ore": [2, 6], "minecraft:deepslate_redstone_ore": [2, 6],
      "minecraft:lapis_ore": [2, 7], "minecraft:deepslate_lapis_ore": [2, 7],
      "minecraft:diamond_ore": [4, 20], "minecraft:deepslate_diamond_ore": [4, 20],
      "minecraft:emerald_ore": [4, 22], "minecraft:deepslate_emerald_ore": [4, 22],
      "minecraft:nether_quartz_ore": [1, 4], "minecraft:nether_gold_ore": [1, 4],
    },
    dropOf: { coal: "minecraft:coal", iron: "minecraft:raw_iron", copper: "minecraft:raw_copper", gold: "minecraft:raw_gold", redstone: "minecraft:redstone", lapis: "minecraft:lapis_lazuli", diamond: "minecraft:diamond", emerald: "minecraft:emerald", quartz: "minecraft:quartz" },
    contracts: [["minecraft:coal_ore", "coal", 32, 48], ["minecraft:iron_ore", "iron", 24, 40], ["minecraft:copper_ore", "copper", 24, 40], ["minecraft:gold_ore", "gold", 12, 20], ["minecraft:redstone_ore", "redstone", 16, 28], ["minecraft:lapis_ore", "lapis", 12, 20], ["minecraft:diamond_ore", "diamond", 4, 8], ["minecraft:emerald_ore", "emerald", 3, 6]],
  },
  farmer: {
    name: "Farmer", icon: "textures/items/iron_hoe", buff: "minecraft:speed",
    // mature-only crops handled via growth state check
    blocks: {
      "minecraft:wheat": [1, 5], "minecraft:carrots": [1, 5], "minecraft:potatoes": [1, 5],
      "minecraft:beetroot": [1, 6], "minecraft:melon_block": [2, 6], "minecraft:pumpkin": [2, 6],
      "minecraft:sweet_berry_bush": [1, 4], "minecraft:nether_wart": [2, 7],
    },
    dropOf: { wheat: "minecraft:wheat", carrots: "minecraft:carrot", potatoes: "minecraft:potato", beetroot: "minecraft:beetroot", melon: "minecraft:melon_slice", pumpkin: "minecraft:pumpkin", wart: "minecraft:nether_wart" },
    contracts: [["minecraft:wheat", "wheat", 48, 96], ["minecraft:carrots", "carrots", 48, 96], ["minecraft:potatoes", "potatoes", 48, 96], ["minecraft:beetroot", "beetroot", 32, 64], ["minecraft:melon_block", "melon", 24, 48], ["minecraft:pumpkin", "pumpkin", 24, 48]],
  },
  lumber: {
    name: "Lumberjack", icon: "textures/items/iron_axe", buff: "minecraft:speed",
    blocks: {
      "minecraft:oak_log": [1, 4], "minecraft:spruce_log": [1, 4], "minecraft:birch_log": [1, 4],
      "minecraft:jungle_log": [1, 5], "minecraft:acacia_log": [1, 5], "minecraft:dark_oak_log": [1, 5],
      "minecraft:mangrove_log": [1, 5], "minecraft:cherry_log": [2, 6], "minecraft:pale_oak_log": [2, 6],
    },
    dropOf: { oak: "minecraft:oak_log", spruce: "minecraft:spruce_log", birch: "minecraft:birch_log", jungle: "minecraft:jungle_log", acacia: "minecraft:acacia_log", dark: "minecraft:dark_oak_log", mangrove: "minecraft:mangrove_log", cherry: "minecraft:cherry_log" },
    contracts: [["minecraft:oak_log", "oak", 48, 96], ["minecraft:spruce_log", "spruce", 48, 96], ["minecraft:birch_log", "birch", 48, 96], ["minecraft:jungle_log", "jungle", 32, 64], ["minecraft:dark_oak_log", "dark", 32, 64], ["minecraft:cherry_log", "cherry", 24, 48]],
  },
};
const TIER_PAY = [0, 2, 4, 7, 12]; // base coins per tier

// ---------- state ----------
function getJ(p) {
  try {
    const raw = p.getDynamicProperty(PROP);
    if (typeof raw === "string") return JSON.parse(raw);
  } catch {}
  return { cur: null, xp: { miner: 0, farmer: 0, lumber: 0 }, c: null };
}
function setJ(p, st) { try { p.setDynamicProperty(PROP, JSON.stringify(st)); } catch {} }

export function levelOf(xp) {
  let l = 1, need = 0;
  while (l < MAXLVL) { need += 50 * l; if (xp < need) break; l++; }
  return l;
}
function xpInto(xp, lvl) { let base = 0; for (let i = 1; i < lvl; i++) base += 50 * i; return [xp - base, 50 * lvl]; }
export function maxJobLevel(p) {
  const st = getJ(p);
  return Math.max(...Object.values(st.xp ?? {}).map(levelOf), 1);
}
function payMult(lvl) { return lvl >= 25 ? 1.65 : lvl >= 5 ? 1.15 : 1.0; }

// ---------- anti-abuse: player-placed blocks pay nothing ----------
const placed = new Set();
const placedOrder = [];
function key(dim, loc) { return dim.id + "|" + Math.floor(loc.x) + "|" + Math.floor(loc.y) + "|" + Math.floor(loc.z); }
function markPlaced(dim, loc) {
  const k = key(dim, loc);
  if (placed.has(k)) return;
  placed.add(k); placedOrder.push(k);
  if (placedOrder.length > 6000) placed.delete(placedOrder.shift());
}

function isMature(typeId, perm) {
  try {
    if (typeId === "minecraft:wheat" || typeId === "minecraft:carrots" || typeId === "minecraft:potatoes") return perm.getState("growth") === 7;
    if (typeId === "minecraft:beetroot") return perm.getState("growth") === 7;
    if (typeId === "minecraft:nether_wart") return perm.getState("age") === 3;
    if (typeId === "minecraft:sweet_berry_bush") return (perm.getState("growth") ?? 0) >= 2;
  } catch {}
  return true; // melon/pumpkin/logs/ores have no growth
}

// ---------- contracts ----------
function genContracts(p, st) {
  const d = dayNumber();
  if (st.c && st.c.d === d && st.c.job === st.cur) return st.c;
  const job = JOBS[st.cur];
  const lvl = levelOf(st.xp[st.cur] ?? 0);
  const n = lvl >= 20 ? 3 : 2;
  let h = strHash(p.id + ":" + st.cur + ":" + d);
  const list = [];
  const used = new Set();
  while (list.length < n) {
    h = (h * 1103515245 + 12345) >>> 0;
    const i = h % job.contracts.length;
    if (used.has(i)) continue;
    used.add(i);
    const [block, , lo, hi] = job.contracts[i];
    h = (h * 1103515245 + 12345) >>> 0;
    const need = lo + (h % (hi - lo + 1));
    list.push({ i, block, need, done: 0, claimed: false });
  }
  st.c = { d, job: st.cur, list };
  setJ(p, st);
  return st.c;
}
function contractReward(job, entry) {
  const [, , , hi] = JOBS[job].contracts[entry.i];
  const coins = Math.round(entry.need * TIER_PAY[JOBS[job].blocks[entry.block][0]] * 3);
  const xp = Math.round(entry.need * 2);
  return { coins, xp };
}

// ---------- work event ----------
function onBreak(ev) {
  const p = ev.player;
  if (!p) return;
  const typeId = ev.brokenBlockPermutation.type.id;
  const st = getJ(p);
  if (!st.cur) return;
  const job = JOBS[st.cur];
  const info = job.blocks[typeId];
  if (!info) return;
  const k = key(p.dimension, ev.block.location);
  if (placed.has(k)) { placed.delete(k); return; } // your own placed block = nothing
  if (!isMature(typeId, ev.brokenBlockPermutation)) return;

  const [tier, xpGain] = info;
  const lvlBefore = levelOf(st.xp[st.cur] ?? 0);
  const pay = Math.max(1, Math.round(TIER_PAY[tier] * payMult(lvlBefore))) * boostMult("coins");
  addCoins(p, pay);
  st.xp[st.cur] = (st.xp[st.cur] ?? 0) + xpGain;
  const lvlAfter = levelOf(st.xp[st.cur]);

  // contracts progress
  const c = genContracts(p, st);
  for (const e of c.list) {
    // deepslate/variants count toward the base ore contract
    if (!e.claimed && e.done < e.need && (typeId === e.block || typeId === e.block.replace("minecraft:", "minecraft:deepslate_"))) {
      e.done++;
      if (e.done === e.need) { p.sendMessage("\u00a76[JOB] Contract complete! Claim it in Hub -> Jobs."); try { p.playSound("random.orb"); } catch {} }
    }
  }

  // perks
  if (lvlBefore >= 10 && Math.random() < 0.10) { // double drop
    const short = Object.keys(job.dropOf).find((s) => typeId.includes(s));
    if (short) { try { p.dimension.spawnItem(new ItemStack(job.dropOf[short], 1), ev.block.location); } catch {} }
  }
  if (lvlBefore >= 15 && Math.random() < 0.15) {
    try { p.addEffect(job.buff, 600, { amplifier: 0, showParticles: false }); } catch {}
  }

  if (lvlAfter > lvlBefore) {
    p.sendMessage("\u00a76[JOB] \u00a7l" + job.name + " level " + lvlAfter + "!\u00a7r" + perkNote(lvlAfter));
    try { p.playSound("random.levelup"); } catch {}
    if (lvlAfter >= MAXLVL) world.sendMessage("\u00a76[JOB] \u00a7l" + p.name + "\u00a7r\u00a76 is now a \u00a7lMASTER " + job.name.toUpperCase() + "\u00a7r\u00a76! (+50% pay)");
  }
  try { p.onScreenDisplay.setActionBar("\u00a76+" + fmt(pay) + " \u00a77| " + job.name + " +\u00a7b" + xpGain + "xp"); } catch {}
  setJ(p, st);
}

function perkNote(l) {
  if (l === 5) return " \u00a7a+15% pay unlocked!";
  if (l === 10) return " \u00a7a10% double-drop unlocked!";
  if (l === 15) return " \u00a7awork buff procs unlocked!";
  if (l === 20) return " \u00a7a3rd daily contract unlocked!";
  if (l === 25) return " \u00a76MASTER: +50% pay!";
  return "";
}

// ---------- UI ----------
const PERKS = [[5, "+15% pay"], [10, "10% double-drop"], [15, "Work buff procs (Haste/Speed)"], [20, "3rd daily contract"], [25, "MASTER: +50% pay + title rules"]];

export async function openJobs(player) {
  const st = getJ(player);
  if (!st.cur) return pickJob(player, st);
  const job = JOBS[st.cur];
  const lvl = levelOf(st.xp[st.cur] ?? 0);
  const [into, need] = xpInto(st.xp[st.cur] ?? 0, lvl);
  const c = genContracts(player, st);
  const buttons = [];
  for (const e of c.list) {
    const r = contractReward(st.cur, e);
    const status = e.claimed ? "\u00a78[Claimed]" : e.done >= e.need ? "\u00a7a[CLAIM!]" : "\u00a7e[" + e.done + "/" + e.need + "]";
    buttons.push({ label: "Contract: " + e.block.replace("minecraft:", "").replace(/_/g, " ") + " x" + e.need + "\n" + status + " \u00a7r| \u00a76" + fmt(r.coins) + " \u00a7b+" + r.xp + "xp", icon: "textures/items/map_filled" });
  }
  buttons.push({ label: "Perks & pay info", icon: "textures/items/book_normal" });
  buttons.push({ label: "Change job\n\u00a78Levels are kept per job", icon: "textures/ui/refresh" });
  const sel = await actionMenu(player, job.name + "  Lv." + lvl + (lvl >= MAXLVL ? " \u00a76MASTER" : ""),
    "XP: \u00a7b" + into + "/" + need + "\u00a7r" + (lvl >= MAXLVL ? " (max)" : "") + "  Pay: \u00a76x" + payMult(lvl).toFixed(2) + "\u00a7r\nDaily contracts (reset every day):",
    buttons, "pokedex_green");
  if (sel < 0) return;
  if (sel < c.list.length) {
    const e = c.list[sel];
    if (e.claimed) player.sendMessage("\u00a7e[JOB] Already claimed.");
    else if (e.done < e.need) player.sendMessage("\u00a7e[JOB] Not done yet: " + e.done + "/" + e.need);
    else {
      const r = contractReward(st.cur, e);
      e.claimed = true;
      addCoins(player, r.coins * boostMult("quests"));
      st.xp[st.cur] += r.xp;
      setJ(player, st);
      player.sendMessage("\u00a7a[JOB] Contract paid: \u00a76+" + fmt(r.coins * boostMult("quests")) + " \u00a7b+" + r.xp + "xp");
      try { player.playSound("random.levelup"); } catch {}
    }
    return openJobs(player);
  }
  if (sel === c.list.length) {
    const lines = PERKS.map(([l, t]) => (lvl >= l ? "\u00a7a[Lv" + l + "] " : "\u00a78[Lv" + l + "] ") + t).join("\n");
    await actionMenu(player, job.name + " perks",
      lines + "\n\n\u00a77Base pay/block by rarity, contracts pay the big money. Blocks you placed yourself pay \u00a7cnothing\u00a77; crops must be fully grown.",
      [{ label: "Back", icon: "textures/ui/buttons/bubble_no" }], "pokedex_green");
    return openJobs(player);
  }
  return pickJob(player, st);
}

async function pickJob(player, st) {
  const sel = await actionMenu(player, "Pick your job", "One active job. XP and levels are saved per job - switch any time.",
    Object.entries(JOBS).map(([k, j]) => ({
      label: j.name + "  \u00a7bLv." + levelOf(st.xp[k] ?? 0) + (st.cur === k ? " \u00a7a(current)" : ""), icon: j.icon,
    })), "pokedex_green");
  if (sel < 0) return;
  const k = Object.keys(JOBS)[sel];
  if (st.cur === k) return openJobs(player);
  st.cur = k;
  st.c = null; // fresh contracts for the new job
  setJ(player, st);
  player.sendMessage("\u00a7a[JOB] You are now a \u00a7l" + JOBS[k].name + "\u00a7r\u00a7a (Lv." + levelOf(st.xp[k] ?? 0) + ")");
  return openJobs(player);
}

export function initJobs() {
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    try { if (ev.player && ev.block) markPlaced(ev.player.dimension, ev.block.location); } catch {}
  });
  world.afterEvents.playerBreakBlock.subscribe((ev) => { try { onBreak(ev); } catch {} });
}
