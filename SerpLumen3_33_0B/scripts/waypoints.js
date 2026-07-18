// waypoints.js - Teleport Pillars. Place a LODESTONE to create a personal
// teleport pillar (max 10). Hub -> Travel -> Teleport Pillars lists yours;
// tap to warp there. Breaking the lodestone removes the pillar.
// No new items/blocks - the vanilla lodestone IS the pillar.
import { world, system } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm } from "./forms.js";

const PROP = "sl:wp"; // per-player dp: [{n, x, y, z, dim}]
const MAXWP = 10;
const BLOCK = "minecraft:lodestone";

function getWp(p) { try { return JSON.parse(p.getDynamicProperty(PROP) ?? "[]"); } catch { return []; } }
function setWp(p, a) { try { p.setDynamicProperty(PROP, JSON.stringify(a)); } catch {} }
function keyOf(w) { return w.dim + "|" + w.x + "|" + w.y + "|" + w.z; }

export function initWaypoints() {
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    try {
      if (!ev.player || ev.block.typeId !== BLOCK) return;
      const p = ev.player;
      const wp = getWp(p);
      const loc = ev.block.location;
      if (wp.length >= MAXWP) {
        p.sendMessage("\u00a7e[Tele] You already have " + MAXWP + " pillars - break one first. (This lodestone works as vanilla only.)");
        return;
      }
      wp.push({ n: "Tru " + (wp.length + 1), x: loc.x, y: loc.y, z: loc.z, dim: p.dimension.id });
      setWp(p, wp);
      p.sendMessage("\u00a7a[Tele] \u26a1 Teleport pillar placed (" + wp.length + "/" + MAXWP + ")! Hub -> Travel -> Teleport Pillars. Rename it there.");
      try { p.playSound("beacon.activate"); } catch {}
    } catch {}
  });

  // breaking a lodestone removes any pillar registered at that spot (any owner's)
  world.afterEvents.playerBreakBlock.subscribe((ev) => {
    try {
      if (ev.brokenBlockPermutation.type.id !== BLOCK) return;
      const loc = ev.block.location;
      const key = ev.player.dimension.id + "|" + loc.x + "|" + loc.y + "|" + loc.z;
      for (const p of world.getAllPlayers()) {
        const wp = getWp(p);
        const left = wp.filter((w) => keyOf(w) !== key);
        if (left.length !== wp.length) {
          setWp(p, left);
          p.sendMessage("\u00a7e[Tele] A teleport pillar was destroyed (" + left.length + "/" + MAXWP + " left).");
        }
      }
    } catch {}
  });
}

export async function openWaypoints(player) {
  const wp = getWp(player);
  if (wp.length === 0) {
    return actionMenu(player, "Teleport Pillars",
      "You have no pillars yet.\n\nPlace a \u00a7bLODESTONE\u00a7r anywhere to create one (max " + MAXWP + "). Break it to remove. Craft: chiseled stone bricks + 1 netherite ingot.",
      [{ label: "OK", icon: "textures/ui/buttons/bubble_no" }], "pokedex_light_blue");
  }
  const sel = await actionMenu(player, "Teleport Pillars (" + wp.length + "/" + MAXWP + ")",
    "Tap a pillar to travel. Place lodestones to add more.",
    wp.map((w) => ({ label: "\u26a1 " + w.n + "\n\u00a77" + w.x + ", " + w.y + ", " + w.z + " \u00a78" + w.dim.replace("minecraft:", ""), icon: "textures/blocks/lodestone_top" })),
    "pokedex_light_blue");
  if (sel < 0) return;
  const w = wp[sel];
  const act = await actionMenu(player, w.n, "\u00a77" + w.x + ", " + w.y + ", " + w.z, [
    { label: "\u00a7aTeleport here", icon: "textures/items/ender_pearl" },
    { label: "Rename", icon: "textures/items/name_tag" },
    { label: "\u00a7cForget this pillar\n\u00a78(block stays in the world)", icon: "textures/ui/buttons/bubble_no" },
  ], "pokedex_light_blue");
  if (act === 0) {
    try {
      player.teleport({ x: w.x + 0.5, y: w.y + 1, z: w.z + 0.5 }, { dimension: world.getDimension(w.dim) });
      try { player.playSound("mob.endermen.portal"); } catch {}
    } catch { player.sendMessage("\u00a7c[Tele] Teleport failed."); }
  } else if (act === 1) {
    const mf = new ModalFormData().title("Rename pillar");
    mf.textField("Name:", "e.g. Farm lua", { defaultValue: w.n });
    let res;
    try { res = await mf.show(player); } catch { return; }
    if (res.canceled) return;
    w.n = String(res.formValues[0]).trim().slice(0, 24) || w.n;
    setWp(player, wp);
    player.sendMessage("\u00a7a[Tele] Renamed to \u00a7f" + w.n);
  } else if (act === 2) {
    if (!(await confirmForm(player, "Forget pillar \"" + w.n + "\"? The lodestone block stays where it is."))) return;
    wp.splice(sel, 1);
    setWp(player, wp);
    player.sendMessage("\u00a7e[Tele] Forgotten.");
    return openWaypoints(player);
  }
}
