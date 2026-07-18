// palbase.js - Pokemon Workshop: type-based base work. Pokemon deployed as Guards on your
// claimed land can be assigned a JOB. Every work cycle they consume food from
// your Food Chest and produce into your Output Chest.
//
// Jobs are gated by TYPE (a Pokemon's "work suitability"), and output scales
// with its level. This is an economy layer on top of SERP - it never touches
// SERP's own systems.
import { world, system, ItemStack } from "@minecraft/server";
import { actionMenu, confirmForm } from "./forms.js";
import { SPECIES } from "./speciesdata.js";
import { POKENAMES } from "./pokenames.js";
import { claimAt } from "./claims.js";
import { guardsOf, guardName } from "./guards.js";

const BASE = "sl:palbase";  // world: { playerId: { food:{x,y,z}, out:{x,y,z}, jobs:{gid:jobKey}, tick } }
const CYCLE = 1200;         // 60 s per work cycle
const FOOD = [
  // vanilla staples
  "minecraft:apple", "minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_chicken", "minecraft:cooked_porkchop",
  "minecraft:carrot", "minecraft:potato", "minecraft:baked_potato", "minecraft:wheat", "minecraft:sweet_berries",
  "minecraft:melon_slice", "minecraft:golden_apple",
  // SERP berries - Pokemon food, of course they eat these
  "serp:oran_berry", "serp:sitrus_berry", "serp:razz_berry", "serp:pecha_berry", "serp:lum_berry",
  "serp:mago_berry", "serp:persim_berry", "serp:rawst_berry", "serp:aspear_berry", "serp:cheri_berry",
  "serp:moomoo_milk",
];

