// npcs.js - anchored, non-despawning NPCs (nurse, officer, store, game corner,
// professor, trainer). Same model as machines.js: an admin drops an NPC at their
// feet, its type+role+position are saved to a world dynamic property, and an
// upkeep loop re-spawns it if it despawns and pins it to its exact spot so it
// "stands in one place" and never disappears.
//
// SERP's poke_npc despawns from distance and strolls within a home radius, so we
//   (1) re-spawn it when a player returns to a loaded chunk, and
//   (2) trigger its role event (serp:job1..4) on every (re)spawn - a fresh
//       poke_npc randomizes its job, so the role must be re-applied, and
//   (3) snap it back to the anchor if it drifts, keeping it put.
// Stable @minecraft/server 2.6.0 only. No runCommand.
import { world, system } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm, isAdmin } from "./forms.js";

const PROP = "sl:npcs"; // [{nid, type, job, name, dim, x, y, z}]
const HARD_MAX = 64;
const PIN_DIST = 1.5;   // snap back if the NPC wanders this far from its anchor

// role -> spawn entity + optional job event. job1=nurse, job2=officer,
// job3=store, job4=game corner (all on serp:poke_npc). profs/trainer are their
// own summonable entities.
const NPCS = [
  { key: "nurse",   label: "Y tá (Nurse Joy)",        type: "serp:poke_npc", job: "serp:job1", icon: "textures/items/potion_bottle_heal" },
  { key: "officer", label: "Cảnh sát (Officer Jenny)", type: "serp:poke_npc", job: "serp:job2", icon: "textures/items/iron_helmet" },
  { key: "store",   label: "Cửa hàng (Store)",         type: "serp:poke_npc", job: "serp:job3", icon: "textures/items/emerald" },
  { key: "game",    label: "Sòng bài (Game Corner)",   type: "serp:poke_npc", job: "serp:job4", icon: "textures/items/gold_ingot" },
  { key: "prof",    label: "Tiến sĩ (Professor)",      type: "serp:profs",    icon: "textures/items/book_written" },
  { key: "trainer", label: "Huấn luyện viên (Trainer)", type: "serp:trainer",  icon: "textures/items/iron_sword" },
];
const BYKEY = Object.fromEntries(NPCS.map((n) => [n.key, n]));

function all() { try { return JSON.parse(world.getDynamicProperty(PROP) ?? "[]"); } catch { return []; } }
function save(a) { try { world.setDynamicProperty(PROP, JSON.stringify(a)); } catch {} }
function ntag(n) { return "sln_" + n.nid; }
function dimOf(n) { return world.getDimension(n.dim || "overworld"); }
function labelOf(n) { return n.name || (BYKEY[n.key]?.label ?? n.key); }

function findEnt(n) {
  try { return [...dimOf(n).getEntities({ tags: [ntag(n)] })][0] ?? null; } catch { return null; }
}

const lastSpawn = new Map(); // nid -> ts, anti-spam cooldown

function setupNpc(e, n) {
  try {
    e.addTag("sl_npc");
    e.addTag(ntag(n));
    if (n.job) { try { e.triggerEvent(n.job); } catch {} } // re-apply the role
    if (n.name) { try { e.nameTag = n.name; } catch {} }
  } catch {}
}

function materialize(n) {
  const dim = dimOf(n);
  let e;
  try { e = dim.spawnEntity(n.type, { x: n.x, y: n.y, z: n.z }); } catch { return null; }
  setupNpc(e, n);
  lastSpawn.set(n.nid, Date.now());
  return e;
}

export function initNpcs() {
  system.runInterval(() => {
    try {
      const players = world.getAllPlayers();
      if (players.length === 0) return;
      const reg = all();
      if (reg.length === 0) return;
      for (const n of reg) {
        const dim = dimOf(n);
        let tagged = [];
        try { tagged = [...dim.getEntities({ tags: [ntag(n)] })]; } catch {}
        if (tagged.length > 1) for (const extra of tagged.slice(1)) { try { extra.remove(); } catch {} }
        if (tagged.length > 0) {
          // pin: keep the NPC standing on its anchor (it strolls within a home
          // radius otherwise). Snap back only when it has actually wandered off.
          const e = tagged[0];
          try {
            const dx = e.location.x - n.x, dz = e.location.z - n.z;
            if (dx * dx + dz * dz > PIN_DIST * PIN_DIST) {
              e.teleport({ x: n.x, y: e.location.y, z: n.z }, { dimension: dim });
            }
          } catch {}
          continue;
        }
        const near = players.some((p) =>
          p.dimension.id === "minecraft:" + (n.dim || "overworld") &&
          (p.location.x - n.x) ** 2 + (p.location.z - n.z) ** 2 <= 60 * 60);
        if (!near) continue;
        // adopt an untagged same-type NPC already standing here instead of stacking
        let adopted = false;
        try {
          for (const c of dim.getEntities({ location: { x: n.x, y: n.y, z: n.z }, maxDistance: 3, type: n.type })) {
            if (c.typeId !== n.type) continue;
            if (c.getTags().some((t) => t.startsWith("sln_"))) continue;
            setupNpc(c, n);
            adopted = true;
            break;
          }
        } catch {}
        if (adopted) continue;
        if (Date.now() - (lastSpawn.get(n.nid) ?? 0) < 20000) continue; // 20s cooldown
        materialize(n);
      }
    } catch {}
  }, 100);
}

// ---------- admin UI ----------
export function npcCount() { return all().length; }

