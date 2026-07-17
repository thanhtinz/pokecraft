// claims.js - land claims for the Realm. Square regions (full world height),
// stored in a world dynamic property, enforced with beforeEvents - stable
// API only, zero SERP overlap.
//   - Outsiders can't break/place blocks, or open containers/redstone
//     (doors and walking are fine; fighting Pokemon is untouched)
//   - Explosions never damage claimed blocks (impact list is filtered,
//     the explosion itself still happens outside)
//   - Owners add members to build together; admins bypass + manage all
import { world, system, ItemStack } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm, isAdmin } from "./forms.js";
import { isLow } from "./perf.js";

const PROP = "sl:claims"; // [{id, owner, ownerName, dim, x1,z1,x2,z2, members:[{id,name}]}]
const CFG = {
  maxClaims: 2,
  minRadius: 8,
  playerMaxRadius: 24,  // what a player can reach on their own (49x49)
  hardMaxRadius: 48,    // what an admin can grant (97x97)
  spawnGuard: 64,
};
const LIMITS = "sl:claimlimit"; // world: { playerId: radius } - admin-granted upgrades

function limitOf(p) {
  try {
    const r = JSON.parse(world.getDynamicProperty(LIMITS) ?? "{}");
    return Math.min(CFG.hardMaxRadius, Math.max(CFG.playerMaxRadius, r[p.id] ?? 0));
  } catch { return CFG.playerMaxRadius; }
}
function setLimit(pid, r) {
  let all2 = {};
  try { all2 = JSON.parse(world.getDynamicProperty(LIMITS) ?? "{}"); } catch {}
  all2[pid] = r;
  try { world.setDynamicProperty(LIMITS, JSON.stringify(all2)); } catch {}
}

function all() { try { return JSON.parse(world.getDynamicProperty(PROP) ?? "[]"); } catch { return []; } }
function save(a) { try { world.setDynamicProperty(PROP, JSON.stringify(a)); } catch {} }

// claims usable as "home" teleports (yours + ones you're a member of)
export function homeClaims(player) {
  return all().filter((cl) => cl.owner === player.id || cl.members.some((m) => m.id === player.id));
}

export function claimAt(dimId, x, z) {
  x = Math.floor(x); z = Math.floor(z);
  return all().find((c) => c.dim === dimId && x >= c.x1 && x <= c.x2 && z >= c.z1 && z <= c.z2) ?? null;
}
function overlaps(dimId, x1, z1, x2, z2) {
  return all().some((c) => c.dim === dimId && x1 <= c.x2 && x2 >= c.x1 && z1 <= c.z2 && z2 >= c.z1);
}
function canBuild(player, c) {
  if (!c) return true;
  if (c.owner === player.id) return true;
  if (c.members.some((m) => m.id === player.id)) return true;
  return isAdmin(player);
}
function sizeOf(c) { return (c.x2 - c.x1 + 1) + "x" + (c.z2 - c.z1 + 1); }

const BLOCKED_INTERACT = ["chest", "barrel", "furnace", "smoker", "hopper", "dropper", "dispenser", "shulker", "button", "lever", "anvil", "grindstone", "brewing", "beacon", "crafter", "smithing", "lectern", "jukebox", "decorated_pot"];

function deny(player, c) {
  try { player.onScreenDisplay.setActionBar("\u00a7c\u26d4 " + c.ownerName + "'s land"); } catch {}
}

