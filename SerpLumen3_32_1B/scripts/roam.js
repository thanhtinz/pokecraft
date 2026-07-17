// roam.js - Daily Legendary. Every day ONE legendary appears somewhere
// 1000-2000 blocks from the village (world spawn) at a random big size.
//
// It is CATCHABLE - but brutally so: our own gate lets through only 1 capture in
// 10,000 (0.01%). Every other ball breaks and it pops straight back where it
// stood. Whoever finally lands it KEEPS it and the event ends for everyone.
// If nobody catches it within 10 minutes of it showing itself, it vanishes.
import { world, system } from "@minecraft/server";
import { POKENAMES } from "./pokenames.js";
import { SPECIES } from "./speciesdata.js";
import { dayNumber } from "./forms.js";
import { takePokemon, decode, isPCTag, isPartyTag } from "./serpdata.js";

const PROP = "sl:roam";
const TAG = "sl_roam";
const CATCH_CHANCE = 0.0001;    // 1 in 10,000
const WINDOW_MS = 10 * 60000;   // 10 minutes once it appears
const SIZES = ["serp:xl", "serp:xxl", "serp:xxxl"];
const LEGENDS = [144, 145, 146, 150, 151, 243, 244, 245, 249, 250, 251, 377, 378, 379, 380, 381, 382, 383,
  384, 385, 386, 480, 481, 482, 483, 484, 485, 486, 487, 488, 491, 492, 493, 494, 638, 639, 640, 641, 642,
  643, 644, 645, 646, 716, 717, 718, 720, 785, 786, 787, 788, 789, 790, 791, 792, 800, 802, 807]
  .filter((d) => SPECIES[String(d)]);

function jget() { try { return JSON.parse(world.getDynamicProperty(PROP) ?? "null"); } catch { return null; } }
function jset(v) { try { world.setDynamicProperty(PROP, JSON.stringify(v)); } catch {} }
function nameOf(dex) { return POKENAMES[String(dex)] ?? ("#" + dex); }

function village() {
  try { const w = world.getDefaultSpawnLocation(); return { x: w.x, z: w.z }; } catch { return { x: 0, z: 0 }; }
}
function spawnPoint() {
  const s = village();
  const ang = Math.random() * Math.PI * 2;
  const dist = 1000 + Math.random() * 1000;
  return { x: Math.round(s.x + Math.cos(ang) * dist), z: Math.round(s.z + Math.sin(ang) * dist), sx: s.x, sz: s.z };
}
function compass8(dx, dz) {
  const a = (Math.atan2(dx, -dz) * 180 / Math.PI + 360) % 360;
  return ["North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West"][Math.round(a / 45) % 8];
}

function newDay(day) {
  const prev = jget();
  let pool = LEGENDS;
  if (prev && LEGENDS.length > 1) pool = LEGENDS.filter((d) => d !== prev.dex);
  const dex = pool[Math.floor(Math.random() * pool.length)];
  const p = spawnPoint();
  const now = Date.now();
  const dayEnd = (day + 1) * 86400000;
  const minAt = now + 30 * 60000;
  const maxAt = Math.max(minAt + 15 * 60000, dayEnd - 2 * 3600000);
  const st = {
    d: day, dex, x: p.x, z: p.z, sx: p.sx, sz: p.sz,
    at: minAt + Math.floor(Math.random() * (maxAt - minAt)),
    size: SIZES[Math.floor(Math.random() * SIZES.length)],
    announced: false, spawned: false, done: false, hintN: 0, expires: 0,
  };
  jset(st);
  return st;
}

function announce(st) {
  st.announced = true;
  jset(st);
  world.sendMessage("\u00a75\u2605 [LEGENDARY] \u00a7f\u00a7l" + nameOf(st.dex) + "\u00a7r\u00a7f has been sighted far from the village!\n" +
    "\u00a7d It CAN be caught - but it breaks out of almost every ball. \u00a7fFirst trainer to land one keeps it.\n" +
    "\u00a77 Hints follow. Once it shows itself you get \u00a7f10 minutes\u00a77.");
  for (const p of world.getAllPlayers()) { try { p.playSound("mob.enderdragon.growl"); } catch {} }
}

function hint(st) {
  const fuzz = [500, 320, 200, 110, 50][Math.min(st.hintN, 4)];
  const fx = st.x + (Math.random() * 2 - 1) * fuzz;
  const fz = st.z + (Math.random() * 2 - 1) * fuzz;
  const dx = fx - st.sx, dz = fz - st.sz;
  const dist = Math.round(Math.sqrt(dx * dx + dz * dz) / 100) * 100;
  world.sendMessage("\u00a75\u2605 [LEGENDARY] \u00a7f" + nameOf(st.dex) + ": \u00a7d" + compass8(dx, dz) +
    "\u00a7f of the village, ~\u00a7d" + dist + "\u00a7f blocks out" +
    (st.hintN >= 3 ? " \u00a77(near x " + Math.round(fx) + ", z " + Math.round(fz) + ")" : ""));
  st.hintN++;
  jset(st);
}

function findRoamer() {
  try { return [...world.getDimension("overworld").getEntities({ tags: [TAG] })][0] ?? null; } catch { return null; }
}

