// titles.js - player titles shown ON the name (above head + in chat, because
// SERP's own chat pipeline prints names from nameTag - we just set nameTag
// and both places update, no chat interception needed).
//
// Admins BUILD titles (Server admin -> Titles): text + decoration
// (solid color or multi-color GRADIENT, bold/italic/sparkle style, symbol,
// bracket style) + optional AUTO-GRANT rule (dex count / money / catches /
// shinies / streak / season board #1). Players equip from Hub -> Titles.
import { world, system } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm, dayNumber } from "./forms.js";
import { dexSet, setTitlePrefixFn } from "./tracker.js";
import { setTitlesModule } from "./sunnyscan.js";
import { getCoins, fmt } from "./economy.js";
import { dailyStatus } from "./daily.js";
import { maxJobLevel } from "./jobs.js";
// i18n imported as T so it doesn't clash with the `t` title-object variable used throughout
import { t as T } from "./i18n.js";

const TITLES = "sl:titles";       // world: [{id,text,color,grad,style,sym,brk,cond}]
const GRANTQ = "sl:titlegrants";  // world: { playerName: [titleId,...] } offline queue
const MINE = "sl:mytitles";       // player dp: [titleId,...]
const EQUIP = "sl:title";         // player dp: titleId

function jget(k, fb) { try { return JSON.parse(world.getDynamicProperty(k) ?? fb); } catch { return JSON.parse(fb); } }
function jset(k, v) { try { world.setDynamicProperty(k, JSON.stringify(v)); } catch {} }
export function allTitles() { return jget(TITLES, "[]"); }

// ---------- decoration ----------
const COLORS = [["White","f"],["Yellow","e"],["Gold","6"],["Red","c"],["Dark Red","4"],["Pink","d"],["Purple","u"],["Blue","9"],["Aqua","b"],["Cyan","3"],["Green","a"],["Dark Green","2"],["Gray","7"],["Black-ish","8"],["Minecoin Gold","g"]];
const GRADS = [["(no gradient)", null],["Fire (red-gold-yellow)", ["4","c","6","e"]],["Ocean (blue-aqua)", ["1","9","b","3"]],["Nature (green-lime)", ["2","a","e"]],["Royal (purple-pink)", ["5","u","d"]],["Sunset (pink-gold)", ["d","c","6","e"]],["Ice (aqua-white)", ["3","b","f"]]];
const STYLES = [["Normal",""],["Bold","§l"],["Italic","§o"],["Bold Italic","§l§o"]];
const SYMS = [
  ["(none)",""],
  // text symbols
  ["\u2605 star","\u2605"],["\u2606 hollow star","\u2606"],["\u2666 diamond","\u2666"],["\u2665 heart","\u2665"],["\u25cf dot","\u25cf"],["\u25b2 triangle","\u25b2"],
  // SERP's own glyphs (render everywhere the SERP RP is loaded)
  ["\uE132 SERP shiny","\uE132"],["\uE127 Pokeball","\uE127"],["\uE122 Masterball","\uE122"],["\uE130 male sign","\uE130"],["\uE131 female sign","\uE131"],
  // SerpLumen icon pack (our own glyph page)
  ["\uE200 Crown","\uE200"],["\uE201 Trophy","\uE201"],["\uE202 Sword","\uE202"],["\uE203 Shield","\uE203"],["\uE204 Lightning","\uE204"],["\uE205 Flame","\uE205"],["\uE206 Water drop","\uE206"],["\uE207 Leaf","\uE207"],["\uE208 Skull","\uE208"],["\uE209 Gem","\uE209"],["\uE20A Wing","\uE20A"],["\uE20B Medal","\uE20B"],
  ["\u00a7k sparkle","SPARK"],
];
const BRKS = [["[ ]","[","]"],["« »","«","»"],["( )","(",")"],["- -","-","-"],["(none)","",""]];

const CONDS = [
  ["Manual only (admin gives it)", null],
  ["Pokedex count >=", "dex"],
  ["Money >=", "money"],
  ["Total caught >=", "catch"],
  ["Shinies caught >=", "shiny"],
  ["Check-in streak >=", "streak"],
  ["Any job level >=", "job"],
  ["Season board #1 (weekly winner)", "top1"],
];