export async function openNpcs(admin) {
  if (!isAdmin(admin)) return admin.sendMessage("§cAdmins only.");
  const reg = all();
  const sel = await actionMenu(admin, "NPCs (đứng yên, không mất)",
    reg.length
      ? "§b" + reg.length + "§r NPC đã neo - tự respawn nếu mất, tự đứng yên tại chỗ.\n§7Đặt NPC mới hoặc quản lý NPC đã có."
      : "Chưa có NPC nào.\n§7Đặt y tá / cảnh sát / cửa hàng / tiến sĩ... - chúng §ađứng yên một chỗ§7 và §akhông biến mất§7 như máy.",
    [
      { label: "§aĐặt NPC tại đây\n§8Tại chân bạn, neo + tự respawn", icon: "textures/items/villager_spawn_egg" },
      { label: "Quản lý NPC (" + reg.length + ")\n§8Tới chỗ / đổi tên / xoá", icon: "textures/ui/settings_glyph_color_2x" },
    ], "pokedex_black");
  if (sel === 0) return placeNpc(admin);
  if (sel === 1) return manageNpcs(admin);
}

async function placeNpc(admin) {
  const dimName = admin.dimension.id.replace("minecraft:", "");
  const sel = await actionMenu(admin, "Đặt NPC",
    "NPC xuất hiện tại chân bạn ở §f" + dimName + "§r và đứng yên tại đó.\nĐứng đúng chỗ bạn muốn rồi chọn:",
    NPCS.map((n) => ({ label: n.label, icon: n.icon })), "pokedex_black");
  if (sel < 0 || sel >= NPCS.length) return openNpcs(admin);
  if (all().length >= HARD_MAX) return admin.sendMessage("§c[NPCs] Đã đạt giới hạn " + HARD_MAX + " NPC.");
  const def = NPCS[sel];
  // optional custom name
  let name = "";
  try {
    const form = await new ModalFormData().title("Tên hiển thị (tuỳ chọn)")
      .textField("Để trống = dùng tên mặc định của NPC", "vd: Y tá Hoa").show(admin);
    if (!form.canceled && form.formValues) name = String(form.formValues[0]).trim().slice(0, 30);
  } catch {}
  const n = {
    nid: "n" + Date.now() + Math.floor(Math.random() * 999),
    key: def.key, type: def.type, job: def.job ?? null, name: name || null,
    dim: dimName,
    x: Math.floor(admin.location.x) + 0.5,
    y: Math.round(admin.location.y),
    z: Math.floor(admin.location.z) + 0.5,
  };
  const a = all(); a.push(n); save(a);
  const e = materialize(n);
  if (e) { admin.sendMessage("§a[NPCs] §l" + labelOf(n) + "§r§a đã đặt & neo. Nó sẽ đứng yên và tự trở lại nếu mất."); try { admin.playSound("random.orb"); } catch {} }
  else admin.sendMessage("§e[NPCs] Đã neo " + labelOf(n) + " nhưng chưa spawn được ngay. Đi vài bước là nó tự hiện.");
  return placeNpc(admin);
}

async function manageNpcs(admin) {
  const reg = all();
  if (reg.length === 0) { admin.sendMessage("§e[NPCs] Chưa có NPC nào."); return openNpcs(admin); }
  const sel = await actionMenu(admin, "NPC đã neo", "Chọn một NPC:",
    reg.map((n) => ({
      label: labelOf(n) + "\n§8" + (n.dim || "overworld") + " " + Math.round(n.x) + ", " + Math.round(n.y) + ", " + Math.round(n.z),
      icon: BYKEY[n.key]?.icon ?? "textures/items/villager_spawn_egg",
    })), "pokedex_black");
  if (sel < 0 || sel >= reg.length) return openNpcs(admin);
  const n = reg[sel];
  const act = await actionMenu(admin, labelOf(n), "Bạn muốn làm gì?", [
    { label: "§bTới chỗ NPC", icon: "textures/items/ender_pearl" },
    { label: "§eĐổi tên hiển thị", icon: "textures/items/name_tag" },
    { label: "§cXoá NPC này\n§8Xoá NPC + ngừng respawn", icon: "textures/ui/trash_light" },
  ], "pokedex_black");
  if (act === 0) {
    try { admin.teleport({ x: n.x, y: n.y + 1, z: n.z }, { dimension: dimOf(n) }); } catch { admin.sendMessage("§c[NPCs] Tới chỗ thất bại."); }
    return manageNpcs(admin);
  }
  if (act === 1) {
    let name = "";
    try {
      const form = await new ModalFormData().title("Đổi tên NPC")
        .textField("Tên mới (để trống = tên mặc định)", labelOf(n)).show(admin);
      if (form.canceled || !form.formValues) return manageNpcs(admin);
      name = String(form.formValues[0]).trim().slice(0, 30);
    } catch { return manageNpcs(admin); }
    const a = all(); const t = a.find((x) => x.nid === n.nid);
    if (t) { t.name = name || null; save(a); }
    const e = findEnt(n); try { if (e) e.nameTag = name || ""; } catch {}
    admin.sendMessage("§a[NPCs] Đã đổi tên.");
    return manageNpcs(admin);
  }
  if (act === 2) {
    if (!(await confirmForm(admin, "Xoá NPC " + labelOf(n) + "?\n\nNPC bị xoá và không respawn nữa."))) return manageNpcs(admin);
    const e = findEnt(n); try { if (e) e.remove(); } catch {}
    lastSpawn.delete(n.nid);
    save(all().filter((x) => x.nid !== n.nid));
    admin.sendMessage("§e[NPCs] Đã xoá NPC.");
    return manageNpcs(admin);
  }
  return manageNpcs(admin);
}
