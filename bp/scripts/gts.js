// gts.js - Wonder Trade + GTS, attached to SERP's own Trade Machine entity.
//   Tap normally  -> SERP's machine works untouched
//   Sneak + tap   -> our menu (Wonder Trade / GTS)
// Storage lives in world dynamic properties so listings survive restarts and
// work while the other player is offline. No new currency, no new items.
import { world, system } from "@minecraft/server";
import { themedRaw, confirm } from "./ui.js";
import * as S from "./serpdata.js";
import { POKENAMES } from "./pokenames.js";
import { suppressCatch } from "./tracker.js";
import { ModalFormData } from "@minecraft/server-ui";
import { t } from "./i18n.js";

const MACHINE = "serp:trade_machine";
const WONDER_CAP = 20;
const GTS_CAP = 30;
const SH = "\uE132";

function jget(key, fb) { try { return JSON.parse(world.getDynamicProperty(key) ?? fb); } catch { return JSON.parse(fb); } }
function jset(key, v) { try { world.setDynamicProperty(key, JSON.stringify(v)); } catch {} }
const wonderPool = () => jget("sl:wonder", "[]");
const gtsList = () => jget("sl:gts", "[]");
const owed = () => jget("sl:owed", "{}");

function msg(p, text) { try { p.sendMessage("§e[Trade Machine]§r " + text); } catch {} }
function nameOf(dex) { return POKENAMES[String(dex)] ?? ("#" + dex); }

function pokeLabel(fields) {
  return nameOf(Number(fields[2])) + " Lv." + S.level(fields) + (S.isShiny(fields) ? " " + SH : "");
}

// deliver a pokemon: direct if online, else owed queue
function deliver(ownerId, ownerName, fields) {
  for (const p of world.getAllPlayers()) {
    if (p.id === ownerId) {
      suppressCatch(p.id);
      if (S.givePokemon(p, fields)) { msg(p, t(p, "gts.received", { label: pokeLabel(fields) })); try { p.playSound("random.levelup"); } catch {} return; }
    }
  }
  const o = owed();
  (o[ownerId] = o[ownerId] ?? []).push(fields);
  jset("sl:owed", o);
}

function pickFromParty(player, title, note, filter) {
  const party = S.getParty(player).filter((p) => !filter || filter(p.fields));
  if (party.length === 0) { msg(player, t(player, "gts.noeligible")); return Promise.resolve(null); }
  const form = themedRaw("serp.main.trade").body("§l" + title + "§r" + (note ? "\n" + note : ""));
  for (const p of party) form.button(pokeLabel(p.fields), "pokedrock/pokedex/" + p.fields[2]);
  form.button(t(player, "common.cancel"), "textures/ui/buttons/bubble_no");
  return form.show(player).then((res) =>
    res.canceled || res.selection >= party.length ? null : party[res.selection]
  ).catch(() => null);
}

// ---------- WONDER TRADE ----------
async function openWonder(player) {
  const pool = wonderPool();
  const mine = pool.filter((e) => e.id === player.id).length;
  const pick = await pickFromParty(player, t(player, "gts.wonder.title"),
    t(player, "gts.wonder.note", { n: pool.length, mine: mine ? t(player, "gts.wonder.yours", { m: mine }) : "" }));
  if (!pick) return;
  if (!(await confirm(player, "serp.main.trade", t(player, "gts.wonder.confirm", { label: pokeLabel(pick.fields) })))) return;
  const fields = S.takePokemon(player, pick.tag);
  if (!fields) return msg(player, t(player, "gts.moved"));
  suppressCatch(player.id);
  const p2 = wonderPool(); // re-read: someone may have deposited meanwhile
  const idx = p2.findIndex((e) => e.id !== player.id);
  if (idx >= 0) {
    const other = p2.splice(idx, 1)[0];
    jset("sl:wonder", p2);
    deliver(other.id, other.n, fields);
    if (S.givePokemon(player, other.f)) msg(player, t(player, "gts.wonder.matched", { name: other.n, label: pokeLabel(other.f) }));
    else { deliver(player.id, player.name, other.f); msg(player, t(player, "gts.wonder.matched.pc")); }
    try { player.playSound("random.levelup"); } catch {}
  } else {
    if (p2.length >= WONDER_CAP) { S.givePokemon(player, fields); return msg(player, t(player, "gts.wonder.poolfull")); }
    p2.push({ f: fields, id: player.id, n: player.name, at: Date.now() });
    jset("sl:wonder", p2);
    msg(player, t(player, "gts.wonder.deposited", { label: pokeLabel(fields) }));
  }
}

