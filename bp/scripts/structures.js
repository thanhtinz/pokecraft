// structures.js - admin building tool (Structure Block-style), Script API only.
//   - Mark two corners (pos1 / pos2) like a structure block selection.
//   - Save the selection as a named structure (persists across restarts).
//   - Place a saved structure back into the world anywhere.
//   - Clear a selection (fill with air) to delete a built construction, or
//     fill it solid.
// Uses world.structureManager (stable) + Dimension.fillBlocks (stable). No
// runCommand, no Beta API. Saved-structure names live in a world dynamic
// property so we can list them in the menu.
import { world, BlockVolume, StructureSaveMode } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm } from "./forms.js";
import { BUNDLED } from "./bundled_structures.js"; // .mcstructure files shipped in the pack

const REG = "sl:structreg";          // ["sl:house", ...] saved ids
const AXIS_CAP = 64;                 // max blocks per axis (structure block limit)
const FILL_CHUNK = 24000;            // keep each fillBlocks call under the engine cap

function reg() { try { return JSON.parse(world.getDynamicProperty(REG) ?? "[]"); } catch { return []; } }
function saveReg(a) { try { world.setDynamicProperty(REG, JSON.stringify([...new Set(a)])); } catch {} }

function getSel(admin) { try { return JSON.parse(admin.getDynamicProperty("sl:struct_sel") ?? "null"); } catch { return null; } }
function setSel(admin, s) { try { admin.setDynamicProperty("sl:struct_sel", JSON.stringify(s)); } catch {} }

function floorLoc(l) { return { x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z) }; }
function minmax(sel) {
  const min = { x: Math.min(sel.a.x, sel.b.x), y: Math.min(sel.a.y, sel.b.y), z: Math.min(sel.a.z, sel.b.z) };
  const max = { x: Math.max(sel.a.x, sel.b.x), y: Math.max(sel.a.y, sel.b.y), z: Math.max(sel.a.z, sel.b.z) };
  return { min, max };
}
function dims(sel) {
  const { min, max } = minmax(sel);
  return { dx: max.x - min.x + 1, dy: max.y - min.y + 1, dz: max.z - min.z + 1, min, max };
}

// slug: valid structure identifier (namespace:name), lowercase safe charset
function slug(name) {
  const s = String(name).toLowerCase().replace(/[^a-z0-9_]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 30);
  return s ? "sl:" + s : null;
}
function pretty(id) { return id.replace(/^sl:/, ""); }

// fill a box in engine-safe chunks (fillBlocks caps how many blocks per call)
function fillBox(dim, min, max, block) {
  const dx = max.x - min.x + 1, dz = max.z - min.z + 1;
  const per = Math.max(1, Math.floor(FILL_CHUNK / (dx * dz))); // Y-slabs that stay under the cap
  let done = 0;
  for (let y = min.y; y <= max.y; y += per) {
    const y2 = Math.min(max.y, y + per - 1);
    try {
      const vol = new BlockVolume({ x: min.x, y, z: min.z }, { x: max.x, y: y2, z: max.z });
      dim.fillBlocks(vol, block);
      done += dx * dz * (y2 - y + 1);
    } catch { /* unloaded chunk / out of bounds - skip that slab */ }
  }
  return done;
}

function selText(admin) {
  const sel = getSel(admin);
  if (!sel) return "§8Chưa chọn vùng. Đặt Điểm 1 và Điểm 2 để bắt đầu.";
  const p1 = sel.a ? `(${sel.a.x}, ${sel.a.y}, ${sel.a.z})` : "§8-";
  const p2 = sel.b ? `(${sel.b.x}, ${sel.b.y}, ${sel.b.z})` : "§8-";
  let size = "";
  if (sel.a && sel.b) {
    const { dx, dy, dz } = dims(sel);
    const over = (dx > AXIS_CAP || dy > AXIS_CAP || dz > AXIS_CAP);
    size = `\n§7Kích thước: §f${dx}×${dy}×${dz}§r` + (over ? ` §c(quá ${AXIS_CAP}/trục!)` : ` §8(${dx * dy * dz} khối)`);
  }
  return `§7Điểm 1: §f${p1}\n§7Điểm 2: §f${p2}${size}`;
}

// ---------- save selection ----------
async function saveSelection(admin) {
  const sel = getSel(admin);
  if (!sel || !sel.a || !sel.b) return admin.sendMessage("§c[Structures] Hãy đặt cả Điểm 1 và Điểm 2 trước.");
  const { dx, dy, dz, min, max } = dims(sel);
  if (dx > AXIS_CAP || dy > AXIS_CAP || dz > AXIS_CAP)
    return admin.sendMessage(`§c[Structures] Vùng quá lớn (tối đa ${AXIS_CAP} khối/trục). Hiện ${dx}×${dy}×${dz}.`);
  const form = await new ModalFormData().title("Lưu công trình")
    .textField("Tên công trình", "vd: nha_go").show(admin);
  if (form.canceled || !form.formValues) return;
  const id = slug(form.formValues[0]);
  if (!id) return admin.sendMessage("§c[Structures] Tên không hợp lệ.");
  const list = reg();
  if (list.includes(id) && !(await confirmForm(admin, `Đã có công trình §e${pretty(id)}§r. Ghi đè?`))) return;
  try {
    try { world.structureManager.delete(id); } catch {}
    world.structureManager.createFromWorld(id, admin.dimension, min, max, {
      saveMode: StructureSaveMode.World, includeEntities: false, includeBlocks: true,
    });
    saveReg([...list, id]);
    admin.sendMessage(`§a[Structures] Đã lưu §l${pretty(id)}§r§a (${dx}×${dy}×${dz}).`);
  } catch (e) {
    admin.sendMessage("§c[Structures] Lưu thất bại: §7" + e);
  }
}