// ---------- player UI ----------
export async function openClaims(player) {
  const mine = all().filter((c) => c.owner === player.id);
  const buttons = mine.map((c) => ({
    label: "Claim " + sizeOf(c) + "\n\u00a78(" + c.x1 + "," + c.z1 + ") to (" + c.x2 + "," + c.z2 + ") | " + c.members.length + " members",
    icon: "textures/blocks/grass_side_carried",
  }));
  buttons.push({ label: "\u00a7aClaim land HERE\n\u00a78Square around where you stand", icon: "textures/items/map_empty" });
  buttons.push({ label: "Pokemon Guards\n\u00a78Deploy PC Pokemon to defend your land", icon: "textures/items/carrot_on_a_stick" });
  buttons.push({ label: "Pokemon Workshop\n\u00a78Put your Pokemon to work", icon: "textures/items/iron_pickaxe" });
  const sel = await actionMenu(player, "Land Claims",
    "Yours: " + mine.length + "/" + CFG.maxClaims + ". Outsiders can't build or open containers on your land. Explosions can't touch it either.",
    buttons, "pokedex_light_blue");
  if (sel < 0) return;
  if (sel === mine.length) return createClaim(player);
  if (sel === mine.length + 1) { const { openGuards } = await import("./guards.js"); return openGuards(player); }
  if (sel === mine.length + 2) { const { openPalBase } = await import("./palbase.js"); return openPalBase(player); }
  return manageClaim(player, mine[sel]);
}

async function createClaim(player) {
  const mine = all().filter((c) => c.owner === player.id);
  if (mine.length >= CFG.maxClaims) return player.sendMessage("\u00a7c[Claims] You already have " + CFG.maxClaims + " claims - delete one first.");
  if (player.dimension.id !== "minecraft:overworld") return player.sendMessage("\u00a7c[Claims] Only in the overworld.");
  const mf = new ModalFormData().title("Claim land here");
  mf.slider("Radius (blocks from where you stand)", CFG.minRadius, limitOf(player), { valueStep: 1, defaultValue: Math.min(16, limitOf(player)) });
  let res;
  try { res = await mf.show(player); } catch { return; }
  if (res.canceled) return;
  const r = Number(res.formValues[0]);
  const px = Math.floor(player.location.x), pz = Math.floor(player.location.z);
  const x1 = px - r, z1 = pz - r, x2 = px + r, z2 = pz + r;
  // spawn guard
  let s = { x: 0, z: 0 };
  try { const w = world.getDefaultSpawnLocation(); s = { x: w.x, z: w.z }; } catch {}
  if (x1 - CFG.spawnGuard <= s.x && x2 + CFG.spawnGuard >= s.x && z1 - CFG.spawnGuard <= s.z && z2 + CFG.spawnGuard >= s.z)
    return player.sendMessage("\u00a7c[Claims] Too close to world spawn (" + CFG.spawnGuard + " block guard).");
  if (overlaps("minecraft:overworld", x1, z1, x2, z2))
    return player.sendMessage("\u00a7c[Claims] Overlaps someone's existing claim.");
  const a = all();
  a.push({ id: "c" + Date.now(), owner: player.id, ownerName: player.name, dim: "minecraft:overworld", x1, z1, x2, z2, y: Math.round(player.location.y), members: [] });
  save(a);
  player.sendMessage("\u00a7a[Claims] Claimed \u00a7l" + (r * 2 + 1) + "x" + (r * 2 + 1) + "\u00a7r\u00a7a around you. Your land is protected!");
  try { player.playSound("random.levelup"); } catch {}
}