// ---------- GTS ----------
function wantLabel(viewer, w) {
  return nameOf(w.dex) + (w.lvl > 1 ? " Lv." + w.lvl + "+" : "") + (w.sh ? " " + SH + " " + t(viewer, "gts.shiny") : "");
}
function matches(fields, w) {
  return Number(fields[2]) === w.dex && S.level(fields) >= (w.lvl ?? 1) && (!w.sh || S.isShiny(fields));
}

async function gtsCreate(player) {
  const pick = await pickFromParty(player, t(player, "gts.list.title"), t(player, "gts.list.note"));
  if (!pick) return;
  // wanted species via text search (input needs a vanilla modal - Bedrock limit)
  const mf = new ModalFormData().title(t(player, "gts.want.title"));
  mf.textField(t(player, "gts.want.species"), t(player, "gts.want.species.ph"));
  mf.slider(t(player, "gts.want.minlvl"), 1, 100, { valueStep: 1, defaultValue: 1 });
  mf.toggle(t(player, "gts.want.shinyonly"), { defaultValue: false });
  let res;
  try { res = await mf.show(player); } catch { return; }
  if (res.canceled) return;
  const [nameQ, lvl, sh] = res.formValues;
  const q = String(nameQ).trim().toLowerCase();
  const hit = Object.entries(POKENAMES).find(([, n]) => n.toLowerCase() === q) ??
              Object.entries(POKENAMES).find(([, n]) => n.toLowerCase().startsWith(q));
  if (!q || !hit) return msg(player, t(player, "gts.unknownspecies", { name: nameQ }));
  const want = { dex: Number(hit[0]), lvl: Number(lvl), sh: !!sh };
  const list = gtsList();
  if (list.filter((e) => e.id === player.id).length >= 3) return msg(player, t(player, "gts.max"));
  if (list.length >= GTS_CAP) return msg(player, t(player, "gts.full"));
  if (!(await confirm(player, "serp.main.trade", t(player, "gts.list.confirm", { offer: pokeLabel(pick.fields), want: wantLabel(player, want) })))) return;
  const fields = S.takePokemon(player, pick.tag);
  if (!fields) return msg(player, t(player, "gts.moved"));
  suppressCatch(player.id);
  list.push({ f: fields, id: player.id, n: player.name, want, at: Date.now() });
  jset("sl:gts", list);
  msg(player, t(player, "gts.listed"));
}

async function gtsBrowse(player) {
  const list = gtsList();
  const others = list.filter((e) => e.id !== player.id);
  if (others.length === 0) return msg(player, t(player, "gts.nolistings"));
  const form = themedRaw("serp.main.trade").body(t(player, "gts.browse.body", { n: others.length }));
  for (const e of others)
    form.button(t(player, "gts.browse.btn", { label: pokeLabel(e.f), want: wantLabel(player, e.want), name: e.n }), "pokedrock/pokedex/" + e.f[2]);
  form.button(t(player, "common.back"), "textures/ui/buttons/bubble_no");
  const res = await form.show(player).catch(() => null);
  if (!res || res.canceled || res.selection >= others.length) return;
  const entry = others[res.selection];
  const pick = await pickFromParty(player, t(player, "gts.fulfill.title"),
    t(player, "gts.fulfill.note", { want: wantLabel(player, entry.want) }),
    (f) => matches(f, entry.want));
  if (!pick) return;
  if (!(await confirm(player, "serp.main.trade", t(player, "gts.trade.confirm", { give: pokeLabel(pick.fields), get: pokeLabel(entry.f) })))) return;
  const cur = gtsList(); // re-validate against live data
  const li = cur.findIndex((e) => e.id === entry.id && e.at === entry.at);
  if (li < 0) return msg(player, t(player, "gts.taken"));
  const fields = S.takePokemon(player, pick.tag);
  if (!fields) return msg(player, t(player, "gts.moved"));
  if (!matches(fields, entry.want)) { S.givePokemon(player, fields); return msg(player, t(player, "gts.nomatch")); }
  suppressCatch(player.id);
  cur.splice(li, 1);
  jset("sl:gts", cur);
  deliver(entry.id, entry.n, fields);
  if (S.givePokemon(player, entry.f)) msg(player, t(player, "gts.trade.done", { label: pokeLabel(entry.f), name: entry.n }));
  else { deliver(player.id, player.name, entry.f); msg(player, t(player, "gts.trade.donepc")); }
  try { player.playSound("random.levelup"); } catch {}
}

