// guards.js - Pokemon Guards. Deploy Pokemon FROM YOUR PC around your claimed
// land: they stand guard, auto-attack hostile MOBS that come near, and never
// touch players or other Pokemon (SERP pokemon are family wandering_trader/mob,
// hostiles are family "monster" - we only ever target "monster").
//
// SERP has no real combat AI (battles are UI-based), so combat is script-driven:
// each guard scans for hostiles, lunges and applies level-scaled damage.
// Guards carry the "tamed" tag -> SERP's catch flow ignores them (no stealing).
// The Pokemon's PC tag stays on you - the guard is a projection; if a mob
// downs it, it just respawns at its post after 5 minutes. Nothing is ever lost.
import { world, system } from "@minecraft/server";
import { actionMenu, confirmForm, isAdmin } from "./forms.js";
import { getParty, getPC, level as lvlOf, isShiny, displayName } from "./serpdata.js";
import { POKENAMES } from "./pokenames.js";
import { SPECIES } from "./speciesdata.js";
import { claimAt } from "./claims.js";

const PROP = "sl:guards"; // [{gid, owner, ownerName, dex, v, lvl, name, x,y,z, downUntil}]
const DEFAULT_MAXG = 6;   // Pokemon share Minecraft's "monster" mob cap: every
                          // guard standing around your base is one wild Pokemon
                          // that can no longer spawn. Keep this modest.
const HARD_MAXG = 20;
function maxGuards() {
  try {
    const v = Number(world.getDynamicProperty("sl:maxguards"));
    return v >= 1 && v <= HARD_MAXG ? v : DEFAULT_MAXG;
  } catch { return DEFAULT_MAXG; }
}
export function setMaxGuards(n) {
  try { world.setDynamicProperty("sl:maxguards", Math.max(1, Math.min(HARD_MAXG, Math.floor(n)))); } catch {}
}
export function guardLimits() { return { cur: maxGuards(), hard: HARD_MAXG, def: DEFAULT_MAXG }; }
const RANGE = 10;      // aggro radius
const LEASH = 7;       // guards hold their post instead of wandering indoors
const DOWN_MS = 5 * 60000;

function all() { try { return JSON.parse(world.getDynamicProperty(PROP) ?? "[]"); } catch { return []; } }
function save(a) { try { world.setDynamicProperty(PROP, JSON.stringify(a)); } catch {} }
function mine(p) { return all().filter((g) => g.owner === p.id); }

function findEnt(g) {
  try { return [...world.getDimension("overworld").getEntities({ tags: ["slg_" + g.gid] })][0] ?? null; } catch { return null; }
}

// displayName() can return a rawtext object (species translate key) - guards
// need a plain string: nickname if set, else species name from POKENAMES.
function plainName(f) {
  const dn = displayName(f);
  if (typeof dn === "string" && dn) return dn;
  return POKENAMES[String(Number(f[2]))] ?? ("#" + f[2]);
}

// safe display name even for unmigrated legacy records
function nameOf(g) {
  return (typeof g.name === "string" && g.name && g.name !== "[object Object]")
    ? g.name : (POKENAMES[String(g.dex)] ?? ("#" + g.dex));
}

function desiredName(g) {
  // ball glyph + name + level + shiny sparkle (SERP glyph font)
  return "\u00a7b\u2691 \u00a7f\uE127 \u00a7b" + nameOf(g) + " \u00a77Lv." + g.lvl + (g.v > 2 ? " \u00a76\uE132" : "") + " \u00a78(guard)";
}

const lastSpawn = new Map(); // gid -> ts, anti-runaway cooldown

function setupGuardEntity(e, g, owner) {
  try {
    // Real combat AI (added by SerpLumen's entity override): the Pokemon hunts
    // and melee-attacks hostile mobs by itself - no script damage loop, no
    // teleporting, and no more 0,0,0 drift (we removed move_towards_restriction).
    try { e.triggerEvent("sl:guard_on"); } catch {}
    e.addTag("tamed");
    e.addTag("sl_guard");
    e.addTag("slg_" + g.gid);
    e.nameTag = desiredName(g);
    if (g.v > 2) { try { e.setProperty("serp:v", g.v); } catch {} }
  } catch {}
}