async function manageClaim(player, c) {
  const sel = await actionMenu(player, "Your claim " + sizeOf(c),
    "Corner (" + c.x1 + "," + c.z1 + ") to (" + c.x2 + "," + c.z2 + ")\nMembers: " + (c.members.length ? c.members.map((m) => m.name).join(", ") : "\u00a78none"),
    [
      { label: "Add member\n\u00a78They can build here", icon: "textures/items/name_tag" },
      { label: "Remove member", icon: "textures/ui/buttons/bubble_no" },
      { label: (c.lockDoors ? "\u00a7aDoors: LOCKED" : "Doors: open") + "\n\u00a78Outsiders " + (c.lockDoors ? "can't" : "can") + " open doors/gates", icon: "textures/items/iron_door" },
      { label: (c.noMobs ? "\u00a7aNo-mob zone: ON" : "No-mob zone: off") + "\n\u00a78Push wild Pokemon & mobs out", icon: "textures/blocks/barrier" },
      { label: "\u00a7aExpand / resize\n\u00a78Up to " + (limitOf(player) * 2 + 1) + "x" + (limitOf(player) * 2 + 1) + " for you", icon: "textures/items/map_empty" },
      { label: "\u00a7bShow borders\n\u00a78Outline the land for 12 s", icon: "textures/items/blaze_powder" },
      { label: "\u00a7cDelete this claim", icon: "textures/blocks/barrier" },
    ], "pokedex_light_blue");
  if (sel < 0) return;
  const a = all();
  const live = a.find((x) => x.id === c.id);
  if (!live) return;
  if (sel === 0) {
    const others = world.getAllPlayers().filter((p) => p.id !== player.id && !live.members.some((m) => m.id === p.id));
    if (others.length === 0) return player.sendMessage("\u00a7e[Claims] Nobody online to add.");
    const ps = await actionMenu(player, "Add member", "", others.map((p) => ({ label: p.name })), "pokedex_light_blue");
    if (ps < 0) return;
    live.members.push({ id: others[ps].id, name: others[ps].name });
    save(a);
    player.sendMessage("\u00a7a[Claims] " + others[ps].name + " can now build on your land.");
    others[ps].sendMessage("\u00a7a[Claims] " + player.name + " added you to their land!");
  } else if (sel === 2) {
    live.lockDoors = !live.lockDoors;
    save(a);
    player.sendMessage(live.lockDoors ? "\u00a7a[Claims] Doors locked - only you & members can open them." : "\u00a7e[Claims] Doors are open to everyone again.");
    return manageClaim(player, live);
  } else if (sel === 3) {
    live.noMobs = !live.noMobs;
    save(a);
    player.sendMessage(live.noMobs ? "\u00a7a[Claims] No-mob zone ON - wild Pokemon & mobs get pushed out." : "\u00a7e[Claims] No-mob zone off.");
    return manageClaim(player, live);
  } else if (sel === 1) {
    if (live.members.length === 0) return player.sendMessage("\u00a7e[Claims] No members to remove.");
    const ms = await actionMenu(player, "Remove member", "", live.members.map((m) => ({ label: m.name })), "pokedex_light_blue");
    if (ms < 0) return;
    const gone = live.members.splice(ms, 1)[0];
    save(a);
    player.sendMessage("\u00a7e[Claims] Removed " + gone.name + ".");
  } else if (sel === 4) {
    return expandClaim(player, live, a);
  } else if (sel === 5) {
    showBorder(player, { x1: live.x1, z1: live.z1, x2: live.x2, z2: live.z2 }, "minecraft:villager_happy");
    return;
  } else if (sel === 6) {
    if (!(await confirmForm(player, "Delete this claim? Your land loses ALL protection."))) return;
    save(a.filter((x) => x.id !== c.id));
    player.sendMessage("\u00a7e[Claims] Claim deleted.");
  }
}

