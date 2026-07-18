// events.js - admin-run server events (no new currency, no new items):
//   x2 kill coins / x2 quest rewards for a chosen duration, broadcast + countdown
//   Mystery Gift: one active gift every player can claim once (real Pokemon
//   with proper learnset moves via dxgive, optional shiny)
import { world, system } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm } from "./forms.js";
import { POKENAMES } from "./pokenames.js";
import { givePokemon } from "./dxgive.js";
import { suppressCatch } from "./tracker.js";
import { fmt } from "./economy.js";
import { rewardsCfg, setRewardsCfg, rankLabel, daysLeft, seasonEnabled, setSeasonEnabled } from "./season.js";
import { allTitles } from "./titles.js";
import { milestoneCfg, setMilestoneCfg, milestoneLabel } from "./daily.js";
import { SERP_ITEMS } from "./items.js";
import { VANILLA_ITEMS } from "./vanillaitems.js";
const SEARCHABLE_ITEMS = [...SERP_ITEMS, ...VANILLA_ITEMS];

function jget(k, fb) { try { return JSON.parse(world.getDynamicProperty(k) ?? fb); } catch { return JSON.parse(fb); } }
function jset(k, v) { try { world.setDynamicProperty(k, JSON.stringify(v)); } catch {} }

// ---------- x2 boost ----------
const BOOST = "sl:boost"; // { t: "coins"|"quests", until }

export function boostMult(type) {
  const b = jget(BOOST, "null");
  return b && b.t === type && b.until > Date.now() ? 2 : 1;
}
function boostInfo() {
  const b = jget(BOOST, "null");
  if (!b || b.until <= Date.now()) return null;
  const mins = Math.ceil((b.until - Date.now()) / 60000);
  return { ...b, mins };
}

async function openBoostAdmin(admin) {
  const cur = boostInfo();
  if (cur) {
    const sel = await actionMenu(admin, "x2 Event running",
        "§a" + (cur.t === "coins" ? "x2 KILL COINS" : "x2 QUEST REWARDS") + "§r - " + cur.mins + " min left",
        [{ label: "§cEnd event now", icon: "textures/ui/buttons/bubble_no" }], "pokedex_orange");
    if (sel === 0) {
      jset(BOOST, null);
      world.sendMessage("§6[EVENT] §fThe x2 event has ended. Thanks for playing!");
    }
    return;
  }
  const t = await actionMenu(admin, "Start x2 Event", "Pick the boost:", [
    { label: "x2 Kill Coins\n§8Wild Pokemon defeats pay double", icon: "textures/items/emerald" },
    { label: "x2 Quest Rewards\n§8Daily quest coins doubled", icon: "textures/items/book_written" },
  ], "pokedex_orange");
  if (t < 0) return;
  const HOURS = [1, 2, 4, 8, 24];
  const h = await actionMenu(admin, "Duration", "How long?",
      HOURS.map((n) => ({ label: n + " hour" + (n > 1 ? "s" : ""), icon: "textures/items/clock_item" })), "pokedex_orange");
  if (h < 0) return;
  const type = t === 0 ? "coins" : "quests";
  jset(BOOST, { t: type, until: Date.now() + HOURS[h] * 3600000 });
  world.sendMessage("§6[EVENT] §l" + (type === "coins" ? "x2 KILL COINS" : "x2 QUEST REWARDS") + "§r§6 for the next §f" + HOURS[h] + "h§6! Go go go!");
  for (const p of world.getAllPlayers()) { try { p.playSound("random.levelup"); } catch {} }
}

// ---------- Mystery Gift ----------
const MGIFT = "sl:mgift"; // { id, dex, lvl, shiny, note, until }
const CLAIMED = "sl:mgclaim"; // per-player: [giftId,...]

export function activeGift() {
  const g = jget(MGIFT, "null");
  return g && g.until > Date.now() ? g : null;
}
export function giftClaimed(player, g) {
  try { return JSON.parse(player.getDynamicProperty(CLAIMED) ?? "[]").includes(g.id); } catch { return false; }
}