function materialize(g) {
  const dim = world.getDimension("overworld");
  let e;
  try { e = dim.spawnEntity("pokemon:p" + g.dex, { x: g.x, y: g.y, z: g.z }); } catch { return null; }
  const owner = world.getAllPlayers().find((p) => p.id === g.owner);
  setupGuardEntity(e, g, owner);
  lastSpawn.set(g.gid, Date.now());
  return e;
}

// ---------- UI ----------
export async function openGuards(player) {
  const my = mine(player);
  const buttons = my.map((g) => {
    const down = g.downUntil > Date.now();
    return { label: nameOf(g) + " Lv." + g.lvl + (g.v > 2 ? " \uE132" : "") + "\n\u00a78(" + Math.round(g.x) + "," + Math.round(g.z) + ")" + (down ? " \u00a7cdown " + Math.ceil((g.downUntil - Date.now()) / 60000) + "m" : " \u00a7aon duty") + " - tap to recall", icon: "textures/items/carrot_on_a_stick" };
  });
  buttons.push({ label: "\u00a7aDeploy a Pokemon HERE\n\u00a78From your PC - must be on your land", icon: "textures/items/ender_eye" });
  const sel = await actionMenu(player, "Pokemon Guards",
    "Guards: \u00a7b" + my.length + "\u00a7r/" + maxGuards() + "\u00a7r  \u00a78(from your party and your PC)\u00a7r\nThey roam your land with their own AI and attack hostile mobs within " + RANGE + " blocks - never players or Pokemon. Defeated guards respawn after 5 min.",
    buttons, "pokedex_cyan");
  if (sel < 0) return;
  if (sel === my.length) return deploy(player);
  // recall
  const g = my[sel];
  if (!(await confirmForm(player, "Recall " + nameOf(g) + " back to rest?"))) return;
  const e = findEnt(g);
  try { if (e) e.remove(); } catch {}
  removeText(g);
  save(all().filter((x) => x.gid !== g.gid));
  player.sendMessage("\u00a7e[Guards] " + nameOf(g) + " recalled.");
  return openGuards(player);
}

async function deploy(player) {
  if (mine(player).length >= maxGuards()) return player.sendMessage("\u00a7c[Guards] Max " + maxGuards() + " guards - recall one first.");
  if (player.dimension.id !== "minecraft:overworld") return player.sendMessage("\u00a7c[Guards] Overworld only.");
  const c = claimAt("minecraft:overworld", player.location.x, player.location.z);
  if (!c || (c.owner !== player.id && !c.members.some((m) => m.id === player.id)))
    return player.sendMessage("\u00a7c[Guards] Stand on YOUR claimed land to deploy (Hub -> Land Claims).");
  // ALL your Pokemon: current team first, then the whole PC
  const box = [
    ...getParty(player).map((x) => ({ ...x, from: "team" })),
    ...getPC(player).map((x) => ({ ...x, from: "pc" })),
  ];
  if (box.length === 0) return player.sendMessage("\u00a7c[Guards] You have no Pokemon yet.");
  const PAGE = 22;
  const pages = Math.max(1, Math.ceil(box.length / PAGE));
  let pageNo = 0, f = null;
  while (f === null) {
    const slice = box.slice(pageNo * PAGE, pageNo * PAGE + PAGE);
    const buttons = slice.map(({ fields, from }) => ({
      label: (from === "team" ? "\u00a7a[team] \u00a7r" : "") + plainName(fields) + " Lv." + lvlOf(fields) + (isShiny(fields) ? " \uE132" : ""),
      icon: "textures/items/carrot_on_a_stick",
    }));
    if (pages > 1) buttons.push({ label: "Next page (" + (pageNo + 1) + "/" + pages + ")", icon: "textures/ui/refresh" });
    const sel = await actionMenu(player, "Pick a guard", "All your Pokemon (" + box.length + "):", buttons, "pokedex_cyan");
    if (sel < 0) return;
    if (pages > 1 && sel === slice.length) { pageNo = (pageNo + 1) % pages; continue; }
    f = slice[sel].fields;
  }
  const sp = SPECIES[String(Number(f[2]))];
  const isWater = sp && (sp[4] === 18 || sp[5] === 18); // 18 = Water
  if (isWater) {
    let wet = false;
    try {
      const b = player.dimension.getBlock({ x: Math.floor(player.location.x), y: Math.floor(player.location.y), z: Math.floor(player.location.z) });
      const b2 = player.dimension.getBlock({ x: Math.floor(player.location.x), y: Math.floor(player.location.y) - 1, z: Math.floor(player.location.z) });
      wet = !!(b?.typeId?.includes("water") || b2?.typeId?.includes("water"));
    } catch {}
    if (!wet && !(await confirmForm(player,
        "\u00a7b" + plainName(f) + "\u00a7r is a \u00a7bWater\u00a7r type - on dry land it will look stranded and won't move naturally.\n\nStand in a pond/pool on your land for the best result.\n\nDeploy here anyway?"))) return;
  }
  const g = {
    gid: "g" + Date.now(),
    owner: player.id, ownerName: player.name,
    dex: Number(f[2]), v: Number(f[3]) || 1, lvl: lvlOf(f),
    name: plainName(f),
    x: Math.floor(player.location.x) + 0.5, y: Math.round(player.location.y), z: Math.floor(player.location.z) + 0.5,
    downUntil: 0,
  };
  const a = all();
  a.push(g);
  save(a);
  materialize(g);
  player.sendMessage("\u00a7a[Guards] \u00a7l" + g.name + "\u00a7r\u00a7a is now guarding this spot!");
  try { player.playSound("random.levelup"); } catch {}
}

