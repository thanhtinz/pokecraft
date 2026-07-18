// admin.js - SunnyX admin tools
// NPC placement: respawns SERP NPCs (Joy, Jenny, Professor, Store, Game
// Corner, Trainer, quest NPC) at the admin's position via spawnEntity. SERP
// auto-initializes poke_npc/trainer through its own entitySpawn handler, so
// a freshly placed NPC behaves exactly like a world-generated one.

import { world, system } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { confirm, themedForm } from "./ui.js";

const NPCS = [
  ["serp:joy", "Nurse Joy (healing)"],
  ["serp:jenny", "Officer Jenny (PvP / money transfer)"],
  ["serp:profs", "Professor (starter pick)"],
  ["serp:store", "Pokestore shop"],
  ["serp:game_corner", "Quan ly Game Corner"],
  ["serp:trainer", "Trainer (team thu)"],
  ["serp:poke_npc", "NPC nhiem vu"],
];

export function isAdmin(player) {
  // Matches SERP's own admin gate: operator permission level (GameDirectors)
  // and Creative mode. SERP uses no custom tag; we keep "sxadmin" only as a
  // manual fallback for non-op staff.
  try {
    if (player.commandPermissionLevel >= 1) return true;
  } catch {}
  try {
    if (player.getGameMode && player.getGameMode() === "Creative") return true;
  } catch {}
  return player.hasTag("sxadmin");
}

export function openAdmin(player) {
  if (!isAdmin(player)) {
    player.sendMessage("\u00a7cOnly operators (or granted admins) can use this.");
    return;
  }
  themedForm("store", "Sunny Pokedrock Admin")
    .button("Place NPC at my position")
    .button("Clean up event mobs (Alpha/Raid/Gym/extra spawns)")
    .button("SERP commands (type in chat)")
    .button("NPC Guard: " + anchorCount() + " anchors (auto-restore)")
    .button("Grant / revoke admin (for Realms)")
    .button("Close")
    .show(player)
    .then((res) => {
      if (res.canceled) return;
      if (res.selection === 0) placeNPC(player);
      else if (res.selection === 1) cleanupEventMobs(player);
      else if (res.selection === 3) {
        confirm(player, "NPC Guard", anchorCount() + " NPC positions are anchored and auto-restored every 30s when loaded.\n\nClear all anchors?", "\u00a7cClear").then((ok) => {
          if (ok) {
            clearAnchors();
            player.sendMessage("\u00a7eAll NPC anchors cleared.");
          }
        });
      } else if (res.selection === 4) {
        openGrantAdmin(player);
      } else if (res.selection === 2) {
        themedForm("store", "SERP commands - OP only")
          .body(
            "Type directly in chat (GameDirectors permission):\n\n" +
            "/serp:pokereset <player> - reset team + PC\n" +
            "/serp:pokestarter <player> - re-pick starter\n" +
            "/serp:getpokemon <player> <dexId> - give a Pokemon (SERP-native ids ONLY, DX ids will error)\n" +
            "/serp:getbadge <player> <1-18> - give a badge\n" +
            "/serp:pokereturn <player> - recall effect\n\n" +
            "To give DX species: trade from an admin account.\n\n" +
            "REDEEM (at the quest NPC): serp:key_stone -> Mega Ring, serp:wishing_star -> Dynamax Band, serp:bad_egg -> Pokemon Egg.")
          .button("Close").show(player).then(() => {});
      }
    });
}

// On Realms you cannot use /op or /tag to delegate: operator status is set in
// the Realm member UI (owner + toggled operators only), and there's no console.
// This lets an existing admin grant the "sxadmin" tag to any online player so
// they get Sunny Pokedrock admin WITHOUT needing full Realm operator rights.
function openGrantAdmin(player) {
  const players = world.getAllPlayers();
  const form = new ActionFormData().title("serp.main.store").body(
    "Grant or revoke Sunny Pokedrock admin.\n" +
    "\u00a77Operators are always admin. This adds the \"sxadmin\" tag for non-operators (useful on Realms).",
  );
  for (const p of players) {
    const has = p.hasTag("sxadmin");
    const op = (() => { try { return p.commandPermissionLevel >= 1; } catch { return false; } })();
    form.button(p.name + "\n" + (op ? "\u00a7bOperator (always admin)" : has ? "\u00a7aAdmin \u2714 (tap to revoke)" : "\u00a78Not admin (tap to grant)"));
  }
  form.button("Back");
  form.show(player).then((res) => {
    if (res.canceled || res.selection >= players.length) return openAdmin(player);
    const t = players[res.selection];
    if (!t?.isValid) return openAdmin(player);
    try {
      if (t.commandPermissionLevel >= 1) {
        player.sendMessage("\u00a7e" + t.name + " is already an operator - no tag needed.");
      } else if (t.hasTag("sxadmin")) {
        t.removeTag("sxadmin");
        player.sendMessage("\u00a7eRevoked admin from " + t.name + ".");
        t.sendMessage("\u00a7eYour Sunny Pokedrock admin access was removed.");
      } else {
        t.addTag("sxadmin");
        player.sendMessage("\u00a7aGranted admin to " + t.name + ".");
        t.sendMessage("\u00a7aYou now have Sunny Pokedrock admin access.");
      }
    } catch {}
    openGrantAdmin(player);
  });
}

function placeNPC(player) {
  const form = themedForm("store", "Place NPC").body("The NPC will appear where you stand, facing you.");
  for (const [, label] of NPCS) form.button(label);
  form.button("Back");
  form.show(player).then((res) => {
    if (res.canceled || res.selection >= NPCS.length) return;
    const [id, label] = NPCS[res.selection];
    try {
      const view = player.getViewDirection();
      const loc = {
        x: player.location.x + view.x * 2,
        y: player.location.y,
        z: player.location.z + view.z * 2,
      };
      const e = player.dimension.spawnEntity(id, loc);
      try {
        e.setRotation({ x: 0, y: (player.getRotation().y + 180) % 360 });
      } catch {}
      anchorNPC(e);
      player.sendMessage("\u00a7aPlaced " + label + ".");
      system.run(() => placeNPC(player));
    } catch {
      player.sendMessage("\u00a7cCouldn't place it here.");
    }
  });
}

function cleanupEventMobs(player) {
  confirm(
    player,
    "Clean up event mobs",
    "Removes every Pokemon spawned by Sunny Pokedrock (Alpha, Raid, Gym, conditional) currently alive.\nPlayer Pokemon and NPCs are untouched.",
    "\u00a7cClean up",
  ).then((ok) => {
    if (!ok) return;
    let n = 0;
    for (const name of ["overworld", "nether", "the_end"]) {
      try {
        const dim = world.getDimension(name);
        for (const tag of ["sunnyx_alpha", "sunnyx_raid", "sunnyx_gym", "sunnyx_spawn", "sunnyx_legend"]) {
          for (const e of dim.getEntities({ tags: [tag] })) {
            try {
              e.remove();
              n++;
            } catch {}
          }
        }
      } catch {}
    }
    player.sendMessage("\u00a7aCleaned " + n + " event mobs.");
  });
}