export async function claimMysteryGift(player) {
  const g = activeGift();
  if (!g) return player.sendMessage("§e[SunHub] No Mystery Gift active right now.");
  if (giftClaimed(player, g)) return player.sendMessage("§e[SunHub] You already claimed this gift.");
  const name = POKENAMES[String(g.dex)] ?? ("#" + g.dex);
  if (!(await confirmForm(player, "Mystery Gift!\n\n§l" + name + " Lv." + g.lvl + (g.shiny ? " §6SHINY \uE132" : "") + "§r" + (g.note ? "\n§7\"" + g.note + "\"§r" : "") + "\n\nClaim it?"))) return;
  suppressCatch(player.id);
  const r = givePokemon(player, g.dex, g.lvl, { shiny: g.shiny });
  if (!r.ok) return player.sendMessage("§c[SunHub] Team and PC are full - make space first!");
  let cl = [];
  try { cl = JSON.parse(player.getDynamicProperty(CLAIMED) ?? "[]"); } catch {}
  cl.push(g.id);
  player.setDynamicProperty(CLAIMED, JSON.stringify(cl));
  if (g.titleId) {
    const tmod = await import("./titles.js");
    tmod.grantById(player, g.titleId, true);
    player.sendMessage("\u00a7d[SunHub] A title came with the gift - equip it in Hub -> Titles!");
  }
  player.sendMessage("§a[SunHub] §l" + name + "§r§a arrived in your " + (r.where === "team" ? "team" : "PC") + "! Enjoy!");
  try { player.playSound("random.levelup"); } catch {}
}

async function openGiftAdmin(admin) {
  const g = activeGift();
  const buttons = [{ label: "Create Mystery Gift\n§8Everyone claims once", icon: "textures/items/cake" }];
  if (g) buttons.push({ label: "§cEnd current gift\n§8" + (POKENAMES[String(g.dex)] ?? g.dex) + " Lv." + g.lvl, icon: "textures/ui/buttons/bubble_no" });
  const sel = await actionMenu(admin, "Mystery Gift", g ? "Active: §l" + (POKENAMES[String(g.dex)] ?? g.dex) + " Lv." + g.lvl + (g.shiny ? " SHINY" : "") : "No active gift.", buttons, "pokedex_orange");
  if (sel < 0) return;
  if (sel === 1 && g) { jset(MGIFT, null); admin.sendMessage("§e[SunHub] Mystery Gift ended."); return; }
  if (sel !== 0) return;
  const mf = new ModalFormData().title("Create Mystery Gift");
  mf.textField("Species name:", "e.g. Eevee");
  mf.slider("Level", 1, 100, { valueStep: 1, defaultValue: 20 });
  mf.toggle("Shiny", { defaultValue: false });
  mf.slider("Active for (days)", 1, 14, { valueStep: 1, defaultValue: 3 });
  const ts = allTitles();
  mf.dropdown("Attach a title:", ["(no title)", ...ts.map((t) => t.text)], { defaultValueIndex: 0 });
  mf.textField("Note (optional):", "e.g. Happy 1st anniversary!");
  let res;
  try { res = await mf.show(admin); } catch { return; }
  if (res.canceled) return;
  const [nameQ, lvl, shiny, days, titleI, note] = res.formValues;
  const q = String(nameQ).trim().toLowerCase();
  const hit = Object.entries(POKENAMES).find(([, n]) => n.toLowerCase() === q) ??
              Object.entries(POKENAMES).find(([, n]) => n.toLowerCase().startsWith(q));
  if (!hit) return admin.sendMessage("§c[SunHub] Unknown species: " + nameQ);
  const titleId = Number(titleI) > 0 ? ts[Number(titleI) - 1].id : null;
  const gift = { id: "g" + Date.now(), dex: Number(hit[0]), lvl: Number(lvl), shiny: !!shiny, titleId, note: String(note).trim(), until: Date.now() + Number(days) * 86400000 };
  jset(MGIFT, gift);
  const nm = POKENAMES[hit[0]];
  world.sendMessage("§d[MYSTERY GIFT] §fA gift is live: §l" + nm + " Lv." + gift.lvl + (gift.shiny ? " §6SHINY \uE132" : "") + "§r§f! Claim it in the Hub -> Redeem Gift (" + days + " days).");
  for (const p of world.getAllPlayers()) { try { p.playSound("random.levelup"); } catch {} }
}