async function expandClaim(player, c, a) {
  const cx = Math.floor((c.x1 + c.x2) / 2), cz = Math.floor((c.z1 + c.z2) / 2);
  const curR = Math.floor((c.x2 - c.x1) / 2);
  const mf = new ModalFormData().title("Expand your land");
  const lim = limitOf(player);
  mf.slider("New radius from the centre (" + cx + ", " + cz + ")", CFG.minRadius, lim, { valueStep: 1, defaultValue: Math.max(Math.min(curR, lim), CFG.minRadius) });
  let res;
  try { res = await mf.show(player); } catch { return; }
  if (res.canceled) return;
  const r = Math.min(Number(res.formValues[0]), lim);
  if (r === curR) return player.sendMessage("\u00a7e[Claims] Same size - nothing changed.");
  const x1 = cx - r, z1 = cz - r, x2 = cx + r, z2 = cz + r;

  // spawn guard
  let s = { x: 0, z: 0 };
  try { const w = world.getDefaultSpawnLocation(); s = { x: w.x, z: w.z }; } catch {}
  if (x1 - CFG.spawnGuard <= s.x && x2 + CFG.spawnGuard >= s.x && z1 - CFG.spawnGuard <= s.z && z2 + CFG.spawnGuard >= s.z)
    return player.sendMessage("\u00a7c[Claims] That size would reach the world spawn guard (" + CFG.spawnGuard + " blocks).");

  // must not overlap ANY other claim (its own old area is fine)
  const clash = a.find((o) => o.id !== c.id && o.dim === c.dim && x1 <= o.x2 && x2 >= o.x1 && z1 <= o.z2 && z2 >= o.z1);
  if (clash) return player.sendMessage("\u00a7c[Claims] That size would overlap " + clash.ownerName + "'s claim - try a smaller radius.");

  // shrinking: warn if it would cut things loose
  if (r < curR && !(await confirmForm(player,
      "Shrink from " + (curR * 2 + 1) + "x" + (curR * 2 + 1) + " to " + (r * 2 + 1) + "x" + (r * 2 + 1) + "?\n\n\u00a7cAnything outside the new border loses protection\u00a7r (chests, builds, roaming Pokemon)."))) return;

  c.x1 = x1; c.z1 = z1; c.x2 = x2; c.z2 = z2;
  save(a);
  player.sendMessage((r > curR ? "\u00a7a[Claims] Expanded to " : "\u00a7e[Claims] Resized to ") + (r * 2 + 1) + "x" + (r * 2 + 1) + " blocks!");
  showBorder(player, { x1, z1, x2, z2 }, "minecraft:villager_happy");
  try { player.playSound("random.levelup"); } catch {}
}

// ---------- admin UI ----------
const MACHINE_IDS = ["serp:pc_box", "serp:healing_machine", "serp:trade_machine", "serp:tm_machine", "serp:mini_lab", "serp:rotomarket", "serp:communicator", "serp:slot_machine"];

export async function openMachinePurge(admin) {
  let found = [];
  try {
    for (const e of admin.dimension.getEntities({ location: admin.location, maxDistance: 24 })) {
      if (MACHINE_IDS.includes(e.typeId)) found.push(e);
    }
  } catch {}
  const sel = await actionMenu(admin, "Purge machines",
    found.length
      ? "\u00a7e" + found.length + "\u00a7r SERP machine(s) within 24 blocks:\n" + found.slice(0, 8).map((e) => "\u00a78- " + e.typeId.replace("serp:", "") + " (" + Math.floor(e.location.x) + ", " + Math.floor(e.location.z) + ")").join("\n")
      : "No SERP machines within 24 blocks of you.",
    [
      { label: found.length ? "\u00a7cDelete all " + found.length + " nearby machines" : "\u00a78(nothing nearby)", icon: "textures/blocks/barrier" },
      { label: "\u00a74Delete EVERY machine in loaded chunks\n\u00a78Server-wide cleanup", icon: "textures/blocks/tnt_side" },
    ], "pokedex_black");
  if (sel === 0) {
    if (!found.length) return;
    for (const e of found) { try { e.remove(); } catch {} }
    return admin.sendMessage("\u00a7a[Admin] Removed " + found.length + " machine(s).");
  }
  if (sel === 1) {
    if (!(await confirmForm(admin, "Delete \u00a7lEVERY\u00a7r SERP machine in all loaded chunks?\n\n\u00a7cThis includes machines players crafted and placed themselves.\u00a7r"))) return;
    let n = 0;
    for (const dimId of ["overworld", "nether", "the_end"]) {
      try {
        for (const e of world.getDimension(dimId).getEntities({})) {
          if (MACHINE_IDS.includes(e.typeId)) { try { e.remove(); n++; } catch {} }
        }
      } catch {}
    }
    admin.sendMessage("\u00a7a[Admin] Removed " + n + " machine(s) from loaded chunks.");
  }
}