export function renderTitle(t) {
  const style = STYLES[t.style]?.[1] ?? "";
  const [, o, c] = BRKS[t.brk] ?? BRKS[0];
  let sym = SYMS[t.sym]?.[1] ?? "";
  let symL = sym, symR = sym;
  if (sym === "SPARK") { symL = "§k▌§r"; symR = "§k▌§r"; }
  const grad = GRADS[t.grad]?.[1];
  let core;
  if (grad) {
    const chars = [...t.text];
    core = chars.map((ch, i) => "§" + grad[Math.floor((i / Math.max(1, chars.length - 1)) * (grad.length - 1))] + style + ch).join("");
  } else {
    core = "§" + (t.color ?? "e") + style + t.text;
  }
  const col = grad ? "§" + grad[0] : "§" + (t.color ?? "e");
  return col + o + "§r" + (symL ? col + symL + "§r" : "") + core + "§r" + (symR ? col + symR + "§r" : "") + col + c + "§r";
}

// human-readable condition label in the viewer's language
function condLabel(viewer, cond) {
  if (!cond) return T(viewer, "title.cond.manual");
  if (cond.type === "top1") return T(viewer, "title.cond.top1");
  return T(viewer, "title.cond." + cond.type, { n: cond.n });
}

// ---------- ownership / equip ----------
function myTitles(p) { try { return JSON.parse(p.getDynamicProperty(MINE) ?? "[]"); } catch { return []; } }
function setMyTitles(p, a) { try { p.setDynamicProperty(MINE, JSON.stringify(a)); } catch {} }
function equipped(p) { try { return p.getDynamicProperty(EQUIP) ?? null; } catch { return null; } }
function setEquipped(p, id) { try { p.setDynamicProperty(EQUIP, id ?? undefined); } catch {} }

function grantTo(p, t, silent) {
  const mine = myTitles(p);
  if (mine.includes(t.id)) return false;
  mine.push(t.id);
  setMyTitles(p, mine);
  if (!silent) {
    for (const pl of world.getAllPlayers()) pl.sendMessage(T(pl, "title.earned", { name: p.name, title: renderTitle(t) }));
    try { p.playSound("random.levelup"); } catch {}
    p.sendMessage(T(p, "title.equip.hint"));
  }
  return true;
}

// grant a title to an online player object (used by gift codes etc.)
export function grantById(player, titleId, silent) {
  const t = allTitles().find((x) => x.id === titleId);
  if (!t) return false;
  return grantTo(player, t, silent);
}

export function condProgress(p, cond) {
  if (!cond) return null;
  if (cond.type === "top1") return T(p, "title.cond.top1.progress");
  const n = cond.n ?? 0;
  let cur = 0;
  if (cond.type === "dex") cur = dexSet(p).size;
  else if (cond.type === "money") cur = getCoins(p);
  else if (cond.type === "streak") cur = dailyStatus(p).streak;
  else if (cond.type === "job") cur = maxJobLevel(p);
  else {
    let st = {};
    try { st = JSON.parse(p.getDynamicProperty("sx:stats") ?? "{}"); } catch {}
    cur = cond.type === "catch" ? (st.catch ?? 0) : cond.type === "shiny" ? (st.shiny ?? 0) : 0;
  }
  return Math.min(cur, n) + " / " + n;
}

export function grantByName(name, titleId) {
  for (const p of world.getAllPlayers()) {
    if (p.name === name) {
      const t = allTitles().find((x) => x.id === titleId);
      if (t) grantTo(p, t);
      return;
    }
  }
  const q = jget(GRANTQ, "{}");
  (q[name] = q[name] ?? []).includes(titleId) || q[name].push(titleId);
  jset(GRANTQ, q);
}

// titles with the "top1" condition - season.js grants these to weekly #1s
export function top1Titles() { return allTitles().filter((t) => t.cond && t.cond.type === "top1"); }

// rendered title prefix for use in broadcasts/messages ("«X» " or "")
export function titlePrefix(p) {
  try {
    const id = equipped(p);
    if (!id) return "";
    const tt = allTitles().find((x) => x.id === id);
    return tt ? renderTitle(tt) + " " : "";
  } catch { return ""; }
}

// ---------- nameTag apply ----------
function applyName(p) {
  const id = equipped(p);
  const t = id ? allTitles().find((x) => x.id === id) : null;
  const want = t ? renderTitle(t) + " §f" + p.name : p.name;
  try { if (p.nameTag !== want) p.nameTag = want; } catch {}
}

// ---------- auto-grant ----------
function meets(p, cond) {
  if (!cond || cond.type === "top1") return false;
  const n = cond.n ?? 0;
  if (cond.type === "dex") return dexSet(p).size >= n;
  if (cond.type === "money") return getCoins(p) >= n;
  if (cond.type === "streak") return dailyStatus(p).streak >= n;
  if (cond.type === "job") return maxJobLevel(p) >= n;
  let st = {};
  try { st = JSON.parse(p.getDynamicProperty("sx:stats") ?? "{}"); } catch {}
  if (cond.type === "catch") return (st.catch ?? 0) >= n;
  if (cond.type === "shiny") return (st.shiny ?? 0) >= n;
  return false;
}