// type ids: 1 Bug 2 Dark 3 Dragon 4 Electric 5 Fairy 6 Fighting 7 Fire 8 Flying
// 9 Ghost 10 Grass 11 Ground 12 Ice 13 Normal 14 Poison 15 Psychic 16 Rock 17 Steel 18 Water
export const JOBS = {
  mine:    { name: "\u26cf Mining",     types: [11, 16, 17],
             out: [["minecraft:cobblestone", 8], ["minecraft:coal", 3], ["minecraft:raw_iron", 2], ["minecraft:raw_copper", 2], ["serp:hard_stone", 1]],
             rare: [["minecraft:diamond", 0.035], ["minecraft:emerald", 0.02]] },
  logging: { name: "\ud83c\udf32 Logging",  types: [10, 6, 1],
             out: [["minecraft:oak_log", 6], ["minecraft:stick", 4], ["minecraft:oak_sapling", 2], ["serp:red_apricorn", 1]],
             rare: [["minecraft:honeycomb", 0.05], ["serp:leaf_stone", 0.02]] },
  farm:    { name: "\ud83c\udf31 Planting", types: [10, 5],
             out: [["minecraft:wheat", 5], ["minecraft:carrot", 3], ["minecraft:potato", 3], ["serp:oran_berry", 2], ["serp:razz_berry", 2]],
             rare: [["serp:sitrus_berry", 0.05], ["minecraft:golden_carrot", 0.02]] },
  water:   { name: "\ud83d\udca7 Watering", types: [18, 12],
             out: [["minecraft:sugar_cane", 4], ["minecraft:kelp", 3], ["minecraft:wheat_seeds", 4], ["serp:lum_berry", 1]],
             rare: [["serp:water_stone", 0.03], ["minecraft:nautilus_shell", 0.02]] },
  smelt:   { name: "\ud83d\udd25 Smelting", types: [7, 3],
             out: [["minecraft:iron_ingot", 3], ["minecraft:gold_ingot", 2], ["minecraft:charcoal", 4], ["minecraft:copper_ingot", 3]],
             rare: [["serp:fire_stone", 0.03], ["minecraft:netherite_scrap", 0.015]] },
  craft:   { name: "\u2692 Crafting",   types: [17, 15],
             out: [["minecraft:torch", 8], ["minecraft:paper", 4], ["minecraft:stick", 6], ["serp:pokeball", 2]],
             rare: [["serp:greatball", 0.06], ["serp:ultraball", 0.02]] },
  haul:    { name: "\ud83d\udce6 Hauling",  types: [13, 8],
             out: [["minecraft:chest", 1], ["minecraft:leather", 3], ["minecraft:hopper", 1], ["serp:pokeball", 3]],
             rare: [["minecraft:shulker_shell", 0.02], ["serp:exp_share", 0.02]] },
  power:   { name: "\u26a1 Power",      types: [4],
             out: [["minecraft:redstone", 8], ["minecraft:redstone_torch", 3], ["minecraft:redstone_block", 1]],
             rare: [["serp:thunder_stone", 0.03], ["minecraft:beacon", 0.008]] },
  cool:    { name: "\u2744 Cooling",    types: [12],
             out: [["minecraft:ice", 5], ["minecraft:snowball", 8], ["minecraft:packed_ice", 2]],
             rare: [["minecraft:blue_ice", 0.05], ["serp:ice_stone", 0.03]] },
  med:     { name: "\ud83d\udc8a Medicine", types: [14, 5, 15],
             out: [["serp:potion", 2], ["serp:pecha_berry", 2], ["minecraft:glass_bottle", 3], ["serp:oran_berry", 2]],
             rare: [["serp:super_potion", 0.08], ["serp:hyper_potion", 0.03], ["serp:full_heal", 0.03], ["serp:revive", 0.02]] },
  harvest: { name: "\ud83c\udf3e Harvesting", types: [10, 13, 1],
             out: [["minecraft:melon_slice", 5], ["minecraft:pumpkin", 2], ["minecraft:sweet_berries", 4], ["minecraft:bread", 2], ["serp:razz_berry", 2]],
             rare: [["serp:rare_candy", 0.02], ["minecraft:cake", 0.03]] },
  ranch:   { name: "\ud83d\udc04 Ranching", types: [13, 1, 5],
             out: [["minecraft:egg", 4], ["serp:moomoo_milk", 1], ["minecraft:white_wool", 3], ["minecraft:honey_bottle", 1]],
             rare: [["serp:pokemon_egg", 0.015], ["minecraft:golden_apple", 0.02]] },
};

function jget(k, fb) { try { return JSON.parse(world.getDynamicProperty(k) ?? fb); } catch { return JSON.parse(fb); } }
function jset(k, v) { try { world.setDynamicProperty(k, JSON.stringify(v)); } catch {} }
function baseOf(p) { const r = jget(BASE, "{}"); return r[p.id] ?? { jobs: {} }; }
function saveBase(p, b) { const r = jget(BASE, "{}"); r[p.id] = b; jset(BASE, r); }

export function typesOf(dex) {
  const sp = SPECIES[String(dex)];
  return sp ? [sp[4], sp[5]].filter(Boolean) : [];
}
export function jobsFor(dex) {
  const t = typesOf(dex);
  return Object.entries(JOBS).filter(([, j]) => j.types.some((x) => t.includes(x))).map(([k]) => k);
}

function container(dim, at) {
  try {
    const b = dim.getBlock({ x: at.x, y: at.y, z: at.z });
    return b?.getComponent("minecraft:inventory")?.container ?? null;
  } catch { return null; }
}
function takeFood(c) {
  if (!c) return false;
  for (let i = 0; i < c.size; i++) {
    const it = c.getItem(i);
    if (!it || !FOOD.includes(it.typeId)) continue;
    if (it.amount > 1) { it.amount -= 1; c.setItem(i, it); } else c.setItem(i, undefined);
    return true;
  }
  return false;
}
function deposit(c, id, n) {
  if (!c) return false;
  try { const left = c.addItem(new ItemStack(id, n)); return !left; } catch { return false; }
}

