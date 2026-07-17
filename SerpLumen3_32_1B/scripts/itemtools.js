// itemtools.js - the Pokedex itself is left to SERP (untouched). We only add two
// custom items: a Radar (scan nearby wild Pokemon) and a Hub opener. Both are
// given to every player and open their menu on use.
import { world, system, ItemStack, EntityComponentTypes } from "@minecraft/server";
import { justTappedEntity } from "./sunnyscan.js";
import { ActionFormData } from "@minecraft/server-ui";

const RADAR_ITEM = "sunnyx:radar";
let HUB_ITEM = "sunhub:menu";
let hubOpener = null;
export function setHubOpener(fn) { hubOpener = fn; }
export function setHubItem(id) { HUB_ITEM = id; }

function compass8(dx, dz) {
  const ang = (Math.atan2(dx, -dz) * 180 / Math.PI + 360) % 360;
  return ["N", "NE", "E", "SE", "S", "SW", "W", "NW"][Math.round(ang / 45) % 8];
}

function openRadar(player) {
  let ents = [];
  try {
    ents = [...player.dimension.getEntities({ location: player.location, maxDistance: 80 })].filter(
      (e) => e.isValid && typeof e.typeId === "string" && e.typeId.startsWith("pokemon:p") && !e.getTags().includes("tamed")
    );
  } catch {}
  const form = new ActionFormData().title("serp.main.pokedex_cyan");
  if (ents.length === 0) {
    form.body("\u00a7lRADAR\u00a7r\n\n\u00a77No wild Pokemon within 80 blocks.").button("Close", "textures/ui/buttons/bubble_no").show(player);
    return;
  }
  const groups = new Map();
  for (const e of ents) {
    const dex = Number(e.typeId.slice(9)) || 0;
    const dx = e.location.x - player.location.x;
    const dz = e.location.z - player.location.z;
    const dist = Math.round(Math.sqrt(dx * dx + dz * dz));
    const g = groups.get(dex);
    if (!g) groups.set(dex, { dex, count: 1, dist, dir: compass8(dx, dz) });
    else { g.count++; if (dist < g.dist) { g.dist = dist; g.dir = compass8(dx, dz); } }
  }
  const list = [...groups.values()].sort((a, b) => a.dist - b.dist);
  form.body("\u00a7lRADAR\u00a7r  \u00a77(" + ents.length + " within 80 blocks)");
  for (const g of list) {
    form.button(
      { rawtext: [{ translate: "entity.pokemon:p" + g.dex + ".name" }, { text: "  \u00a77x" + g.count + "\n\u00a7b" + g.dist + "m " + g.dir }] },
      "pokedrock/pokedex/" + g.dex
    );
  }
  form.button("Close", "textures/ui/buttons/bubble_no").show(player);
}

function hasItem(player, id) {
  try {
    const inv = player.getComponent(EntityComponentTypes.Inventory);
    if (!inv || !inv.container) return true;
    for (let i = 0; i < inv.container.size; i++) {
      const it = inv.container.getItem(i);
      if (it && it.typeId === id) return true;
    }
    return false;
  } catch { return true; }
}
function giveOnce(player, id) {
  if (hasItem(player, id)) return;
  try {
    const inv = player.getComponent(EntityComponentTypes.Inventory);
    inv.container.addItem(new ItemStack(id, 1));
  } catch { /* ignore */ }
}

export function initItemTools() {
  world.afterEvents.itemUse.subscribe((ev) => {
    const id = ev.itemStack && ev.itemStack.typeId;
    const src = ev.source;
    if (!id || !src) return;
    if (id === RADAR_ITEM) system.run(() => openRadar(src));
    else if (id === HUB_ITEM && hubOpener && !justTappedEntity(src.id)) system.run(() => hubOpener(src));
  });
  // Hand out the items, once, to everyone.
  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      giveOnce(p, RADAR_ITEM);
      giveOnce(p, HUB_ITEM);
    }
  }, 100);
}