function findSpecies(q) {
  q = String(q).trim().toLowerCase();
  if (!q) return null;
  return Object.entries(POKENAMES).find(([, n]) => n.toLowerCase() === q) ??
         Object.entries(POKENAMES).find(([, n]) => n.toLowerCase().startsWith(q));
}
function findItem(q) {
  q = String(q).trim().toLowerCase();
  if (!q) return null;
  return SEARCHABLE_ITEMS.find((i) => i.name.toLowerCase() === q) ??
         SEARCHABLE_ITEMS.find((i) => i.name.toLowerCase().startsWith(q)) ??
         SEARCHABLE_ITEMS.find((i) => i.name.toLowerCase().includes(q));
}

async function configRank(admin, cfg, rankKey, rankName) {
  const cur = cfg[rankKey];
  const mf = new ModalFormData().title("Season prize for " + rankName + " (per board)");
  mf.textField("Coins:", "e.g. 3000", { defaultValue: String(cur.coins ?? 0) });
  mf.textField("Item name (blank = none):", "e.g. Masterball", { defaultValue: cur.item ? cur.item.name : "" });
  mf.slider("Item quantity", 1, 32, { valueStep: 1, defaultValue: cur.item ? cur.item.qty : 1 });
  mf.textField("Pokemon species (blank = none):", "e.g. Eevee", { defaultValue: cur.poke ? (POKENAMES[String(cur.poke.dex)] ?? "") : "" });
  mf.slider("Pokemon level", 1, 100, { valueStep: 1, defaultValue: cur.poke ? cur.poke.lvl : 20 });
  mf.toggle("Pokemon shiny", { defaultValue: cur.poke ? !!cur.poke.shiny : false });
  let res;
  try { res = await mf.show(admin); } catch { return; }
  if (res.canceled) return;
  const [coinsV, itemQ, qty, pokeQ, lvl, shiny] = res.formValues;
  const rk = { coins: Math.max(0, Math.floor(Number(coinsV) || 0)) };
  if (String(itemQ).trim()) {
    const it = findItem(itemQ);
    if (!it) return admin.sendMessage("\u00a7c[SunHub] Unknown item: " + itemQ);
    rk.item = { id: it.id, name: it.name, qty: Number(qty) };
  }
  if (String(pokeQ).trim()) {
    const sp = findSpecies(pokeQ);
    if (!sp) return admin.sendMessage("\u00a7c[SunHub] Unknown species: " + pokeQ);
    rk.poke = { dex: Number(sp[0]), lvl: Number(lvl), shiny: !!shiny };
  }
  cfg[rankKey] = rk;
  setRewardsCfg(cfg);
  admin.sendMessage("\u00a7a[SunHub] " + rankName + " prize set: " + rankLabel(admin, rk) + " \u00a77(paid on weekly reset - " + daysLeft() + "d left)");
  world.sendMessage("\u00a7d[SEASON] \u00a7fWeekly prize updated - \u00a76" + rankName + ": " + rankLabel(admin, rk) + "\u00a7f per board. Race is on!");
}

async function configMilestone(admin, cfg, key, name) {
  const cur = cfg[key];
  const mf = new ModalFormData().title("Streak gift: " + name);
  mf.textField("Pokemon species (blank = disable):", "e.g. Eevee", { defaultValue: cur ? (POKENAMES[String(cur.dex)] ?? "") : "" });
  mf.slider("Level", 1, 100, { valueStep: 1, defaultValue: cur ? cur.lvl : 15 });
  mf.toggle("Shiny", { defaultValue: cur ? !!cur.shiny : false });
  let res;
  try { res = await mf.show(admin); } catch { return; }
  if (res.canceled) return;
  const [pokeQ, lvl, shiny] = res.formValues;
  if (!String(pokeQ).trim()) {
    delete cfg[key];
    setMilestoneCfg(cfg);
    return admin.sendMessage("\u00a7e[SunHub] " + name + " streak gift disabled.");
  }
  const sp = findSpecies(pokeQ);
  if (!sp) return admin.sendMessage("\u00a7c[SunHub] Unknown species: " + pokeQ);
  cfg[key] = { dex: Number(sp[0]), lvl: Number(lvl), shiny: !!shiny };
  setMilestoneCfg(cfg);
  admin.sendMessage("\u00a7a[SunHub] " + name + " streak gift set: " + milestoneLabel(cfg[key]));
  world.sendMessage("\u00a7d[STREAK] \u00a7fCheck-in streak gift updated - \u00a76" + name + ": " + milestoneLabel(cfg[key]) + "\u00a7f. Keep the streak alive!");
}