// ---------- player UI ----------
export async function openTitles(player) {
  const all = allTitles();
  const mineIds = myTitles(player);
  const cur = equipped(player);
  const owned = all.filter((t) => mineIds.includes(t.id));
  const locked = all.filter((t) => !mineIds.includes(t.id));
  const buttons = [];
  for (const t of owned) buttons.push({ label: (t.id === cur ? T(player, "title.on") : "") + renderTitle(t), icon: "textures/items/name_tag" });
  for (const t of locked) buttons.push({ label: T(player, "title.locked") + renderTitle(t), icon: "textures/ui/buttons/bubble_no" });
  buttons.push({ label: T(player, "title.none.btn"), icon: "textures/ui/buttons/bubble_no" });
  const curT = cur ? all.find((x) => x.id === cur) : null;
  if (curT) buttons.push({ label: T(player, "title.flex.btn"), icon: "textures/items/firework_rocket" });
  const sel = await actionMenu(player, T(player, "title.title"),
    T(player, "title.body", { owned: owned.length, all: all.length, none: cur ? "" : T(player, "title.none.suffix") }),
    buttons, "pokedex_purple");
  if (sel < 0) return;
  if (sel === owned.length + locked.length) {
    setEquipped(player, null);
    applyName(player);
    player.sendMessage(T(player, "title.removed.plain"));
    return;
  }
  if (curT && sel === owned.length + locked.length + 1) {
    const last = Number(player.getDynamicProperty("sl:flex") ?? 0);
    if (Date.now() - last < 5 * 60000) {
      return player.sendMessage(T(player, "title.flex.cd", { m: Math.ceil((5 * 60000 - (Date.now() - last)) / 60000) }));
    }
    player.setDynamicProperty("sl:flex", Date.now());
    for (const p of world.getAllPlayers()) {
      p.sendMessage(T(p, "title.flex.broadcast", { title: renderTitle(curT), name: player.name }));
      try { p.playSound("random.levelup"); } catch {}
    }
    return;
  }
  const t = sel < owned.length ? owned[sel] : locked[sel - owned.length];
  return openTitleInfo(player, t, mineIds.includes(t.id));
}

async function openTitleInfo(player, t, own) {
  const cur = equipped(player);
  const isOn = cur === t.id;
  const prog = condProgress(player, t.cond);
  const status = own ? (isOn ? T(player, "title.status.on") : T(player, "title.status.owned")) : T(player, "title.status.locked");
  const body =
    T(player, "title.info.preview", { title: renderTitle(t), name: player.name }) + "\n\n" +
    T(player, "title.info.howto", { cond: condLabel(player, t.cond) }) + "\n" +
    (t.cond && t.cond.type !== "top1" && !own ? T(player, "title.info.progress", { prog }) + "\n" : "") +
    T(player, "title.info.status", { status });
  const buttons = [];
  if (own) buttons.push(isOn
    ? { label: T(player, "title.takeoff"), icon: "textures/ui/buttons/bubble_no" }
    : { label: T(player, "title.wear"), icon: "textures/items/name_tag" });
  buttons.push({ label: T(player, "common.back"), icon: "textures/ui/buttons/bubble_no" });
  const sel = await actionMenu(player, T(player, "title.info.title"), body, buttons, "pokedex_purple");
  if (sel < 0) return;
  if (own && sel === 0) {
    setEquipped(player, isOn ? null : t.id);
    applyName(player);
    player.sendMessage(isOn ? T(player, "title.removed") : T(player, "title.nowwearing", { title: renderTitle(t) }));
    return;
  }
  return openTitles(player);
}

