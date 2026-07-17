// machines.js - Machine anchors with auto-restore.
//
// Admin drops a SERP machine (PC box, healing/revive machine, trade, TM, lab,
// rotomarket, communicator, slot) at their feet and it's ANCHORED: its type +
// position are saved to a world dynamic property. An upkeep loop re-spawns any
// anchored machine that has gone missing, at its exact original spot - so if
// machines get griefed, culled, or wiped, they come back on their own.
//
// Same model as guards.js (NPC Guard): the registry is the source of truth; the
// entity is a projection. Nothing is ever permanently lost.
//
// Realms-safe: stable @minecraft/server 2.6.0 only. No owner is set on the
// re-spawned entity (the Script API can't set another entity's tameable owner),
// which is fine for shared server machines - anyone can use them, and admins
// remove them through this same panel, not by hitting them.
import { world, system } from "@minecraft/server";
import { actionMenu, confirmForm, isAdmin } from "./forms.js";

const PROP = "sl:machines"; // [{mid, type, dim, x, y, z}]
const HARD_MAX = 64;
const DIMS = ["overworld", "nether", "the_end"];

// label + spawn identifier. NOTE: SERP's "personal lab" entity id is
// serp:mini_lab (not serp:personal_lab) - verified against the SERP pack.
const MACHINES = [
  ["serp:pc_box", "PC Box"],
  ["serp:healing_machine", "Healing / Revive Machine"],
  ["serp:trade_machine", "Trade Machine"],
  ["serp:tm_machine", "TM Machine"],
  ["serp:mini_lab", "Personal Lab"],
  ["serp:rotomarket", "Rotom Market"],
  ["serp:communicator", "Communicator"],
  ["serp:slot_machine", "Slot Machine"],
];
const LABEL = Object.fromEntries(MACHINES.map(([id, l]) => [id, l]));
const VALID = new Set(MACHINES.map(([id]) => id));

function all() { try { return JSON.parse(world.getDynamicProperty(PROP) ?? "[]"); } catch { return []; } }
function save(a) { try { world.setDynamicProperty(PROP, JSON.stringify(a)); } catch {} }
function mtag(m) { return "slm_" + m.mid; }
function dimOf(m) { return world.getDimension(m.dim || "overworld"); }

function findEnt(m) {
  try { return [...dimOf(m).getEntities({ tags: [mtag(m)] })][0] ?? null; } catch { return null; }
}

const lastSpawn = new Map(); // mid -> ts, anti-spam cooldown

// Auto-protect: when on, every SERP machine a player walks near gets anchored
// automatically, so machines that despawn (SERP machines have no persistent
// component, so Bedrock culls them on chunk unload) are restored on their own.
// Admins can still place/anchor manually; this just covers the ones already in
// the world without anyone doing it by hand.
const AUTO_KEY = "sl:machauto";
export function autoProtectOn() {
  try { return world.getDynamicProperty(AUTO_KEY) !== false; } catch { return true; }
}
export function setAutoProtect(on) {
  try { world.setDynamicProperty(AUTO_KEY, !!on); } catch {}
}

function near2(ax, az, bx, bz, r) {
  return (ax - bx) ** 2 + (az - bz) ** 2 <= r * r;
}

// anchor a machine entity that isn't tracked yet (dedup against existing anchors)
function autoAnchor(e) {
  try {
    if (!VALID.has(e.typeId)) return;
    if (e.getTags().some((t) => t.startsWith("slm_"))) return; // already tracked
    const reg = all();
    const dimName = e.dimension.id.replace("minecraft:", "");
    // if an anchor of the same type already sits ~2 blocks away, adopt into it
    // instead of creating a duplicate.
    const dup = reg.find((m) => m.type === e.typeId && (m.dim || "overworld") === dimName &&
      near2(m.x, m.z, e.location.x, e.location.z, 2) && Math.abs((m.y ?? 0) - e.location.y) <= 2);
    if (dup) {
      try { e.addTag("sl_machine"); e.addTag(mtag(dup)); } catch {}
      return;
    }
    if (reg.length >= HARD_MAX) return;
    const m = {
      mid: "m" + Date.now() + Math.floor(Math.random() * 999),
      type: e.typeId, dim: dimName,
      x: Math.round(e.location.x * 100) / 100,
      y: Math.round(e.location.y),
      z: Math.round(e.location.z * 100) / 100,
    };
    reg.push(m);
    save(reg);
    try { e.addTag("sl_machine"); e.addTag(mtag(m)); } catch {}
  } catch {}
}

