// perks.js - extra quality-of-life features layered on top of SERP:
//   * Evolution notification: tells you when a team Pokemon can evolve.
//   * Companion abilities: whichever Pokemon you have sent out grants you a
//     matching buff (like the shoulder-ability idea) based on its type/species.
import { world, system } from "@minecraft/server";
import * as S from "./serpdata.js";
import { SERP_N } from "./serpdex.js";

// ---------- evolution notification ----------
function evoTargetIfReady(dex, level, friendship) {
  const evos = (SERP_N[dex] && SERP_N[dex].ev) || [];
  for (const [target, method, param] of evos) {
    if (method === 1 && level >= Number(param) && Number(param) > 0) return target; // level-up
    if (method === 2 && friendship >= 160) return target;                            // friendship
  }
  return 0;
}

function checkEvolutions(player) {
  let notified = [];
  try { notified = JSON.parse(player.getDynamicProperty("sx_evo_notified") || "[]"); } catch {}
  const set = new Set(notified);
  let changed = false;
  for (const p of S.getParty(player)) {
    const f = p.fields;
    const dex = Number(f[2]);
    const lvl = S.level(f);
    const friend = Number(f[7]) || 0;
    const target = evoTargetIfReady(dex, lvl, friend);
    if (target && !set.has(dex)) {
      set.add(dex);
      changed = true;
      try {
        player.sendMessage({
          rawtext: [
            { text: "\u00a7d\u2728 " },
            { translate: "entity.pokemon:p" + dex + ".name" },
            { text: " \u00a7fcan evolve into \u00a7b" },
            { translate: "entity.pokemon:p" + target + ".name" },
            { text: "\u00a7f!" },
          ],
        });
      } catch {}
    }
    // if it evolved / dropped below threshold again, allow future re-notify
    if (!target && set.has(dex)) { set.delete(dex); changed = true; }
  }
  if (changed) {
    try { player.setDynamicProperty("sx_evo_notified", JSON.stringify([...set])); } catch {}
  }
}

// ---------- companion abilities ----------
// Type ids: 1 Bug 2 Dark 4 Elec 6 Fight 7 Fire 8 Flying 9 Ghost 10 Grass
// 11 Ground 12 Ice 13 Normal 14 Poison 15 Psychic 16 Rock 17 Steel 18 Water
function perkForSpecies(dex) {
  // species-specific perks (the "Bidoof chops / Spinarak climbs" idea)
  const SPECIFIC = {
    399: ["haste", 1], 400: ["haste", 2],           // Bidoof / Bibarel - mine & chop faster
    66: ["strength", 0], 67: ["strength", 1], 68: ["strength", 2], // Machop line - strength
    35: ["regeneration", 0], 36: ["regeneration", 1],              // Clefairy line - regen
  };
  if (SPECIFIC[dex]) return SPECIFIC[dex];
  const ty = (SERP_N[dex] && SERP_N[dex].ty) || [0, 0];
  const t = new Set(ty);
  if (t.has(18)) return ["water_breathing", 0];  // Water - breathe underwater
  if (t.has(8)) return ["slow_falling", 0];      // Flying - float down safely
  if (t.has(7)) return ["fire_resistance", 0];   // Fire - fire immunity
  if (t.has(6)) return ["strength", 0];          // Fighting - strength
  if (t.has(11) || t.has(16)) return ["haste", 1]; // Ground/Rock - mine faster
  if (t.has(15) || t.has(2)) return ["night_vision", 0]; // Psychic/Dark - see in dark
  if (t.has(4)) return ["speed", 0];             // Electric - speed
  if (t.has(12)) return ["resistance", 0];       // Ice - tougher
  if (t.has(10)) return ["saturation", 0];       // Grass - stay fed
  return null;
}

function applyCompanion(player) {
  let outDex = 0;
  const party = S.getParty(player);
  const bySlot = {};
  for (const p of party) bySlot[Number(p.fields[0].replace("team", ""))] = p;
  for (let s = 1; s <= 6; s++) {
    if (player.hasTag("out_slot" + s) && bySlot[s]) { outDex = Number(bySlot[s].fields[2]); break; }
  }
  if (!outDex) return;
  const perk = perkForSpecies(outDex);
  if (!perk) return;
  try { player.addEffect(perk[0], 160, { amplifier: perk[1], showParticles: false }); } catch {}
}

export function initPerks() {
  // evolution check every ~8s
  system.runInterval(() => {
    for (const player of world.getAllPlayers()) {
      try { checkEvolutions(player); } catch {}
    }
  }, 160);
  // companion buff refresh every ~5s (effect lasts 8s so it stays on)
  system.runInterval(() => {
    for (const player of world.getAllPlayers()) {
      try { applyCompanion(player); } catch {}
    }
  }, 100);
}