// ---------- admin UI ----------
async function createTitle(admin) {
  const mf = new ModalFormData().title(T(admin, "title.create.title"));
  mf.textField(T(admin, "title.create.text.label"), T(admin, "title.create.text.ph"));
  mf.dropdown(T(admin, "title.create.color"), COLORS.map(([n]) => n), { defaultValueIndex: 1 });
  mf.dropdown(T(admin, "title.create.grad"), GRADS.map(([n]) => n), { defaultValueIndex: 0 });
  mf.dropdown(T(admin, "title.create.style"), STYLES.map(([n]) => n), { defaultValueIndex: 1 });
  mf.dropdown(T(admin, "title.create.sym"), SYMS.map(([n]) => n), { defaultValueIndex: 1 });
  mf.dropdown(T(admin, "title.create.brk"), BRKS.map(([n]) => n), { defaultValueIndex: 0 });
  mf.dropdown(T(admin, "title.create.cond"), CONDS.map(([n]) => n), { defaultValueIndex: 0 });
  mf.textField(T(admin, "title.create.n.label"), T(admin, "title.create.n.ph"), { defaultValue: "0" });
  let res;
  try { res = await mf.show(admin); } catch { return; }
  if (res.canceled) return;
  const [text, color, grad, style, sym, brk, condI, nRaw] = res.formValues;
  if (!String(text).trim()) return admin.sendMessage(T(admin, "title.create.empty"));
  const all = allTitles();
  if (all.length >= 30) return admin.sendMessage(T(admin, "title.create.max"));
  const condType = CONDS[Number(condI)][1];
  const t = {
    id: "t" + Date.now(),
    text: String(text).trim().slice(0, 20),
    color: COLORS[Number(color)][1],
    grad: Number(grad), style: Number(style), sym: Number(sym), brk: Number(brk),
    cond: condType ? { type: condType, n: Math.max(0, Math.floor(Number(nRaw) || 0)) } : null,
  };
  all.push(t);
  jset(TITLES, all);
  admin.sendMessage(T(admin, "title.created", { title: renderTitle(t), cond: condLabel(admin, t.cond) }));
  for (const pl of world.getAllPlayers()) pl.sendMessage(T(pl, "title.new", { title: renderTitle(t), cond: condLabel(pl, t.cond) }));
}

async function manageTitle(admin, t) {
  const sel = await actionMenu(admin, T(admin, "title.manage.title"), T(admin, "title.manage.body", { title: renderTitle(t), cond: condLabel(admin, t.cond) }), [
    { label: T(admin, "title.give"), icon: "textures/items/name_tag" },
    { label: T(admin, "title.delete"), icon: "textures/ui/buttons/bubble_no" },
  ], "pokedex_purple");
  if (sel === 0) {
    const others = world.getAllPlayers();
    const ps = await actionMenu(admin, T(admin, "title.give.who"), "", others.map((p) => ({ label: p.name })), "pokedex_purple");
    if (ps < 0) return;
    grantTo(others[ps], t);
  } else if (sel === 1) {
    if (!(await confirmForm(admin, T(admin, "title.delete.confirm", { text: t.text })))) return;
    jset(TITLES, allTitles().filter((x) => x.id !== t.id));
    admin.sendMessage(T(admin, "title.deleted"));
  }
}

export async function openTitlesAdmin(admin) {
  const all = allTitles();
  const buttons = [{ label: T(admin, "title.createnew"), icon: "textures/items/experience_bottle" }];
  for (const t of all) buttons.push({ label: renderTitle(t) + "\n§8" + condLabel(admin, t.cond), icon: "textures/items/name_tag" });
  const sel = await actionMenu(admin, T(admin, "title.admin.title", { n: all.length }), T(admin, "title.admin.body"), buttons, "pokedex_purple");
  if (sel < 0) return;
  if (sel === 0) return createTitle(admin);
  return manageTitle(admin, all[sel - 1]);
}

// ---------- init ----------
export function initTitles() {
  setTitlePrefixFn(titlePrefix);
  setTitlesModule({ titlePrefix });
  // titled join line - the one chat surface we CAN decorate
  world.afterEvents.playerSpawn.subscribe((ev) => {
    try {
      if (!ev.initialSpawn) return;
      const p = ev.player;
      const pre = titlePrefix(p);
      if (pre) {
        for (const pl of world.getAllPlayers()) pl.sendMessage(pre + T(pl, "title.joined", { name: p.name }));
      }
    } catch {}
  });
  // apply nameTags + deliver queued grants + auto-grant checks
  system.runInterval(() => {
    try {
      const all = allTitles();
      const auto = all.filter((t) => t.cond && t.cond.type !== "top1");
      const q = jget(GRANTQ, "{}");
      let qChanged = false;
      for (const p of world.getAllPlayers()) {
        applyName(p);
        // queued grants (e.g. season #1 while offline)
        const ids = q[p.name];
        if (ids && ids.length) {
          for (const id of ids) { const t = all.find((x) => x.id === id); if (t) grantTo(p, t); }
          delete q[p.name];
          qChanged = true;
        }
        // auto-grant rules
        const mine = myTitles(p);
        for (const t of auto) if (!mine.includes(t.id) && meets(p, t.cond)) grantTo(p, t);
      }
      if (qChanged) jset(GRANTQ, q);
    } catch {}
  }, 200);
}