export async function openGuardCapAdmin(admin) {
  const { guardLimits, setMaxGuards } = await import("./guards.js");
  const lim = guardLimits();
  const mf = new ModalFormData().title("Guard limit");
  mf.slider("Max guards per player", 1, lim.hard, { valueStep: 1, defaultValue: lim.cur });
  let res;
  try { res = await mf.show(admin); } catch { return; }
  if (res.canceled) return;
  const n = Number(res.formValues[0]);
  setMaxGuards(n);
  admin.sendMessage("\u00a7a[Admin] Guard limit set to " + n + " per player." +
    (n > 8 ? " \u00a7e(Heads-up: Pokemon share Minecraft's monster mob cap - many guards means fewer wild spawns.)" : ""));
}

export async function openClaimsAdmin(admin) {
  const a = all();
  if (a.length === 0) return admin.sendMessage("\u00a7e[Claims] No claims exist yet.");
  const sel = await actionMenu(admin, "All claims (" + a.length + ")", "Tap to manage:",
    a.map((c) => ({ label: c.ownerName + " - " + sizeOf(c) + "\n\u00a78(" + c.x1 + "," + c.z1 + ") " + c.dim.replace("minecraft:", ""), icon: "textures/blocks/grass_side_carried" })),
    "pokedex_black");
  if (sel < 0) return;
  const c = a[sel];
  const act = await actionMenu(admin, c.ownerName + "'s claim", sizeOf(c) + " at (" + c.x1 + "," + c.z1 + ")-(" + c.x2 + "," + c.z2 + ")\nSize limit: " + (limitOf({ id: c.owner }) * 2 + 1) + "x" + (limitOf({ id: c.owner }) * 2 + 1) + "\nMembers: " + (c.members.map((m) => m.name).join(", ") || "none"),
    [
      { label: "Teleport to it", icon: "textures/items/ender_pearl" },
      { label: "\u00a7cDelete claim", icon: "textures/blocks/barrier" },
      { label: "\u00a7eGrant a bigger size limit\n\u00a78Let this player expand further", icon: "textures/items/map_empty" },
      { label: "\u00a76Resize it now (admin)\n\u00a78Force any size up to " + (CFG.hardMaxRadius * 2 + 1), icon: "textures/items/diamond_pickaxe" },
    ], "pokedex_black");
  if (act === 2) {
    const mf = new ModalFormData().title("Size limit for " + c.ownerName);
    mf.slider("Max radius this player may claim", CFG.playerMaxRadius, CFG.hardMaxRadius, { valueStep: 1, defaultValue: limitOf({ id: c.owner }) });
    let r2;
    try { r2 = await mf.show(admin); } catch { return; }
    if (r2.canceled) return;
    const nr = Number(r2.formValues[0]);
    setLimit(c.owner, nr);
    admin.sendMessage("\u00a7a[Admin] " + c.ownerName + " can now claim up to " + (nr * 2 + 1) + "x" + (nr * 2 + 1) + ".");
    for (const p of world.getAllPlayers()) {
      if (p.id === c.owner) p.sendMessage("\u00a7a[Claims] An admin raised your land limit to \u00a7l" + (nr * 2 + 1) + "x" + (nr * 2 + 1) + "\u00a7r\u00a7a - expand it in Hub -> Land Claims!");
    }
    return;
  }
  if (act === 3) {
    const mf = new ModalFormData().title("Resize " + c.ownerName + "'s claim");
    mf.slider("Radius", CFG.minRadius, CFG.hardMaxRadius, { valueStep: 1, defaultValue: Math.floor((c.x2 - c.x1) / 2) });
    let r3;
    try { r3 = await mf.show(admin); } catch { return; }
    if (r3.canceled) return;
    const rr = Number(r3.formValues[0]);
    const cx2 = Math.floor((c.x1 + c.x2) / 2), cz2 = Math.floor((c.z1 + c.z2) / 2);
    const nx1 = cx2 - rr, nz1 = cz2 - rr, nx2 = cx2 + rr, nz2 = cz2 + rr;
    const clash = all().find((o) => o.id !== c.id && o.dim === c.dim && nx1 <= o.x2 && nx2 >= o.x1 && nz1 <= o.z2 && nz2 >= o.z1);
    if (clash) return admin.sendMessage("\u00a7c[Admin] That overlaps " + clash.ownerName + "'s claim.");
    const arr = all();
    const live2 = arr.find((x) => x.id === c.id);
    live2.x1 = nx1; live2.z1 = nz1; live2.x2 = nx2; live2.z2 = nz2;
    save(arr);
    admin.sendMessage("\u00a7a[Admin] Resized to " + (rr * 2 + 1) + "x" + (rr * 2 + 1) + ".");
    return;
  }
  if (act === 0) {
    try { admin.teleport({ x: (c.x1 + c.x2) / 2, y: 100, z: (c.z1 + c.z2) / 2 }, { dimension: world.getDimension("overworld") }); } catch {}
  } else if (act === 1) {
    if (!(await confirmForm(admin, "Delete " + c.ownerName + "'s claim?"))) return;
    save(all().filter((x) => x.id !== c.id));
    admin.sendMessage("\u00a7e[Claims] Deleted.");
  }
}