// ---------- work cycle ----------
function runCycle() {
  const r = jget(BASE, "{}");
  let dirty = false;
  for (const p of world.getAllPlayers()) {
    const b = r[p.id];
    if (!b || !b.out || !Object.keys(b.jobs ?? {}).length) continue;
    const dim = world.getDimension("overworld");
    const outC = container(dim, b.out);
    const foodC = b.food ? container(dim, b.food) : null;
    if (!outC) continue;
    const guards = guardsOf(p.id);
    let worked = 0, hungry = 0, produced = [];
    for (const g of guards) {
      const jk = b.jobs[g.gid];
      const job = JOBS[jk];
      if (!job) continue;
      if (g.downUntil > Date.now()) continue;         // defeated pals don't work
      if (!takeFood(foodC)) { hungry++; continue; }   // no food = no work
      const lvl = g.lvl ?? 5;
      const mult = 1 + Math.floor(lvl / 25);           // Lv50 = x3, Lv100 = x5
      const pick = job.out[Math.floor(Math.random() * job.out.length)];
      const qty = Math.max(1, Math.round(pick[1] * mult * (0.6 + Math.random() * 0.8)));
      if (deposit(outC, pick[0], qty)) { worked++; produced.push(qty + "x " + pick[0].split(":")[1].replace(/_/g, " ")); }
      for (const [rid, chance] of job.rare ?? []) {
        if (Math.random() < chance * mult && deposit(outC, rid, 1))
          produced.push("\u00a76" + rid.split(":")[1].replace(/_/g, " ") + "\u00a7r");
      }
    }
    if (worked || hungry) {
      b.lastReport = { worked, hungry, items: produced.slice(0, 4), at: Date.now() };
      r[p.id] = b;
      dirty = true;
      if (hungry && !worked) p.sendMessage("\u00a7e[Workshop] Your workers are \u00a7chungry\u00a7e - put food in the Food Chest!");
    }
  }
  if (dirty) jset(BASE, r);
}

export function initPalBase() {
  system.runInterval(() => { try { runCycle(); } catch {} }, CYCLE);
}

// ---------- UI ----------
function posLabel(at) { return at ? "\u00a7a" + at.x + ", " + at.y + ", " + at.z : "\u00a7cnot set"; }

export async function openPalBase(player) {
  const b = baseOf(player);
  const guards = guardsOf(player.id);
  const assigned = guards.filter((g) => b.jobs?.[g.gid]).length;
  const rep = b.lastReport && Date.now() - b.lastReport.at < 300000
    ? "\n\u00a78Last cycle: " + b.lastReport.worked + " worked" + (b.lastReport.hungry ? ", \u00a7c" + b.lastReport.hungry + " hungry" : "") + (b.lastReport.items?.length ? " \u00a78- " + b.lastReport.items.join(", ") : "")
    : "";
  const sel = await actionMenu(player, "Pokemon Workshop",
    "Workers: \u00a7b" + assigned + "\u00a7r/" + guards.length + " deployed Pokemon\nOutput Chest: " + posLabel(b.out) + "\u00a7r\nFood Chest: " + posLabel(b.food) + "\u00a7r" + rep +
    "\n\n\u00a77Each worker eats 1 food per minute and produces into the Output Chest. Jobs depend on the Pokemon's type; higher level = more output.",
    [
      { label: "Assign jobs\n\u00a78Give your deployed Pokemon work", icon: "textures/items/iron_pickaxe" },
      { label: "Set Output Chest\n\u00a78Look at a chest, then tap", icon: "textures/blocks/chest_front" },
      { label: "Set Food Chest\n\u00a78Look at a chest, then tap", icon: "textures/items/apple" },
      { label: "Work suitability guide", icon: "textures/items/book_normal" },
    ], "pokedex_orange");
  if (sel === 0) return assignJobs(player, b, guards);
  if (sel === 1 || sel === 2) return setChest(player, b, sel === 1 ? "out" : "food");
  if (sel === 3) return suitabilityGuide(player);
}