// ---------- combat + upkeep ----------
function migrateNames() {
  try {
    const a = all();
    let dirty = false;
    for (const g of a) {
      if (typeof g.name !== "string" || g.name === "[object Object]" || !g.name) {
        g.name = POKENAMES[String(g.dex)] ?? ("#" + g.dex);
        dirty = true;
      }
    }
    if (dirty) save(a);
  } catch {}
}

// floating name: invisible always-show text entity hovering above the guard
function textTag(g) { return "slgt_" + g.gid; }
function findText(g) {
  try { return [...world.getDimension("overworld").getEntities({ tags: [textTag(g)] })][0] ?? null; } catch { return null; }
}
function ensureText(g, guardEnt) {
  let t = findText(g);
  const pos = { x: guardEnt.location.x, y: guardEnt.location.y + 2.3, z: guardEnt.location.z };
  if (!t) {
    try {
      t = world.getDimension("overworld").spawnEntity("sl:text", pos);
      t.addTag("sl_guardtxt");
      t.addTag(textTag(g));
    } catch { return; }
  } else {
    try { t.teleport(pos); } catch {}
  }
  try { const want = desiredName(g); if (t.nameTag !== want) t.nameTag = want; } catch {}
}
function removeText(g) {
  const t = findText(g);
  try { if (t) t.remove(); } catch {}
}

// used by palbase.js
export function guardsOf(playerId) { return all().filter((x) => x.owner === playerId); }
export function guardName(g) { return nameOf(g); }

