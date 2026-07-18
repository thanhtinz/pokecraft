// vehicles.js - own your rides. Any vanilla vehicle you place (boats,
// minecarts, saddled horses/donkeys/mules/llamas/striders/pigs) gets tagged as
// yours. Then:
//   - Hub -> Travel -> My Vehicles: bring any of them to you, or send it home
//   - If it's destroyed/killed, the item comes back to your inventory
//     (queued if you're offline) - never craft the same boat twice again.
import { world, system, ItemStack } from "@minecraft/server";
import { actionMenu, confirmForm, giveItem } from "./forms.js";
import { homeClaims } from "./claims.js";

const OWNED = "sl:veh";     // per-player dp: [{id, type, name}]
const OWED = "sl:vehowed";  // world: { playerName: [itemId,...] }
const MAXV = 10;

// entity -> the item it drops back / display name
const VEHICLES = {
  "minecraft:boat": ["minecraft:oak_boat", "Boat"],
  "minecraft:chest_boat": ["minecraft:oak_chest_boat", "Chest Boat"],
  "minecraft:minecart": ["minecraft:minecart", "Minecart"],
  "minecraft:chest_minecart": ["minecraft:chest_minecart", "Chest Minecart"],
  "minecraft:hopper_minecart": ["minecraft:hopper_minecart", "Hopper Minecart"],
  "minecraft:tnt_minecart": ["minecraft:tnt_minecart", "TNT Minecart"],
  "minecraft:horse": [null, "Horse"],
  "minecraft:donkey": [null, "Donkey"],
  "minecraft:mule": [null, "Mule"],
  "minecraft:llama": [null, "Llama"],
  "minecraft:strider": [null, "Strider"],
  "minecraft:pig": [null, "Pig"],
  "minecraft:camel": [null, "Camel"],
};

function jget(k, fb) { try { return JSON.parse(world.getDynamicProperty(k) ?? fb); } catch { return JSON.parse(fb); } }
function jset(k, v) { try { world.setDynamicProperty(k, JSON.stringify(v)); } catch {} }
function mine(p) { try { return JSON.parse(p.getDynamicProperty(OWNED) ?? "[]"); } catch { return []; } }
function setMine(p, a) { try { p.setDynamicProperty(OWNED, JSON.stringify(a)); } catch {} }
function vtag(p) { return "slveh_" + String(p.id).replace(/[^0-9A-Za-z]/g, "_"); }

function findVeh(player, rec) {
  const dim = world.getDimension("overworld");
  for (const d of [dim, world.getDimension("nether"), world.getDimension("the_end")]) {
    try {
      const hit = [...d.getEntities({ tags: ["slv_" + rec.id] })][0];
      if (hit) return hit;
    } catch {}
  }
  return null;
}