async function setChest(player, b, key) {
  let hit = null;
  try { hit = player.getBlockFromViewDirection({ maxDistance: 8, includeLiquidBlocks: false, includePassableBlocks: false }); } catch {}
  const blk = hit?.block;
  if (!blk || !blk.typeId.includes("chest") && !blk.typeId.includes("barrel"))
    return player.sendMessage("\u00a7c[Workshop] Look at a chest or barrel (within 8 blocks) and try again.");
  const c = claimAt("minecraft:overworld", blk.location.x, blk.location.z);
  if (!c || (c.owner !== player.id && !c.members.some((m) => m.id === player.id)))
    return player.sendMessage("\u00a7c[Workshop] The chest must stand on your claimed land.");
  b[key] = { x: blk.location.x, y: blk.location.y, z: blk.location.z };
  saveBase(player, b);
  player.sendMessage("\u00a7a[Workshop] " + (key === "out" ? "Output" : "Food") + " Chest set at " + blk.location.x + ", " + blk.location.y + ", " + blk.location.z + ".");
  return openPalBase(player);
}

async function assignJobs(player, b, guards) {
  if (guards.length === 0) return player.sendMessage("\u00a7c[Workshop] Deploy Pokemon first (Land Claims -> Pokemon Guards).");
  const sel = await actionMenu(player, "Assign jobs", "Tap a worker to change its job:",
    guards.map((g) => {
      const jk = b.jobs?.[g.gid];
      return { label: guardName(g) + " \u00a77Lv." + g.lvl + "\n" + (jk ? "\u00a7a" + JOBS[jk].name : "\u00a78idle"), icon: "textures/items/carrot_on_a_stick" };
    }), "pokedex_orange");
  if (sel < 0) return;
  const g = guards[sel];
  const avail = jobsFor(g.dex);
  if (avail.length === 0) {
    await actionMenu(player, guardName(g), "\u00a7cThis Pokemon's types have no work suitability.\u00a7r\nTry a Ground/Rock (mining), Grass (farming), Fire (smelting), Electric (power) Pokemon.",
      [{ label: "Back", icon: "textures/ui/buttons/bubble_no" }], "pokedex_orange");
    return assignJobs(player, b, guards);
  }
  const cur = b.jobs?.[g.gid];
  const buttons = avail.map((k) => ({ label: (k === cur ? "\u00a7a[ON] " : "") + JOBS[k].name + "\n\u00a78" + JOBS[k].out.map((o) => o[0].split(":")[1].replace(/_/g, " ")).slice(0, 3).join(", "), icon: "textures/items/iron_pickaxe" }));
  buttons.push({ label: "\u00a77No job (idle)", icon: "textures/ui/buttons/bubble_no" });
  const js = await actionMenu(player, guardName(g) + " Lv." + g.lvl, "Suitable work for its types:", buttons, "pokedex_orange");
  if (js < 0) return;
  b.jobs = b.jobs ?? {};
  if (js === avail.length) delete b.jobs[g.gid];
  else b.jobs[g.gid] = avail[js];
  saveBase(player, b);
  player.sendMessage(js === avail.length ? "\u00a7e[Workshop] " + guardName(g) + " is now idle." : "\u00a7a[Workshop] " + guardName(g) + " assigned to " + JOBS[avail[js]].name + "!");
  return assignJobs(player, baseOf(player), guards);
}

async function suitabilityGuide(player) {
  const TN = ["", "Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass", "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water"];
  const body = Object.values(JOBS).map((j) => "\u00a7f" + j.name + " \u00a78- " + j.types.map((t) => TN[t]).join(", ")).join("\n");
  await actionMenu(player, "Work suitability", body + "\n\n\u00a77Level 25+ doubles output, Lv50+ triples it. Rare bonus items drop occasionally.",
    [{ label: "Back", icon: "textures/ui/buttons/bubble_no" }], "pokedex_orange");
  return openPalBase(player);
}
