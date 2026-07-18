// buddy.js - Partner Skills. Pick one Pokemon from your team/PC as your Buddy.
// While it's active you get a type-based passive, mining assistance and an
// item magnet - Palworld's "partner skill" idea, done with the stable API.
import { world, system } from "@minecraft/server";
import { actionMenu } from "./forms.js";
import { getParty, getPC, level as lvlOf, isShiny, displayName } from "./serpdata.js";
import { SPECIES } from "./speciesdata.js";
import { POKENAMES } from "./pokenames.js";
import { isLow } from "./perf.js";

const BUD = "sl:buddy"; // per-player dp: { dex, lvl, name }

// type -> partner skill
const SKILLS = {
  4:  { n: "\u26a1 Voltage",     d: "Speed I while walking", eff: ["minecraft:speed", 0] },
  6:  { n: "\ud83d\udc4a Power Fist", d: "Strength I in combat", eff: ["minecraft:strength", 0] },
  7:  { n: "\ud83d\udd25 Ember Coat", d: "Fire resistance", eff: ["minecraft:fire_resistance", 0] },
  8:  { n: "\ud83e\udeb6 Updraft",    d: "Slow falling - glide down safely", eff: ["minecraft:slow_falling", 0] },
  18: { n: "\ud83d\udca7 Aqua Lung",  d: "Water breathing", eff: ["minecraft:water_breathing", 0] },
  2:  { n: "\ud83c\udf12 Night Eye",  d: "Night vision", eff: ["minecraft:night_vision", 0] },
  9:  { n: "\ud83d\udc7b Phase",      d: "Night vision", eff: ["minecraft:night_vision", 0] },
  17: { n: "\ud83d\udee1 Iron Hide",  d: "Resistance I", eff: ["minecraft:resistance", 0] },
  16: { n: "\ud83e\udea8 Stone Sense", d: "Haste I - mine faster", eff: ["minecraft:haste", 0] },
  11: { n: "\u26cf Excavator",   d: "Haste I - mine faster", eff: ["minecraft:haste", 0] },
  10: { n: "\ud83c\udf3f Verdant",    d: "Regeneration (slow)", eff: ["minecraft:regeneration", 0] },
  5:  { n: "\u2728 Fae Luck",    d: "Extra drops from blocks", eff: null },
  12: { n: "\u2744 Frost Step",  d: "Fire resistance", eff: ["minecraft:fire_resistance", 0] },
  15: { n: "\ud83d\udd2e Mind Link",  d: "Item magnet range +50%", eff: null },
  3:  { n: "\ud83d\udc09 Draconic",   d: "Strength I + Resistance I", eff: ["minecraft:strength", 0] },
  1:  { n: "\ud83d\udc1b Swarm",      d: "Extra drops from blocks", eff: null },
  13: { n: "\ud83d\udc3e Loyal",      d: "Item magnet range +50%", eff: null },
  14: { n: "\u2620 Toxin",       d: "Poison immunity aura", eff: null },
};
const MAGNET_TYPES = [15, 13];
const LUCK_TYPES = [5, 1];

function typesOf(dex) {
  const sp = SPECIES[String(dex)];
  return sp ? [sp[4], sp[5]].filter(Boolean) : [];
}
export function buddyOf(p) { try { return JSON.parse(p.getDynamicProperty(BUD) ?? "null"); } catch { return null; } }
function setBuddy(p, b) { try { p.setDynamicProperty(BUD, b ? JSON.stringify(b) : undefined); } catch {} }

export function buddySkills(p) {
  const b = buddyOf(p);
  if (!b) return [];
  return typesOf(b.dex).map((t) => SKILLS[t]).filter(Boolean);
}
export function buddyLuck(p) {
  const b = buddyOf(p);
  if (!b) return 0;
  const t = typesOf(b.dex);
  const base = t.some((x) => LUCK_TYPES.includes(x)) ? 0.18 : 0.06;
  return base + Math.min(0.12, (b.lvl ?? 5) / 800); // up to ~30% at Lv100
}
export function buddyMagnet(p) {
  const b = buddyOf(p);
  if (!b) return 0;
  const t = typesOf(b.dex);
  return t.some((x) => MAGNET_TYPES.includes(x)) ? 9 : 6;
}