// ---------- border visualiser ----------
const showing = new Map(); // playerId -> { until, box }

export function showBorder(player, box, color) {
  showing.set(player.id, { until: Date.now() + 12000, box, color: color ?? "minecraft:endrod" });
  player.sendMessage("\u00a7b[Border] Showing the outline for 12 seconds.");
}

function drawBorders() {
  const now = Date.now();
  for (const p of world.getAllPlayers()) {
    const s = showing.get(p.id);
    if (!s) continue;
    if (isLow(p)) { showing.delete(p.id); continue; }
    if (s.until < now) { showing.delete(p.id); continue; }
    const { x1, z1, x2, z2 } = s.box;
    const y = Math.round(p.location.y) + 1;
    const step = Math.max(1, Math.round((x2 - x1) / 48));
    try {
      for (let x = x1; x <= x2; x += step) {
        p.dimension.spawnParticle(s.color, { x: x + 0.5, y, z: z1 + 0.5 });
        p.dimension.spawnParticle(s.color, { x: x + 0.5, y, z: z2 + 0.5 });
      }
      for (let z = z1; z <= z2; z += step) {
        p.dimension.spawnParticle(s.color, { x: x1 + 0.5, y, z: z + 0.5 });
        p.dimension.spawnParticle(s.color, { x: x2 + 0.5, y, z: z + 0.5 });
      }
      // corner pillars so you can see them from afar
      for (const [cx, cz] of [[x1, z1], [x1, z2], [x2, z1], [x2, z2]]) {
        for (let dy = 0; dy < 6; dy++) p.dimension.spawnParticle("minecraft:endrod", { x: cx + 0.5, y: y + dy, z: cz + 0.5 });
      }
    } catch {}
  }
}