export function initVehicles() {
  // claim ownership of vehicles you place / first ride
  world.afterEvents.entitySpawn.subscribe((ev) => {
    try {
      const e = ev.entity;
      if (!e || !VEHICLES[e.typeId]) return;
      if (e.getTags().some((t) => t.startsWith("slv_"))) return;
      // nearest player within 4 blocks = the one who placed it
      let owner = null, bd = 16;
      for (const p of world.getAllPlayers()) {
        if (p.dimension.id !== e.dimension.id) continue;
        const d = (p.location.x - e.location.x) ** 2 + (p.location.y - e.location.y) ** 2 + (p.location.z - e.location.z) ** 2;
        if (d < bd) { bd = d; owner = p; }
      }
      if (!owner) return;
      const list = mine(owner);
      if (list.length >= MAXV) return; // over quota: stays a plain vanilla vehicle
      const rec = { id: "v" + Date.now() + Math.floor(Math.random() * 999), type: e.typeId, name: VEHICLES[e.typeId][1] };
      try {
        e.addTag("sl_veh");
        e.addTag("slv_" + rec.id);
        e.addTag(vtag(owner));
      } catch {}
      list.push(rec);
      setMine(owner, list);
      owner.onScreenDisplay.setActionBar("\u00a7b\u2691 " + rec.name + " registered - recall it from Hub -> Travel -> My Vehicles");
    } catch {}
  });

  // destroyed / killed -> refund the item to the owner (queued if offline)
  const refund = (e) => {
    try {
      const tags = e.getTags();
      if (!tags.includes("sl_veh")) return;
      const vt = tags.find((t) => t.startsWith("slv_"));
      const ot = tags.find((t) => t.startsWith("slveh_"));
      if (!vt || !ot) return;
      const vid = vt.slice(4);
      const [itemId, label] = VEHICLES[e.typeId] ?? [null, "Vehicle"];
      for (const p of world.getAllPlayers()) {
        if (vtag(p) !== ot) continue;
        const list = mine(p).filter((r) => r.id !== vid);
        setMine(p, list);
        if (itemId) {
          giveItem(p, itemId, 1);
          p.sendMessage("\u00a7a[Vehicle] Your " + label + " was destroyed - the item is back in your inventory.");
        } else {
          p.sendMessage("\u00a7e[Vehicle] Your " + label + " died. (Living rides can't be refunded.)");
        }
        return;
      }
      // owner offline: queue the item
      if (!itemId) return;
      const o = jget(OWED, "{}");
      (o[ot] = o[ot] ?? []).push(itemId);
      jset(OWED, o);
    } catch {}
  };
  world.afterEvents.entityDie.subscribe((ev) => { try { refund(ev.deadEntity); } catch {} });
  world.afterEvents.entityRemove.subscribe((ev) => {
    // entityRemove has no entity object - handled by entityDie for kills; boats
    // broken by hand fire entityDie too, so nothing more needed here.
  });

  // deliver queued refunds
  system.runInterval(() => {
    try {
      const o = jget(OWED, "{}");
      let changed = false;
      for (const p of world.getAllPlayers()) {
        const items = o[vtag(p)];
        if (!items || !items.length) continue;
        for (const id of items) giveItem(p, id, 1);
        p.sendMessage("\u00a7a[Vehicle] " + items.length + " destroyed vehicle(s) returned to your inventory.");
        delete o[vtag(p)];
        changed = true;
      }
      if (changed) jset(OWED, o);
    } catch {}
  }, 200);
}

// --- Admin: reset a player's vehicle registry ---------------------------
// Use when someone's rides "disappeared" (data got corrupted, or the tagged
// entities were culled). Wipes their owned list + any queued refunds, and
// scrubs orphaned sl_veh entities that still carry their owner tag so new
// vehicles register cleanly again.
function resetVehData(target) {
  let removed = 0;
  const ot = vtag(target);
  try { setMine(target, []); } catch {}
  // drop queued refunds for this owner
  try {
    const o = jget(OWED, "{}");
    if (o[ot]) { delete o[ot]; jset(OWED, o); }
  } catch {}
  // scrub any live entities still tagged to this owner
  for (const dimId of ["overworld", "nether", "the_end"]) {
    try {
      const dim = world.getDimension(dimId);
      for (const e of dim.getEntities({ tags: [ot] })) {
        try { e.remove(); removed++; } catch {}
      }
    } catch {}
  }
  return removed;
}

export async function openVehAdmin(admin) {
  const { isAdmin, actionMenu, confirmForm } = await import("./forms.js");
  if (!isAdmin(admin)) return admin.sendMessage("\u00a7cAdmins only.");
  const players = world.getAllPlayers();
  const sel = await actionMenu(admin, "Reset Vehicles",
    "Pick a player to reset their vehicle registry.\n\u00a77Use this if their rides \u00a7cdisappeared\u00a77 or the count is stuck.\nClears their owned list, queued refunds, and orphaned tagged vehicles.",
    players.map((p) => ({ label: p.name + "\n\u00a78" + mine(p).length + "/" + MAXV + " registered", icon: "textures/items/boat_oak" })),
    "pokedex_black");
  if (sel < 0 || sel >= players.length) return;
  const t = players[sel];
  if (!(await confirmForm(admin, "Reset all vehicle data for \u00a7f" + t.name + "\u00a7r?\n\nThis wipes their registered rides so they can place fresh ones. It does NOT refund items."))) return;
  const removed = resetVehData(t);
  admin.sendMessage("\u00a7a[Vehicle] Reset " + t.name + " - cleared registry" + (removed ? " + removed " + removed + " stray vehicle(s)" : "") + ".");
  try { t.sendMessage("\u00a7e[Vehicle] An admin reset your vehicle registry. Place a boat/minecart or tame a mount to register again."); } catch {}
  return openVehAdmin(admin);
}

