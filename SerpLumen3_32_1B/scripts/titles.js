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
const STYLES = [["Normal",""],["Bold","\u00a7l"],["Italic","\u00a7o"],["Bold Italic","\u00a7l\u00a7o"]];
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
const BRKS = [["[ ]","[","]"],["\u00ab \u00bb","\u00ab","\u00bb"],["( )","(",")"],["- -","-","-"],["(none)","",""]];

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
  if (sym === "SPARK") { symL = "\u00a7k\u258c\u00a7r"; symR = "\u00a7k\u258c\u00a7r"; }
  const grad = GRADS[t.grad]?.[1];
  let core;
  if (grad) {
    const chars = [...t.text];
    core = chars.map((ch, i) => "\u00a7" + grad[Math.floor((i / Math.max(1, chars.length - 1)) * (grad.length - 1))] + style + ch).join("");
  } else {
    core = "\u00a7" + (t.color ?? "e") + style + t.text;
  }
  const col = grad ? "\u00a7" + grad[0] : "\u00a7" + (t.color ?? "e");
  return col + o + "\u00a7r" + (symL ? col + symL + "\u00a7r" : "") + core + "\u00a7r" + (symR ? col + symR + "\u00a7r" : "") + col + c + "\u00a7r";
}

function condLabel(cond) {
  if (!cond) return "manual";
  const c = CONDS.find(([, k]) => k === cond.type);
  return cond.type === "top1" ? "season #1" : (c?.[0] ?? cond.type) + " " + cond.n;
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
    world.sendMessage("\u00a7d[TITLE] \u00a7l" + p.name + "\u00a7r\u00a7d earned the title " + renderTitle(t) + "\u00a7d!");
    try { p.playSound("random.levelup"); } catch {}
    p.sendMessage("\u00a7d[TITLE] \u00a7fEquip it in Hub -> Titles!");
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
  if (cond.type === "top1") return "Win any weekly season board";
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
  const want = t ? renderTitle(t) + " \u00a7f" + p.name : p.name;
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
  for (const t of owned) buttons.push({ label: (t.id === cur ? "\u00a7a[ON] \u00a7r" : "") + renderTitle(t), icon: "textures/items/name_tag" });
  for (const t of locked) buttons.push({ label: "\u00a78[LOCKED] \u00a7r" + renderTitle(t), icon: "textures/ui/buttons/bubble_no" });
  buttons.push({ label: "\u00a77Wear no title (plain name)", icon: "textures/ui/buttons/bubble_no" });
  const curT = cur ? all.find((x) => x.id === cur) : null;
  if (curT) buttons.push({ label: "\u00a7dShow off in chat!\n\u00a78Broadcast your title (5 min cooldown)", icon: "textures/items/firework_rocket" });
  const sel = await actionMenu(player, "Titles",
    "Owned: \u00a7a" + owned.length + "\u00a7r / " + all.length + (cur ? "" : "  \u00a78(none equipped)") + "\nTap a title for details.",
    buttons, "pokedex_purple");
  if (sel < 0) return;
  if (sel === owned.length + locked.length) {
    setEquipped(player, null);
    applyName(player);
    player.sendMessage("\u00a7e[SunHub] Title removed - plain name.");
    return;
  }
  if (curT && sel === owned.length + locked.length + 1) {
    const last = Number(player.getDynamicProperty("sl:flex") ?? 0);
    if (Date.now() - last < 5 * 60000) {
      return player.sendMessage("\u00a7e[SunHub] Flex cooldown: " + Math.ceil((5 * 60000 - (Date.now() - last)) / 60000) + " min left.");
    }
    player.setDynamicProperty("sl:flex", Date.now());
    world.sendMessage(renderTitle(curT) + " \u00a7f" + player.name + " \u00a7dis showing off their title! \u00a7k\u258c");
    for (const p of world.getAllPlayers()) { try { p.playSound("random.levelup"); } catch {} }
    return;
  }
  const t = sel < owned.length ? owned[sel] : locked[sel - owned.length];
  return openTitleInfo(player, t, mineIds.includes(t.id));
}