function materialize(m) {
  const dim = dimOf(m);
  let e;
  try { e = dim.spawnEntity(m.type, { x: m.x, y: m.y, z: m.z }); } catch { return null; }
  try {
    e.addTag("sl_machine");
    e.addTag(mtag(m));
    // turn it "on" if the machine supports it (pc/heal/etc use serp:on)
    try { e.triggerEvent("serp:on"); } catch {}
  } catch {}
  lastSpawn.set(m.mid, Date.now());
  return e;
}

export function initMachines() {
  // upkeep: re-spawn any anchored machine whose spot is loaded and empty
  system.runInterval(() => {
    try {
      const players = world.getAllPlayers();
      if (players.length === 0) return;
      const reg = all();
      if (reg.length === 0) return;
      for (const m of reg) {
        const dim = dimOf(m);
        // dedupe: never keep more than one entity per anchor
        let tagged = [];
        try { tagged = [...dim.getEntities({ tags: [mtag(m)] })]; } catch {}
        if (tagged.length > 1) for (const extra of tagged.slice(1)) { try { extra.remove(); } catch {} }
        if (tagged.length > 0) {
          // PIN: only snap back if the machine was genuinely dragged FAR off
          // its anchor (>3 blocks horizontally) - e.g. shoved by something.
          // We ignore the Y axis and small offsets entirely: SERP machines
          // settle onto the ground via physics, so their real Y and a tiny X/Z
          // drift are normal. Pinning those caused the machine to teleport-jitter
          // forever, fighting its own physics.
          const e = tagged[0];
          try {
            const dx = e.location.x - m.x, dz = e.location.z - m.z;
            if (dx * dx + dz * dz > 9) { // >3 blocks horizontal
              e.teleport({ x: m.x, y: e.location.y, z: m.z }, { dimension: dim });
            }
          } catch {}
          continue;
        }
        // only respawn when a player is near enough that the chunk is loaded
        const near = players.some((p) =>
          p.dimension.id === "minecraft:" + (m.dim || "overworld") &&
          (p.location.x - m.x) ** 2 + (p.location.z - m.z) ** 2 <= 60 * 60);
        if (!near) continue;
        // ADOPT an untagged same-type machine already sitting at the spot (e.g.
        // one a player placed there) instead of stacking a second one.
        let adopted = false;
        try {
          for (const c of dim.getEntities({ location: { x: m.x, y: m.y, z: m.z }, maxDistance: 3, type: m.type })) {
            if (c.typeId !== m.type) continue;
            if (c.getTags().some((t) => t.startsWith("slm_"))) continue;
            try { c.addTag("sl_machine"); c.addTag(mtag(m)); } catch {}
            adopted = true;
            break;
          }
        } catch {}
        if (adopted) continue;
        if (Date.now() - (lastSpawn.get(m.mid) ?? 0) < 30000) continue; // 30s cooldown
        materialize(m);
      }
    } catch {}
  }, 100);

  // auto-anchor loop: scan around each player and anchor any un-tracked SERP
  // machine, so machines placed by anyone (or spawned by the world) are
  // protected without an admin doing it by hand. Runs every 5s; respects the
  // toggle. This is what makes machines stop "disappearing when you join".
  system.runInterval(() => {
    try {
      if (!autoProtectOn()) return;
      for (const p of world.getAllPlayers()) {
        let dim, nearby;
        try {
          dim = p.dimension;
          nearby = dim.getEntities({ location: p.location, maxDistance: 32 });
        } catch { continue; }
        for (const e of nearby) {
          if (VALID.has(e.typeId)) autoAnchor(e);
        }
      }
    } catch {}
  }, 100);
}

// ---------- admin UI ----------
export function machineCount() { return all().length; }

