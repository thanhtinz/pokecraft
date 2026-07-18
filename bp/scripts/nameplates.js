// nameplates.js - floating labels above nearby Pokemon and SERP NPCs.
// SERP's entities have no "always_show" nameable, so we hover our own
// invisible sl:text entity (always_show = true) above each one.
//
// Strictly budgeted: only the N closest entities within RANGE of any player
// get a plate, plates are pooled/reused, and everything is cleaned up when
// the subject dies or walks away. Toggle per player in Hub -> Settings.
import { world, system } from "@minecraft/server";
import { POKENAMES } from "./pokenames.js";
import { SPECIES } from "./speciesdata.js";
import { isLow } from "./perf.js";

const TYPE_NAMES = ["", "Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass", "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water"];

const RANGE = 14;      // blocks
const MAX_PLATES = 12; // hard cap server-wide
const TAG = "sl_plate";
const OFF = "sl:nopl";  // per-player dp: "1" = plates hidden for me

const NPCS = {
  "serp:joy": "\u00a7dNurse Joy \u00a77(heal)",
  "serp:jenny": "\u00a79Officer Jenny \u00a77(help)",
  "serp:store": "\u00a76Shopkeeper \u00a77(shop)",
  "serp:profs": "\u00a7aProfessor",
  "serp:game_corner": "\u00a7eGame Corner",
  "serp:poke_npc": "\u00a7fVillager",
  "serp:trainer": "\u00a7cTrainer \u00a77(tap to battle)",
};

export function platesOn(p) { try { return p.getDynamicProperty(OFF) !== "1"; } catch { return true; } }
export function setPlates(p, on) { try { p.setDynamicProperty(OFF, on ? undefined : "1"); } catch {} }

// pokemon:pNNN -> "Pikachu Lv.12" (level unknown for wild: SERP stores it on
// the player's tag, not the entity - so we show species + shiny + size only)
function labelFor(e) {
  const t = e.typeId;
  if (NPCS[t]) return NPCS[t];
  if (!t.startsWith("pokemon:p")) return null;
  const dex = t.slice(9).replace(/[^0-9].*$/, "");
  const name = POKENAMES[dex];
  if (!name) return null;
  let shiny = false;
  try { shiny = Number(e.getProperty("serp:v")) > 2; } catch {}
  const sp = SPECIES[dex];              // [chainLen, chainPos, moves, stats, t1, t2]
  let types = "";
  try {
    const t1 = TYPE_NAMES?.[sp?.[4]], t2 = TYPE_NAMES?.[sp?.[5]];
    if (t1) types = " \u00a78" + t1 + (t2 && t2 !== t1 ? "/" + t2 : "");
  } catch {}
  return (shiny ? "\u00a76\uE132 " : "\u00a7b") + name + "\u00a7r" + types;
}

function plateTag(e) { return "slp_" + e.id; }

export function initNameplates() {
  system.runInterval(() => {
    try {
      const players = world.getAllPlayers().filter((p) => platesOn(p) && !isLow(p));
      if (players.length === 0) {
        // nobody wants plates (all off / battery saver) - clean the existing ones
        try {
          for (const pl of world.getDimension("overworld").getEntities({ tags: [TAG] })) { try { pl.remove(); } catch {} }
        } catch {}
        return;
      }
      const dim = world.getDimension("overworld");
      // wanted: closest subjects to any player, capped
      const wanted = new Map(); // subjectId -> { e, label, d }
      for (const p of players) {
        let near = [];
        try { near = [...p.dimension.getEntities({ location: p.location, maxDistance: RANGE, excludeTypes: ["minecraft:player", "minecraft:item", "sl:text"] })]; } catch { continue; }
        for (const e of near) {
          if (!e.isValid) continue;
          const et = e.getTags();
          if (et.includes("sl_guard")) continue; // guards have their own plate
          const label = labelFor(e);
          if (!label) continue;
          const d = (e.location.x - p.location.x) ** 2 + (e.location.z - p.location.z) ** 2;
          const prev = wanted.get(e.id);
          if (!prev || d < prev.d) wanted.set(e.id, { e, label, d });
        }
      }
      const top = [...wanted.values()].sort((a, b) => a.d - b.d).slice(0, MAX_PLATES);
      const keep = new Set(top.map((x) => "slp_" + x.e.id));

      // reap plates that are no longer wanted
      let plates = [];
      try { plates = [...dim.getEntities({ tags: [TAG] })]; } catch {}
      const existing = new Map();
      for (const pl of plates) {
        const tag = pl.getTags().find((t) => t.startsWith("slp_"));
        if (!tag || !keep.has(tag)) { try { pl.remove(); } catch {} continue; }
        existing.set(tag, pl);
      }
      // place / update
      for (const { e, label } of top) {
        const tag = plateTag(e);
        const pos = { x: e.location.x, y: e.location.y + 1.9, z: e.location.z };
        let pl = existing.get(tag);
        if (!pl) {
          try {
            pl = e.dimension.spawnEntity("sl:text", pos);
            pl.addTag(TAG);
            pl.addTag(tag);
          } catch { continue; }
        } else {
          try { pl.teleport(pos); } catch {}
        }
        try { if (pl.nameTag !== label) pl.nameTag = label; } catch {}
      }
    } catch {}
  }, 20); // once a second - plenty smooth, half the cost
}