async function openStreakAdmin(admin) {
  const cfg = milestoneCfg();
  const sel = await actionMenu(admin, "Streak Rewards", "Pokemon gifts for daily check-in streaks (m30 repeats every 30 days):", [
    { label: "7-day streak\n\u00a78" + milestoneLabel(cfg.m7), icon: "textures/items/clock_item" },
    { label: "14-day streak\n\u00a78" + milestoneLabel(cfg.m14), icon: "textures/items/clock_item" },
    { label: "30-day streak (repeats)\n\u00a78" + milestoneLabel(cfg.m30), icon: "textures/items/nether_star" },
  ], "pokedex_orange");
  if (sel < 0) return;
  const keys = [["m7", "7-day"], ["m14", "14-day"], ["m30", "30-day"]];
  return configMilestone(admin, cfg, keys[sel][0], keys[sel][1]);
}

async function openSeasonAdmin(admin) {
  const cfg = rewardsCfg();
  const on = seasonEnabled();
  const sel = await actionMenu(admin, "Season Rewards",
    (on ? "\u00a7aLeaderboards: ON\u00a7r" : "\u00a7cLeaderboards: OFF\u00a7r") + " - per board, paid every Monday.", [
    { label: on ? "\u00a7cTurn leaderboards OFF\n\u00a78Pause race, resets & payouts" : "\u00a7aTurn leaderboards ON\n\u00a78Fresh race starts immediately", icon: "textures/items/redstone_dust" },
    { label: "\u00a76#1 prize\n\u00a78" + rankLabel(admin, cfg.r1), icon: "textures/items/gold_ingot" },
    { label: "\u00a77#2 prize\n\u00a78" + rankLabel(admin, cfg.r2), icon: "textures/items/iron_ingot" },
    { label: "\u00a7c#3 prize\n\u00a78" + rankLabel(admin, cfg.r3), icon: "textures/items/brick" },
  ], "pokedex_orange");
  if (sel < 0) return;
  if (sel === 0) return setSeasonEnabled(!on);
  const keys = [["r1", "#1"], ["r2", "#2"], ["r3", "#3"]];
  return configRank(admin, cfg, keys[sel - 1][0], keys[sel - 1][1]);
}

export async function openEventsAdmin(admin) {
  const b = boostInfo();
  const cfg = rewardsCfg();
  const sel = await actionMenu(admin, "Events & Gifts", b ? "§ax2 event running (" + b.mins + " min left)" : "Run something fun:", [
    { label: "x2 Events\n§8Double coins or quest rewards", icon: "textures/items/emerald" },
    { label: "Mystery Gift\n§8Free Pokemon for everyone", icon: "textures/items/cake" },
    { label: "Season Rewards\n§8#1: " + rankLabel(admin, cfg.r1), icon: "textures/items/gold_ingot" },
    { label: "Streak Rewards\n§8Check-in milestone Pokemon", icon: "textures/items/clock_item" },
  ], "pokedex_orange");
  if (sel === 0) return openBoostAdmin(admin);
  if (sel === 1) return openGiftAdmin(admin);
  if (sel === 2) return openSeasonAdmin(admin);
  if (sel === 3) return openStreakAdmin(admin);
}

// countdown watcher: announce when a boost ends
export function initEvents() {
  let wasOn = false;
  system.runInterval(() => {
    const on = !!boostInfo();
    if (wasOn && !on) world.sendMessage("§6[EVENT] §fThe x2 event has ended. Thanks for playing!");
    wasOn = on;
  }, 200);
}