function materialize(st, nearPlayer, quiet) {
  const dim = nearPlayer.dimension;
  let y = nearPlayer.location.y + 2;
  try { const b = dim.getTopmostBlock({ x: st.x, z: st.z }); if (b) y = b.y + 2; } catch {}
  let e;
  try { e = dim.spawnEntity("pokemon:p" + st.dex, { x: st.x + 0.5, y, z: st.z + 0.5 }); } catch { return false; }
  try {
    e.addTag(TAG);   // no "tamed" tag: it must stay catchable
    e.nameTag = "\u00a75\u2605 " + nameOf(st.dex) + " \u00a7c(LEGENDARY)";
    try { e.triggerEvent(st.size ?? "serp:xxxl"); } catch {}
    e.addEffect("minecraft:health_boost", 20000000, { amplifier: 40, showParticles: false });
    e.addEffect("minecraft:resistance", 20000000, { amplifier: 1, showParticles: false });
    e.addEffect("minecraft:regeneration", 20000000, { amplifier: 2, showParticles: false });
    system.runTimeout(() => {
      try { const h = e.getComponent("minecraft:health"); if (h) h.setCurrentValue(h.effectiveMax); } catch {}
    }, 10);
  } catch {}
  if (!st.spawned) {
    st.spawned = true;
    st.expires = Date.now() + WINDOW_MS;
    jset(st);
  }
  if (!quiet) {
    world.sendMessage("\u00a75\u2605 [LEGENDARY] \u00a7l" + nameOf(st.dex) + "\u00a7r\u00a75 has appeared near \u00a7f" + nearPlayer.name +
      "\u00a75 at \u00a7f" + st.x + ", " + st.z + "\u00a75! \u00a7c10 minutes only\u00a75 - and it resists nearly every ball.");
    for (const p of world.getAllPlayers()) { try { p.playSound("mob.enderdragon.growl"); } catch {} }
  }
  return true;
}

function vanish(st, msg) {
  try { findRoamer()?.remove(); } catch {}
  st.done = true;
  jset(st);
  if (msg) world.sendMessage(msg);
}

// SERP writes a caught Pokemon onto the player as a tag. While the legendary is
// out we watch for that: 99.99% of the time we hand it straight back.
function guardCapture(st) {
  for (const p of world.getAllPlayers()) {
    let tags;
    try { tags = p.getTags(); } catch { continue; }
    for (const t of tags) {
      if (!isPartyTag(t) && !isPCTag(t)) continue;
      const f = decode(t);
      if (!f || Number(f[2]) !== st.dex) continue;

      if (Math.random() < CATCH_CHANCE) {
        vanish(st, "\u00a76\u2605\u2605\u2605 [LEGENDARY] \u00a7f\u00a7l" + p.name + "\u00a7r\u00a76 CAUGHT \u00a7l" + nameOf(st.dex) +
          "\u00a7r\u00a76! One in ten thousand - it is theirs alone!");
        try { p.sendMessage("\u00a76\u2605 You did the impossible - \u00a7l" + nameOf(st.dex) + "\u00a7r\u00a76 is yours!"); } catch {}
        for (const q of world.getAllPlayers()) { try { q.playSound("random.levelup"); } catch {} }
        return;
      }

      try { takePokemon(p, t); } catch { try { p.removeTag(t); } catch {} }
      try { p.sendMessage("\u00a7c\u2605 " + nameOf(st.dex) + " \u00a7fbroke free! \u00a77(legendary catch rate: 0.01%)"); } catch {}
      try { p.playSound("mob.enderdragon.hurt"); } catch {}
      if (!findRoamer()) materialize(st, p, true);
      return;
    }
  }
}

export function initRoam() {
  system.runInterval(() => {
    try {
      const day = dayNumber();
      let st = jget();
      if (!st || st.d !== day) {
        if (world.getAllPlayers().length === 0) return;
        st = newDay(day);
      }
      if (st.done) return;
      const players = world.getAllPlayers();
      if (players.length === 0) return;
      if (Date.now() < st.at) return;
      if (!st.announced) announce(st);

      if (st.spawned && st.expires && Date.now() > st.expires) {
        vanish(st, "\u00a75\u2605 [LEGENDARY] \u00a7f" + nameOf(st.dex) + " \u00a77vanished. Nobody could hold it today...");
        return;
      }
      if (!st.spawned || !findRoamer()) {
        for (const p of players) {
          const dx = p.location.x - st.x, dz = p.location.z - st.z;
          if (p.dimension.id === "minecraft:overworld" && dx * dx + dz * dz <= 60 * 60) {
            materialize(st, p, st.spawned);
            break;
          }
        }
      }
      if (st.spawned) guardCapture(st);
    } catch {}
  }, 20);

  // hints before it shows, countdown once it is out
  system.runInterval(() => {
    try {
      const st = jget();
      if (!st || st.done || st.d !== dayNumber()) return;
      if (world.getAllPlayers().length === 0) return;
      if (!st.spawned) { if (st.announced && st.hintN < 5) hint(st); return; }
      const left = Math.ceil((st.expires - Date.now()) / 60000);
      if (left > 0 && left <= 3) {
        world.sendMessage("\u00a7c\u2605 [LEGENDARY] \u00a7f" + nameOf(st.dex) + " is slipping away - \u00a7l" + left +
          " minute" + (left > 1 ? "s" : "") + "\u00a7r\u00a7f left!");
      }
    } catch {}
  }, 1200);
}