// ---------- place a structure (pack-bundled .mcstructure + admin-saved) ----------
async function placeStructure(admin) {
  const saved = reg().filter((id) => { try { return !!world.structureManager.get(id); } catch { return false; } });
  saveReg(saved); // prune saved ones that no longer exist
  // pack .mcstructure files are loaded lazily - list them as-is, don't probe
  const items = [
    ...BUNDLED.map((b) => ({ id: b.id, label: "§d" + b.name + "\n§8[pack] " + b.id })),
    ...saved.map((id) => ({ id, label: pretty(id) + "\n§8[đã lưu]" })),
  ];
  if (items.length === 0) return admin.sendMessage("§e[Structures] Chưa có công trình nào (chưa có file .mcstructure trong pack, chưa lưu vùng nào).");
  const sel = await actionMenu(admin, "Đặt công trình", "§7Đặt từ vị trí bạn đứng (góc thấp nhất).\nChọn công trình:",
    items.map((it) => ({ label: it.label, icon: "textures/blocks/grass_side_carried" })), "pokedex_black");
  if (sel < 0) return;
  const id = items[sel].id;
  const at = floorLoc(admin.location);
  try {
    world.structureManager.place(id, admin.dimension, at, { includeEntities: false, includeBlocks: true });
    admin.sendMessage(`§a[Structures] Đã đặt §l${pretty(id)}§r§a tại (${at.x}, ${at.y}, ${at.z}).`);
    try { admin.playSound("random.levelup"); } catch {}
  } catch (e) {
    admin.sendMessage("§c[Structures] Đặt thất bại (vùng chưa tải?): §7" + e);
  }
}

// ---------- delete a saved structure ----------
async function deleteSaved(admin) {
  const list = reg();
  if (list.length === 0) return admin.sendMessage("§e[Structures] Chưa có công trình nào được lưu.");
  const sel = await actionMenu(admin, "Xoá công trình đã lưu", "§7Chỉ xoá bản lưu, không đụng tới thế giới.\nChọn để xoá:",
    list.map((id) => ({ label: "§c" + pretty(id), icon: "textures/ui/trash" })), "pokedex_black");
  if (sel < 0) return;
  const id = list[sel];
  if (!(await confirmForm(admin, `Xoá bản lưu §e${pretty(id)}§r?`))) return;
  try { world.structureManager.delete(id); } catch {}
  saveReg(list.filter((x) => x !== id));
  admin.sendMessage(`§e[Structures] Đã xoá bản lưu ${pretty(id)}.`);
}

// ---------- clear / fill the selection (delete a built construction) ----------
async function clearSelection(admin, block, verb) {
  const sel = getSel(admin);
  if (!sel || !sel.a || !sel.b) return admin.sendMessage("§c[Structures] Hãy chọn vùng (Điểm 1 + Điểm 2) trước.");
  const { dx, dy, dz, min, max } = dims(sel);
  if (dx > AXIS_CAP || dy > AXIS_CAP || dz > AXIS_CAP)
    return admin.sendMessage(`§c[Structures] Vùng quá lớn (tối đa ${AXIS_CAP}/trục).`);
  if (!(await confirmForm(admin, `${verb} vùng §f${dx}×${dy}×${dz}§r (${dx * dy * dz} khối)?\n§cKhông thể hoàn tác.`))) return;
  const n = fillBox(admin.dimension, min, max, block);
  admin.sendMessage(`§a[Structures] Đã ${verb.toLowerCase()} §f${n}§a khối.`);
}

// ---------- main menu ----------
export async function openStructures(admin) {
  while (true) {
    const sel = await actionMenu(admin, "Công trình / Structures", selText(admin), [
      { label: "§aĐặt Điểm 1 tại đây\n§8Góc thứ nhất của vùng", icon: "textures/blocks/wool_colored_lime" },
      { label: "§bĐặt Điểm 2 tại đây\n§8Góc thứ hai của vùng", icon: "textures/blocks/wool_colored_light_blue" },
      { label: "§eLưu vùng thành công trình\n§8Ghi lại các khối trong vùng", icon: "textures/items/structure_void" },
      { label: "§dĐặt công trình có sẵn\n§8Dán một công trình đã lưu vào đây", icon: "textures/blocks/grass_side_carried" },
      { label: "§cXoá công trình (làm trống vùng)\n§8Điền không khí vào vùng chọn", icon: "textures/ui/trash" },
      { label: "§6Điền đá vào vùng\n§8Lấp phẳng nền", icon: "textures/blocks/stone" },
      { label: "§7Xoá công trình đã lưu\n§8Xoá bản lưu (không đụng thế giới)", icon: "textures/ui/trash_light" },
    ], "pokedex_black");
    if (sel < 0) return;
    if (sel === 0) { const s = getSel(admin) ?? {}; s.a = floorLoc(admin.location); s.dim = admin.dimension.id; setSel(admin, s); admin.sendMessage(`§a[Structures] Điểm 1 = (${s.a.x}, ${s.a.y}, ${s.a.z}).`); }
    else if (sel === 1) { const s = getSel(admin) ?? {}; s.b = floorLoc(admin.location); s.dim = admin.dimension.id; setSel(admin, s); admin.sendMessage(`§b[Structures] Điểm 2 = (${s.b.x}, ${s.b.y}, ${s.b.z}).`); }
    else if (sel === 2) await saveSelection(admin);
    else if (sel === 3) await placeStructure(admin);
    else if (sel === 4) await clearSelection(admin, "minecraft:air", "Làm trống");
    else if (sel === 5) await clearSelection(admin, "minecraft:stone", "Điền đá");
    else if (sel === 6) await deleteSaved(admin);
  }
}
