// npcguard.js - Auto-restore for SERP NPCs
// serp:trainer and serp:poke_npc ship with despawn_from_distance in their
// BASE components, so they vanish whenever players wander 32-128 blocks
// away - by SERP design, with no event to remove it. This guard records an
// anchor for every settlement NPC it sees and respawns any that are missing
// while their spot is loaded. Wild roaming trainers are NOT anchored: the
// despawnable types only get anchored when named or standing in a
// settlement (near a persistent NPC or machine).

import { world, system } from "@minecraft/server";

const PERSISTENT = ["serp:joy", "serp:jenny", "serp:profs", "serp:store", "serp:game_corner"];
const DESPAWNABLE = ["serp:trainer", "serp:poke_npc"];
const SETTLEMENT = new Set([
  ...PERSISTENT,
  "serp:healing_machine", "serp:pc_box", "serp:communicator", "serp:slot_machine",
  "serp:rotomarket", "serp:trade_machine", "serp:tm_machine", "serp:mini_lab",
]);
const KEY = "sx:npcanchors";
const MAX_ANCHORS = 150;
const RESPAWN_COOLDOWN_MS = 5 * 60 * 1000;

function load() {
  try {
    return JSON.parse(world.getDynamicProperty(KEY) ?? "[]");
  } catch {
    return [];
  }
}
function save(a) {
  try {
    world.setDynamicProperty(KEY, JSON.stringify(a.slice(0, MAX_ANCHORS)));
  } catch {}
}
function near(a, l, r) {
  return Math.abs(a.x - l.x) <= r && Math.abs(a.y - l.y) <= r && Math.abs(a.z - l.z) <= r;
}

export function anchorNPC(entity) {
  try {
    const anchors = load();
    const l = entity.location;
    const rec = {
      t: entity.typeId,
      x: Math.round(l.x * 10) / 10, y: Math.round(l.y * 10) / 10, z: Math.round(l.z * 10) / 10,
      d: entity.dimension.id,
      n: entity.nameTag || "",
      c: 0,
    };
    const dup = anchors.find((a) => a.t === rec.t && a.d === rec.d && near(a, rec, 6));
    if (dup) {
      dup.x = rec.x; dup.y = rec.y; dup.z = rec.z; dup.n = rec.n || dup.n;
    } else {
      anchors.push(rec);
    }
    save(anchors);
  } catch {}
}

function scanAndRestore() {
  const anchors = load();
  let dirty = false;
  for (const player of world.getAllPlayers()) {
    let dim;
    try {
      dim = player.dimension;
    } catch {
      continue;
    }
    let nearby;
    try {
      nearby = dim.getEntities({ location: player.location, maxDistance: 48 });
    } catch {
      continue;
    }
    // ONLY the despawnable NPCs (serp:trainer / serp:poke_npc) are ever
    // anchored or restored. joy/jenny/profs/store/game_corner have NO despawn
    // component - they never vanish while loaded and they wander on their own,
    // so restoring them just spawns a second copy on top of the one that
    // walked a few blocks away. That was the source of the "2 Nurse Joys in
    // one spot" duplication. We leave all persistent NPCs completely alone.
    const npcs = nearby.filter((e) => DESPAWNABLE.includes(e.typeId));
    const settle = nearby.filter((e) => SETTLEMENT.has(e.typeId));
    // record anchors (only for named or settlement-bound despawnable NPCs)
    for (const e of npcs) {
      const named = (e.nameTag ?? "") !== "";
      const inTown = settle.some((s) => {
        try {
          return near(s.location, e.location, 12);
        } catch {
          return false;
        }
      });
      if (!named && !inTown) continue; // wild roaming trainer - let it live free
      anchorNPC(e);
    }
    // restore missing anchors in this player's loaded area
    const now = Date.now();
    for (const a of anchors) {
      if (!DESPAWNABLE.includes(a.t)) continue; // never restore persistent NPCs
      if (a.d !== dim.id || !near(a, player.location, 40)) continue;
      if (now - (a.c ?? 0) < RESPAWN_COOLDOWN_MS) continue;
      // treat as alive if ANY same-type NPC is within a generous radius, so a
      // trainer that wandered off its exact post is never duplicated.
      const alive = nearby.some((e) => e.typeId === a.t && near(e.location, a, 16));
      if (alive) continue;
      try {
        const ent = dim.spawnEntity(a.t, { x: a.x, y: a.y, z: a.z });
        if (a.n) ent.nameTag = a.n;
        a.c = now;
        dirty = true;
      } catch {}
    }
  }
  if (dirty) save(anchors);
}

// One-time cleanup: drop any anchors for persistent NPC types left over from
// the old buggy version, and de-duplicate any Nurse Joy / Jenny / etc. that
// the old restore loop already spawned on top of each other.
function purgeLegacyPersistentAnchors() {
  try {
    const anchors = load();
    const cleaned = anchors.filter((a) => DESPAWNABLE.includes(a.t));
    if (cleaned.length !== anchors.length) save(cleaned);
  } catch {}
  system.runInterval(() => {
    try {
      for (const player of world.getAllPlayers()) {
        let dim, nearby;
        try {
          dim = player.dimension;
          nearby = dim.getEntities({ location: player.location, maxDistance: 48 });
        } catch { continue; }
        // De-dup ONLY true clones: two of the same NPC overlapping almost
        // exactly (<0.6 block on X AND Z). SERP legitimately places several of
        // the same NPC a couple of blocks apart in a settlement, so the old
        // 1.5-block radius was wiping real NPCs. Overlap this tight only ever
        // happens when the old buggy restore stacked a copy on top.
        for (const type of PERSISTENT) {
          const same = nearby.filter((e) => { try { return e.typeId === type && e.isValid; } catch { return false; } });
          const removed = new Set();
          for (let i = 0; i < same.length; i++) {
            if (removed.has(i)) continue;
            for (let j = i + 1; j < same.length; j++) {
              if (removed.has(j)) continue;
              try {
                const a = same[i].location, b = same[j].location;
                if (Math.abs(a.x - b.x) < 0.6 && Math.abs(a.z - b.z) < 0.6 && Math.abs(a.y - b.y) < 1.5) {
                  same[j].remove();
                  removed.add(j);
                }
              } catch {}
            }
          }
        }
      }
    } catch {}
  }, 200); // every 10s
}

export function anchorCount() {
  return load().length;
}
export function clearAnchors() {
  save([]);
}

export function initNpcGuard() {
  purgeLegacyPersistentAnchors();
  system.runInterval(() => {
    try {
      scanAndRestore();
    } catch {}
  }, 600); // every 30s
}