export async function openVehicles(player) {
  const list = mine(player);
  if (list.length === 0) {
    return actionMenu(player, "My Vehicles",
      "You haven't placed any vehicles yet.\n\nPlace a \u00a7bboat, minecart\u00a7r or tame/saddle a \u00a7bhorse, donkey, llama, strider, camel\u00a7r and it's registered to you automatically (max " + MAXV + ").\n\nThen you can \u00a7ecall it to you\u00a7r from anywhere - and if it gets destroyed, the item comes back to your bag.",
      [{ label: "OK", icon: "textures/ui/buttons/bubble_no" }], "pokedex_light_blue");
  }
  const sel = await actionMenu(player, "My Vehicles (" + list.length + "/" + MAXV + ")",
    "Tap a vehicle to bring it to you:",
    list.map((r) => {
      const e = findVeh(player, r);
      return {
        label: r.name + "\n" + (e ? "\u00a77" + Math.floor(e.location.x) + ", " + Math.floor(e.location.z) + " \u00a78" + e.dimension.id.replace("minecraft:", "") : "\u00a78(not loaded - go near it or recall)"),
        icon: "textures/items/boat_oak",
      };
    }), "pokedex_light_blue");
  if (sel < 0) return;
  const rec = list[sel];
  const act = await actionMenu(player, rec.name, "What do you want to do?", [
    { label: "\u00a7aBring it to me", icon: "textures/items/ender_pearl" },
    { label: "Send it to my land", icon: "textures/blocks/grass_side_carried" },
    { label: "\u00a7cForget it\n\u00a78Stops tracking (vehicle stays)", icon: "textures/ui/buttons/bubble_no" },
  ], "pokedex_light_blue");
  if (act < 0) return;
  if (act === 2) {
    if (!(await confirmForm(player, "Stop tracking this " + rec.name + "?"))) return;
    setMine(player, mine(player).filter((r) => r.id !== rec.id));
    player.sendMessage("\u00a7e[Vehicle] Forgotten.");
    return;
  }
  const e = findVeh(player, rec);
  if (!e) return player.sendMessage("\u00a7c[Vehicle] Can't reach it right now (its area isn't loaded). Try again once someone is near it.");
  if (act === 0) {
    try {
      e.teleport({ x: player.location.x + 1, y: player.location.y, z: player.location.z }, { dimension: player.dimension });
      player.sendMessage("\u00a7a[Vehicle] Your " + rec.name + " is here!");
      try { player.playSound("mob.endermen.portal"); } catch {}
    } catch { player.sendMessage("\u00a7c[Vehicle] Recall failed."); }
    return;
  }
  const lands = homeClaims(player);
  if (lands.length === 0) return player.sendMessage("\u00a7c[Vehicle] You have no claimed land (Hub -> Land Claims).");
  const c = lands[0];
  try {
    e.teleport({ x: (c.x1 + c.x2) / 2 + 0.5, y: (c.y ?? 80) + 1, z: (c.z1 + c.z2) / 2 + 0.5 }, { dimension: world.getDimension("overworld") });
    player.sendMessage("\u00a7a[Vehicle] " + rec.name + " sent to your land.");
  } catch { player.sendMessage("\u00a7c[Vehicle] Could not send it home."); }
}