async function gtsMine(player) {
  const list = gtsList();
  const mine = list.filter((e) => e.id === player.id);
  if (mine.length === 0) return msg(player, t(player, "gts.nomine"));
  const form = themedRaw("serp.main.trade").body(t(player, "gts.mine.body"));
  for (const e of mine) form.button(t(player, "gts.mine.btn", { label: pokeLabel(e.f), want: wantLabel(player, e.want) }), "pokedrock/pokedex/" + e.f[2]);
  form.button(t(player, "common.back"), "textures/ui/buttons/bubble_no");
  const res = await form.show(player).catch(() => null);
  if (!res || res.canceled || res.selection >= mine.length) return;
  const entry = mine[res.selection];
  const cur = gtsList();
  const li = cur.findIndex((e) => e.id === entry.id && e.at === entry.at);
  if (li < 0) return msg(player, t(player, "gts.mine.completed"));
  cur.splice(li, 1);
  jset("sl:gts", cur);
  suppressCatch(player.id);
  if (!S.givePokemon(player, entry.f)) deliver(player.id, player.name, entry.f);
  msg(player, t(player, "gts.mine.cancelled", { label: pokeLabel(entry.f) }));
}

async function openMachine(player) {
  const o = owed()[player.id]?.length ?? 0;
  const form = themedRaw("serp.main.trade")
    .body(t(player, "gts.machine.body", { owed: o ? t(player, "gts.machine.owed", { n: o }) : "" }))
    .button(t(player, "gts.machine.wonder"), "pokedrock/items/poke_ball")
    .button(t(player, "gts.machine.browse"), "pokedrock/items/great_ball")
    .button(t(player, "gts.machine.list"), "pokedrock/items/ultra_ball")
    .button(t(player, "gts.machine.mine"), "pokedrock/items/premier_ball");
  form.button(t(player, "common.close"), "textures/ui/buttons/bubble_no");
  const res = await form.show(player).catch(() => null);
  if (!res || res.canceled) return;
  if (res.selection === 0) return openWonder(player);
  if (res.selection === 1) return gtsBrowse(player);
  if (res.selection === 2) return gtsCreate(player);
  if (res.selection === 3) return gtsMine(player);
}

export function initGts() {
  world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
    try {
      const { player, target } = ev;
      if (!player || !target || target.typeId !== MACHINE) return;
      if (!player.isSneaking) return; // normal tap = SERP's machine
      ev.cancel = true;
      system.run(() => openMachine(player));
    } catch {}
  });
  // deliver owed pokemon to whoever is online
  system.runInterval(() => {
    const o = owed();
    let changed = false;
    for (const p of world.getAllPlayers()) {
      const q = o[p.id];
      if (!q || q.length === 0) continue;
      while (q.length > 0) {
        suppressCatch(p.id);
        if (!S.givePokemon(p, q[0])) break; // team+PC full, retry later
        msg(p, t(p, "gts.delivery", { label: pokeLabel(q.shift()) }));
        try { p.playSound("random.levelup"); } catch {}
        changed = true;
      }
      if (q.length === 0) { delete o[p.id]; changed = true; }
    }
    if (changed) jset("sl:owed", o);
  }, 200);
}