// ---------- enforcement ----------
export function initClaims() {
  world.beforeEvents.playerBreakBlock.subscribe((ev) => {
    try {
      const c = claimAt(ev.player.dimension.id, ev.block.location.x, ev.block.location.z);
      if (c && !canBuild(ev.player, c)) { ev.cancel = true; system.run(() => deny(ev.player, c)); }
    } catch {}
  });
  // NOTE: stable 2.6.0 has no beforeEvents.playerPlaceBlock. Block placement
  // goes through itemStartUseOn -> afterEvents.playerPlaceBlock; we prevent it
  // with beforeEvents.itemUse? (not fired for block placement) - so instead we
  // revert: afterEvents.playerPlaceBlock fires with the placed block, and we
  // put the old block back instantly for outsiders (undo-grief).
  world.afterEvents.playerPlaceBlock.subscribe((ev) => {
    try {
      const p = ev.player, b = ev.block;
      const c = claimAt(p.dimension.id, b.location.x, b.location.z);
      if (!c || canBuild(p, c)) return;
      const perm = b.permutation;
      system.run(() => {
        try {
          b.setType("minecraft:air");
          // refund the block item so the outsider loses nothing
          let refund = null;
          try { refund = perm.getItemStack(1); } catch {}
          if (refund) { try { p.dimension.spawnItem(refund, p.location); } catch {} }
        } catch {}
        deny(p, c);
      });
    } catch {}
  });
  world.beforeEvents.playerInteractWithBlock.subscribe((ev) => {
    try {
      const id = ev.block.typeId;
      const c = claimAt(ev.player.dimension.id, ev.block.location.x, ev.block.location.z);
      if (!c || canBuild(ev.player, c)) return;
      const isDoor = id.includes("door") || id.includes("fence_gate"); // trapdoors included via "door"
      const blocked = BLOCKED_INTERACT.some((b) => id.includes(b)) || (c.lockDoors && isDoor);
      if (blocked) { ev.cancel = true; system.run(() => deny(ev.player, c)); }
    } catch {}
  });
  // explosions: strip claimed blocks from the impact list
  world.beforeEvents.explosion.subscribe((ev) => {
    try {
      const dimId = ev.dimension.id;
      const impacted = ev.getImpactedBlocks();
      const safe = impacted.filter((b) => !claimAt(dimId, b.location.x, b.location.z));
      if (safe.length !== impacted.length) ev.setImpactedBlocks(safe);
    } catch {}
  });
  // no-mob zones: gently push wild Pokemon / mobs out of flagged claims
  system.runInterval(() => {
    try {
      const zones = all().filter((cl) => cl.noMobs);
      if (zones.length === 0) return;
      const dim = world.getDimension("overworld");
      for (const cl of zones) {
        const cx = (cl.x1 + cl.x2) / 2, cz = (cl.z1 + cl.z2) / 2;
        const r = Math.max(cl.x2 - cl.x1, cl.z2 - cl.z1) / 2 + 1;
        let ents = [];
        try { ents = [...dim.getEntities({ location: { x: cx, y: cl.y ?? 64, z: cz }, maxDistance: r * 1.5, excludeTypes: ["minecraft:player", "minecraft:item", "minecraft:xp_orb"] })]; } catch { continue; }
        for (const e of ents) {
          try {
            const { x, z } = e.location;
            if (x < cl.x1 || x > cl.x2 + 1 || z < cl.z1 || z > cl.z2 + 1) continue; // outside
            if (e.getTags().includes("sl_guard")) continue;                            // guards stay
            // nearest edge, 3 blocks past it
            const dW = x - cl.x1, dE = cl.x2 + 1 - x, dN = z - cl.z1, dS = cl.z2 + 1 - z;
            const m = Math.min(dW, dE, dN, dS);
            let nx = x, nz = z;
            if (m === dW) nx = cl.x1 - 3; else if (m === dE) nx = cl.x2 + 4;
            else if (m === dN) nz = cl.z1 - 3; else nz = cl.z2 + 4;
            e.teleport({ x: nx, y: e.location.y, z: nz });
          } catch {}
        }
      }
    } catch {}
  }, 100);

  system.runInterval(drawBorders, 10);

  // entry/exit actionbar
  const inClaim = new Map();
  const lastPos = new Map();
  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      try {
        // cheap gate: if you have not crossed a block, nothing can have changed
        const key = Math.floor(p.location.x) + "," + Math.floor(p.location.z) + "," + p.dimension.id;
        if (lastPos.get(p.id) === key) continue;
        lastPos.set(p.id, key);
        const c = claimAt(p.dimension.id, p.location.x, p.location.z);
        const cur = c ? c.id : null;
        if (inClaim.get(p.id) === cur) continue;
        inClaim.set(p.id, cur);
        if (c) p.onScreenDisplay.setActionBar(canBuild(p, c) ? "\u00a7a\u2691 " + (c.owner === p.id ? "Your land" : c.ownerName + "'s land (member)") : "\u00a7e\u2691 " + c.ownerName + "'s land");
        else p.onScreenDisplay.setActionBar("\u00a77\u2691 Wilderness");
      } catch {}
    }
  }, 20);
}