export function initBuddy() {
  // passive effects, refreshed every 4 s
  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      try {
        const skills = buddySkills(p);
        if (!skills.length) continue;
        for (const s of skills) {
          if (!s.eff) continue;
          p.addEffect(s.eff[0], 120, { amplifier: s.eff[1], showParticles: false });
        }
      } catch {}
    }
  }, 80);

  // item magnet
  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      try {
        if (isLow(p)) continue;
        const r = buddyMagnet(p);
        if (!r) continue;
        for (const it of p.dimension.getEntities({ location: p.location, maxDistance: r, type: "minecraft:item" })) {
          try { it.teleport({ x: p.location.x, y: p.location.y + 0.2, z: p.location.z }); } catch {}
        }
      } catch {}
    }
  }, 15);

  // mining assist: bonus drops
  world.afterEvents.playerBreakBlock.subscribe((ev) => {
    try {
      const p = ev.player;
      const luck = buddyLuck(p);
      if (!luck || Math.random() > luck) return;
      const perm = ev.brokenBlockPermutation;
      let stack = null;
      try { stack = perm.getItemStack(1); } catch {}
      if (!stack) return;
      p.dimension.spawnItem(stack, ev.block.location);
      try { p.onScreenDisplay.setActionBar("\u00a7d\u2728 Buddy bonus drop!"); } catch {}
    } catch {}
  });
}

export async function openBuddy(player) {
  const cur = buddyOf(player);
  const box = [
    ...getParty(player).map((x) => ({ ...x, from: "team" })),
    ...getPC(player).map((x) => ({ ...x, from: "pc" })),
  ];
  if (box.length === 0) return player.sendMessage("\u00a7c[Buddy] You have no Pokemon yet.");
  const skillLine = (dex) => typesOf(dex).map((t) => SKILLS[t]?.n).filter(Boolean).join(" + ") || "\u00a78no skill";
  const buttons = box.slice(0, 22).map(({ fields, from }) => {
    const dex = Number(fields[2]);
    const nm = (typeof displayName(fields) === "string" && displayName(fields)) || POKENAMES[String(dex)] || ("#" + dex);
    const on = cur && cur.dex === dex && cur.lvl === lvlOf(fields);
    return { label: (on ? "\u00a7a[ACTIVE] \u00a7r" : "") + (from === "team" ? "\u00a7a[team] \u00a7r" : "") + nm + " Lv." + lvlOf(fields) + (isShiny(fields) ? " \uE132" : "") + "\n\u00a78" + skillLine(dex), icon: "textures/items/carrot_on_a_stick" };
  });
  buttons.push({ label: "\u00a77Dismiss buddy", icon: "textures/ui/buttons/bubble_no" });
  const sel = await actionMenu(player, "Buddy",
    (cur ? "Active: \u00a7b" + cur.name + " Lv." + cur.lvl + "\u00a7r\n\u00a78" + skillLine(cur.dex) + "\n\n" : "") +
    "Your buddy grants a passive skill, pulls dropped items to you, and rolls bonus drops when you mine.",
    buttons, "pokedex_purple");
  if (sel < 0) return;
  if (sel === buttons.length - 1) {
    setBuddy(player, null);
    player.sendMessage("\u00a7e[Buddy] Dismissed.");
    return;
  }
  const { fields } = box[sel];
  const dex = Number(fields[2]);
  const nm = (typeof displayName(fields) === "string" && displayName(fields)) || POKENAMES[String(dex)] || ("#" + dex);
  setBuddy(player, { dex, lvl: lvlOf(fields), name: nm });
  player.sendMessage("\u00a7a[Buddy] \u00a7l" + nm + "\u00a7r\u00a7a is now your buddy! " + skillLine(dex));
  try { player.playSound("random.levelup"); } catch {}
}