export function initGuards() {
  migrateNames();
  // guard defeated -> 5 min downtime, then auto-respawn at post
  world.afterEvents.entityDie.subscribe((ev) => {
    try {
      const dead = ev.deadEntity;
      if (!dead || !dead.getTags().includes("sl_guard")) return;
      const gtag = dead.getTags().find((t) => t.startsWith("slg_"));
      if (!gtag) return;
      const a = all();
      const g = a.find((x) => "slg_" + x.gid === gtag);
      if (!g) return;
      g.downUntil = Date.now() + DOWN_MS;
      save(a);
      removeText(g);
      lastSpawn.delete(g.gid); // downtime itself is the cooldown - respawn promptly after
      for (const p of world.getAllPlayers()) {
        if (p.id === g.owner) p.sendMessage("\u00a7c[Guards] " + nameOf(g) + " was defeated defending your land! Back on duty in 5 min.");
      }
    } catch {}
  });

  // upkeep: (re)materialize guards whose post is loaded
  system.runInterval(() => {
    try {
      const players = world.getAllPlayers();
      if (players.length === 0) return;
      const dim = world.getDimension("overworld");
      for (const g of all()) {
        if (g.downUntil > Date.now()) continue;
        // dedupe: never allow 2 entities with the same guard tag
        let tagged = [];
        try { tagged = [...dim.getEntities({ tags: ["slg_" + g.gid] })]; } catch {}
        if (tagged.length > 1) for (const extra of tagged.slice(1)) { try { extra.remove(); } catch {} }
        if (tagged.length > 0) continue;
        const near = players.some((p) => p.dimension.id === "minecraft:overworld" &&
          (p.location.x - g.x) ** 2 + (p.location.z - g.z) ** 2 <= 60 * 60);
        if (!near) continue;
        // ADOPT: if an untagged same-species pokemon stands at the post (e.g. a
        // SERP-side replacement of our spawn), claim it instead of spawning more
        let adopted = false;
        try {
          for (const c of dim.getEntities({ location: { x: g.x, y: g.y, z: g.z }, maxDistance: 5, type: "pokemon:p" + g.dex })) {
            if (c.typeId !== "pokemon:p" + g.dex) continue; // trust nothing
            const dx2 = (c.location.x - g.x) ** 2 + (c.location.z - g.z) ** 2;
            if (dx2 > 25) continue;
            const ts = c.getTags();
            if (ts.includes("sl_guard") || ts.includes("tamed")) continue;
            setupGuardEntity(c, g, players.find((p) => p.id === g.owner));
            adopted = true;
            break;
          }
        } catch {}
        if (adopted) continue;
        // cooldown-gated respawn: never more than once per 60s per guard
        if (Date.now() - (lastSpawn.get(g.gid) ?? 0) < 60000) continue;
        materialize(g);
      }
      // orphan floating-text cleanup (guard gone or registry deleted)
      try {
        const reg = all();
        for (const t of world.getDimension("overworld").getEntities({ tags: ["sl_guardtxt"] })) {
          const tg = t.getTags().find((x) => x.startsWith("slgt_"));
          const g2 = reg.find((x) => "slgt_" + x.gid === tg);
          if (!g2 || g2.downUntil > Date.now()) { try { t.remove(); } catch {} }
        }
      } catch {}
    } catch {}
  }, 100);

  // Upkeep only: the guards fight with their own AI now. This loop just keeps
  // names/plates fresh, re-arms the AI group if SERP resets it, and brings a
  // guard home if it wandered off the land.
  system.runInterval(() => {
    try {
      const players = world.getAllPlayers();
      if (players.length === 0) return;
      const dim = world.getDimension("overworld");
      const guards = [...dim.getEntities({ tags: ["sl_guard"] })];
      if (guards.length === 0) return;
      const reg = all();
      for (const e of guards) {
        const gtag = e.getTags().find((t) => t.startsWith("slg_"));
        const g = reg.find((x) => "slg_" + x.gid === gtag);
        if (!g) { try { e.remove(); } catch {} continue; }
        try { const want = desiredName(g); if (e.nameTag !== want) e.nameTag = want; } catch {}
        try { e.triggerEvent("sl:guard_on"); } catch {}
        ensureText(g, e);

        const claim = claimAt("minecraft:overworld", g.x, g.z);
        const outOfBounds = claim
          ? (e.location.x < claim.x1 - 12 || e.location.x > claim.x2 + 12 ||
             e.location.z < claim.z1 - 12 || e.location.z > claim.z2 + 12)
          : ((e.location.x - g.x) ** 2 + (e.location.z - g.z) ** 2 > 48 * 48);
        if (outOfBounds) {
          try { e.remove(); } catch {}
          try { removeText(g); } catch {}
          lastSpawn.set(g.gid, 0);
        }
      }
    } catch {}
  }, 60);
}