async function openTitleInfo(player, t, own) {
  const cur = equipped(player);
  const isOn = cur === t.id;
  const prog = condProgress(player, t.cond);
  const body =
    "Preview: " + renderTitle(t) + " \u00a7f" + player.name + "\u00a7r\n\n" +
    "How to get: \u00a7e" + condLabel(t.cond) + "\u00a7r\n" +
    (t.cond && t.cond.type !== "top1" && !own ? "Your progress: \u00a7b" + prog + "\u00a7r\n" : "") +
    "Status: " + (own ? (isOn ? "\u00a7aOWNED - currently ON" : "\u00a7aOWNED") : "\u00a78LOCKED") + "\u00a7r";
  const buttons = [];
  if (own) buttons.push(isOn
    ? { label: "\u00a7cTake it off", icon: "textures/ui/buttons/bubble_no" }
    : { label: "\u00a7aWear this title", icon: "textures/items/name_tag" });
  buttons.push({ label: "Back", icon: "textures/ui/buttons/bubble_no" });
  const sel = await actionMenu(player, "Title info", body, buttons, "pokedex_purple");
  if (sel < 0) return;
  if (own && sel === 0) {
    setEquipped(player, isOn ? null : t.id);
    applyName(player);
    player.sendMessage(isOn ? "\u00a7e[SunHub] Title removed." : "\u00a7a[SunHub] Now wearing: " + renderTitle(t));
    return;
  }
  return openTitles(player);
}

// ---------- admin UI ----------
async function createTitle(admin) {
  const mf = new ModalFormData().title("Create Title");
  mf.textField("Title text:", "e.g. Champion");
  mf.dropdown("Color (ignored if gradient):", COLORS.map(([n]) => n), { defaultValueIndex: 1 });
  mf.dropdown("Gradient:", GRADS.map(([n]) => n), { defaultValueIndex: 0 });
  mf.dropdown("Style:", STYLES.map(([n]) => n), { defaultValueIndex: 1 });
  mf.dropdown("Symbol:", SYMS.map(([n]) => n), { defaultValueIndex: 1 });
  mf.dropdown("Brackets:", BRKS.map(([n]) => n), { defaultValueIndex: 0 });
  mf.dropdown("Auto-grant when:", CONDS.map(([n]) => n), { defaultValueIndex: 0 });
  mf.textField("Threshold N (for >= rules):", "e.g. 100", { defaultValue: "0" });
  let res;
  try { res = await mf.show(admin); } catch { return; }
  if (res.canceled) return;
  const [text, color, grad, style, sym, brk, condI, nRaw] = res.formValues;
  if (!String(text).trim()) return admin.sendMessage("\u00a7c[SunHub] Title text can't be empty.");
  const all = allTitles();
  if (all.length >= 30) return admin.sendMessage("\u00a7c[SunHub] Max 30 titles.");
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
  admin.sendMessage("\u00a7a[SunHub] Created: " + renderTitle(t) + " \u00a77(" + condLabel(t.cond) + ")");
  world.sendMessage("\u00a7d[TITLE] \u00a7fNew title available: " + renderTitle(t) + " \u00a77- " + condLabel(t.cond));
}

async function manageTitle(admin, t) {
  const sel = await actionMenu(admin, "Title", renderTitle(t) + "\n\u00a77Rule: " + condLabel(t.cond), [
    { label: "Give to a player", icon: "textures/items/name_tag" },
    { label: "\u00a7cDelete title", icon: "textures/ui/buttons/bubble_no" },
  ], "pokedex_purple");
  if (sel === 0) {
    const others = world.getAllPlayers();
    const ps = await actionMenu(admin, "Give to whom?", "", others.map((p) => ({ label: p.name })), "pokedex_purple");
    if (ps < 0) return;
    grantTo(others[ps], t);
  } else if (sel === 1) {
    if (!(await confirmForm(admin, "Delete title \"" + t.text + "\"? Players wearing it lose it."))) return;
    jset(TITLES, allTitles().filter((x) => x.id !== t.id));
    admin.sendMessage("\u00a7e[SunHub] Title deleted.");
  }
}

export async function openTitlesAdmin(admin) {
  const all = allTitles();
  const buttons = [{ label: "\u00a7aCreate new title", icon: "textures/items/experience_bottle" }];
  for (const t of all) buttons.push({ label: renderTitle(t) + "\n\u00a78" + condLabel(t.cond), icon: "textures/items/name_tag" });
  const sel = await actionMenu(admin, "Titles (" + all.length + "/30)", "Tap to manage:", buttons, "pokedex_purple");
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
      if (pre) world.sendMessage(pre + "\u00a7f" + p.name + " \u00a77joined the game");
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