export async function openMachineAnchors(admin) {
  if (!isAdmin(admin)) return admin.sendMessage("\u00a7cAdmins only.");
  const reg = all();
  const sel = await actionMenu(admin, "Machine Restore",
    reg.length
      ? "\u00a7b" + reg.length + "\u00a7r anchored machine(s) - auto-restored when missing.\n\u00a77Tap below to place a new one or manage existing anchors."
      : "No machines anchored yet.\n\u00a77Anchor the server's PC / healing / trade machines here so they \u00a7arestore themselves\u00a77 if griefed or wiped.",
    [
      { label: "\u00a7aPlace a machine HERE\n\u00a78At your feet, anchored + auto-restored", icon: "textures/items/repeater" },
      { label: "Manage anchors (" + reg.length + ")\n\u00a78Teleport to / remove", icon: "textures/ui/settings_glyph_color_2x" },
      { label: "Auto-protect: " + (autoProtectOn() ? "\u00a7aON" : "\u00a7cOFF") + "\n\u00a78Auto-anchor every machine so none despawn", icon: "textures/items/nether_star" },
    ], "pokedex_black");
  if (sel === 0) return placeMachine(admin);
  if (sel === 1) return manageAnchors(admin);
  if (sel === 2) {
    setAutoProtect(!autoProtectOn());
    admin.sendMessage("\u00a7e[Machines] Auto-protect " + (autoProtectOn() ? "\u00a7aON\u00a7e - every machine you pass will be anchored." : "\u00a7cOFF\u00a7e - only manually anchored machines are kept."));
    return openMachineAnchors(admin);
  }
}

async function placeMachine(admin) {
  const dimName = admin.dimension.id.replace("minecraft:", "");
  const sel = await actionMenu(admin, "Place Machine",
    "It appears at your feet and is anchored here in \u00a7f" + dimName + "\u00a7r.\nStand exactly where you want it.",
    MACHINES.map(([, label]) => ({ label, icon: "textures/items/repeater" })),
    "pokedex_black");
  if (sel < 0 || sel >= MACHINES.length) return openMachineAnchors(admin);
  if (all().length >= HARD_MAX) return admin.sendMessage("\u00a7c[Machines] Anchor limit (" + HARD_MAX + ") reached.");
  const [type] = MACHINES[sel];
  const m = {
    mid: "m" + Date.now() + Math.floor(Math.random() * 999),
    type,
    dim: dimName,
    x: Math.floor(admin.location.x) + 0.5,
    y: Math.round(admin.location.y),
    z: Math.floor(admin.location.z) + 0.5,
  };
  const a = all();
  a.push(m);
  save(a);
  const e = materialize(m);
  if (e) {
    admin.sendMessage("\u00a7a[Machines] \u00a7l" + LABEL[type] + "\u00a7r\u00a7a placed & anchored. It will restore itself if removed.");
    try { admin.playSound("random.orb"); } catch {}
  } else {
    admin.sendMessage("\u00a7e[Machines] Anchored " + LABEL[type] + ", but couldn't spawn it right here. Move a little and it'll restore automatically.");
  }
  return placeMachine(admin);
}

async function manageAnchors(admin) {
  const reg = all();
  if (reg.length === 0) { admin.sendMessage("\u00a7e[Machines] No anchors."); return openMachineAnchors(admin); }
  const sel = await actionMenu(admin, "Anchored Machines",
    "Tap a machine to teleport to it or remove its anchor:",
    reg.map((m) => ({
      label: (LABEL[m.type] ?? m.type) + "\n\u00a78" + (m.dim || "overworld") + " " + Math.round(m.x) + ", " + Math.round(m.y) + ", " + Math.round(m.z),
      icon: "textures/items/repeater",
    })), "pokedex_black");
  if (sel < 0 || sel >= reg.length) return openMachineAnchors(admin);
  const m = reg[sel];
  const act = await actionMenu(admin, LABEL[m.type] ?? m.type, "What do you want to do?", [
    { label: "\u00a7bTeleport to it", icon: "textures/items/ender_pearl" },
    { label: "\u00a7cRemove this anchor\n\u00a78Deletes the machine + stops restoring it", icon: "textures/ui/trash_light" },
  ], "pokedex_black");
  if (act === 0) {
    try { admin.teleport({ x: m.x, y: m.y + 1, z: m.z }, { dimension: dimOf(m) }); } catch { admin.sendMessage("\u00a7c[Machines] Teleport failed."); }
    return manageAnchors(admin);
  }
  if (act === 1) {
    if (!(await confirmForm(admin, "Remove this " + (LABEL[m.type] ?? "machine") + " anchor?\n\nThe machine is deleted and won't be restored anymore."))) return manageAnchors(admin);
    const e = findEnt(m);
    try { if (e) e.remove(); } catch {}
    lastSpawn.delete(m.mid);
    save(all().filter((x) => x.mid !== m.mid));
    admin.sendMessage("\u00a7e[Machines] Anchor removed.");
    return manageAnchors(admin);
  }
  return manageAnchors(admin);
}
