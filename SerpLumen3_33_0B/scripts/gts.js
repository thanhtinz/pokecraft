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

const MACHINE = "serp:trade_machine";
const WONDER_CAP = 20;
const GTS_CAP = 30;
const SH = "\uE132";

function jget(key, fb) { try { return JSON.parse(world.getDynamicProperty(key) ?? fb); } catch { return JSON.parse(fb); } }
function jset(key, v) { try { world.setDynamicProperty(key, JSON.stringify(v)); } catch {} }
const wonderPool = () => jget("sl:wonder", "[]");
const gtsList = () => jget("sl:gts", "[]");
const owed = () => jget("sl:owed", "{}");

function msg(p, t) { try { p.sendMessage("\u00a7e[Trade Machine]\u00a7r " + t); } catch {} }
function nameOf(dex) { return POKENAMES[String(dex)] ?? ("#" + dex); }

function pokeLabel(fields) {
  return nameOf(Number(fields[2])) + " Lv." + S.level(fields) + (S.isShiny(fields) ? " " + SH : "");
}

// deliver a pokemon: direct if online, else owed queue
function deliver(ownerId, ownerName, fields) {
  for (const p of world.getAllPlayers()) {
    if (p.id === ownerId) {
      suppressCatch(p.id);
      if (S.givePokemon(p, fields)) { msg(p, "You received \u00a7l" + pokeLabel(fields) + "\u00a7r!"); try { p.playSound("random.levelup"); } catch {} return; }
    }
  }
  const o = owed();
  (o[ownerId] = o[ownerId] ?? []).push(fields);
  jset("sl:owed", o);
}

function pickFromParty(player, title, note, filter) {
  const party = S.getParty(player).filter((p) => !filter || filter(p.fields));
  if (party.length === 0) { msg(player, "No eligible Pokemon in your team."); return Promise.resolve(null); }
  const form = themedRaw("serp.main.trade").body("\u00a7l" + title + "\u00a7r" + (note ? "\n" + note : ""));
  for (const p of party) form.button(pokeLabel(p.fields), "pokedrock/pokedex/" + p.fields[2]);
  form.button("Cancel", "textures/ui/buttons/bubble_no");
  return form.show(player).then((res) =>
    res.canceled || res.selection >= party.length ? null : party[res.selection]
  ).catch(() => null);
}

// ---------- WONDER TRADE ----------
async function openWonder(player) {
  const pool = wonderPool();
  const mine = pool.filter((e) => e.id === player.id).length;
  const pick = await pickFromParty(player, "WONDER TRADE",
    "Send one Pokemon, get a random one\nfrom another trainer. No take-backs!\n\u00a77Waiting in pool: " + pool.length + (mine ? " (yours: " + mine + ")" : "") + "\u00a7r");
  if (!pick) return;
  if (!(await confirm(player, "serp.main.trade", "\u00a7lWonder Trade\u00a7r\n\nSend away \u00a7l" + pokeLabel(pick.fields) + "\u00a7r?\nYou can't choose what comes back."))) return;
  const fields = S.takePokemon(player, pick.tag);
  if (!fields) return msg(player, "That Pokemon just moved - try again.");
  suppressCatch(player.id);
  const p2 = wonderPool(); // re-read: someone may have deposited meanwhile
  const idx = p2.findIndex((e) => e.id !== player.id);
  if (idx >= 0) {
    const other = p2.splice(idx, 1)[0];
    jset("sl:wonder", p2);
    deliver(other.id, other.n, fields);
    if (S.givePokemon(player, other.f)) msg(player, "Matched with \u00a7l" + other.n + "\u00a7r! You got \u00a7l" + pokeLabel(other.f) + "\u00a7r!");
    else { deliver(player.id, player.name, other.f); msg(player, "Matched! Your Pokemon arrives once you have space."); }
    try { player.playSound("random.levelup"); } catch {}
  } else {
    if (p2.length >= WONDER_CAP) { S.givePokemon(player, fields); return msg(player, "Pool is full right now - try later."); }
    p2.push({ f: fields, id: player.id, n: player.name, at: Date.now() });
    jset("sl:wonder", p2);
    msg(player, "\u00a7l" + pokeLabel(fields) + "\u00a7r is in the pool. You'll get a Pokemon when someone else deposits!");
  }
}

// ---------- GTS ----------
function wantLabel(w) {
  return nameOf(w.dex) + (w.lvl > 1 ? " Lv." + w.lvl + "+" : "") + (w.sh ? " " + SH + " shiny" : "");
}
function matches(fields, w) {
  return Number(fields[2]) === w.dex && S.level(fields) >= (w.lvl ?? 1) && (!w.sh || S.isShiny(fields));
}

async function gtsCreate(player) {
  const pick = await pickFromParty(player, "LIST ON GTS", "Pick the Pokemon you offer.");
  if (!pick) return;
  // wanted species via text search (input needs a vanilla modal - Bedrock limit)
  const mf = new ModalFormData().title("GTS - what do you want?");
  mf.textField("Species name (e.g. Gengar):", "type a name...");
  mf.slider("Minimum level", 1, 100, { valueStep: 1, defaultValue: 1 });
  mf.toggle("Shiny only", { defaultValue: false });
  let res;
  try { res = await mf.show(player); } catch { return; }
  if (res.canceled) return;
  const [nameQ, lvl, sh] = res.formValues;
  const q = String(nameQ).trim().toLowerCase();
  const hit = Object.entries(POKENAMES).find(([, n]) => n.toLowerCase() === q) ??
              Object.entries(POKENAMES).find(([, n]) => n.toLowerCase().startsWith(q));
  if (!q || !hit) return msg(player, "Unknown species name: " + nameQ);
  const want = { dex: Number(hit[0]), lvl: Number(lvl), sh: !!sh };
  const list = gtsList();
  if (list.filter((e) => e.id === player.id).length >= 3) return msg(player, "Max 3 active listings per player.");
  if (list.length >= GTS_CAP) return msg(player, "GTS is full - try later.");
  if (!(await confirm(player, "serp.main.trade", "\u00a7lGTS Listing\u00a7r\n\nOffer: \u00a7l" + pokeLabel(pick.fields) + "\u00a7r\nWant: \u00a7l" + wantLabel(want) + "\u00a7r\n\nList it?"))) return;
  const fields = S.takePokemon(player, pick.tag);
  if (!fields) return msg(player, "That Pokemon just moved - try again.");
  suppressCatch(player.id);
  list.push({ f: fields, id: player.id, n: player.name, want, at: Date.now() });
  jset("sl:gts", list);
  msg(player, "Listed! You'll receive the trade even while offline.");
}

async function gtsBrowse(player) {
  const list = gtsList();
  const others = list.filter((e) => e.id !== player.id);
  if (others.length === 0) return msg(player, "No listings from other trainers right now.");
  const form = themedRaw("serp.main.trade").body("\u00a7lGTS - " + others.length + " listings\u00a7r\nTap one you can fulfill:");
  for (const e of others)
    form.button(pokeLabel(e.f) + "\n\u00a77wants: " + wantLabel(e.want) + " \u00a78(" + e.n + ")", "pokedrock/pokedex/" + e.f[2]);
  form.button("Back", "textures/ui/buttons/bubble_no");
  const res = await form.show(player).catch(() => null);
  if (!res || res.canceled || res.selection >= others.length) return;
  const entry = others[res.selection];
  const pick = await pickFromParty(player, "FULFILL LISTING",
    "They want: \u00a7l" + wantLabel(entry.want) + "\u00a7r\nPick a matching Pokemon to give:",
    (f) => matches(f, entry.want));
  if (!pick) return;
  if (!(await confirm(player, "serp.main.trade", "\u00a7lGTS Trade\u00a7r\n\nGive: \u00a7l" + pokeLabel(pick.fields) + "\u00a7r\nGet:  \u00a7l" + pokeLabel(entry.f) + "\u00a7r\n\nConfirm?"))) return;
  const cur = gtsList(); // re-validate against live data
  const li = cur.findIndex((e) => e.id === entry.id && e.at === entry.at);
  if (li < 0) return msg(player, "That listing was just taken.");
  const fields = S.takePokemon(player, pick.tag);
  if (!fields) return msg(player, "That Pokemon just moved - try again.");
  if (!matches(fields, entry.want)) { S.givePokemon(player, fields); return msg(player, "It no longer matches the request."); }
  suppressCatch(player.id);
  cur.splice(li, 1);
  jset("sl:gts", cur);
  deliver(entry.id, entry.n, fields);
  if (S.givePokemon(player, entry.f)) msg(player, "Trade done! You got \u00a7l" + pokeLabel(entry.f) + "\u00a7r from " + entry.n + "!");
  else { deliver(player.id, player.name, entry.f); msg(player, "Trade done! It arrives once you have space."); }
  try { player.playSound("random.levelup"); } catch {}
}

async function gtsMine(player) {
  const list = gtsList();
  const mine = list.filter((e) => e.id === player.id);
  if (mine.length === 0) return msg(player, "You have no active listings.");
  const form = themedRaw("serp.main.trade").body("\u00a7lMY LISTINGS\u00a7r\nTap to cancel and take back:");
  for (const e of mine) form.button(pokeLabel(e.f) + "\n\u00a77wants: " + wantLabel(e.want), "pokedrock/pokedex/" + e.f[2]);
  form.button("Back", "textures/ui/buttons/bubble_no");
  const res = await form.show(player).catch(() => null);
  if (!res || res.canceled || res.selection >= mine.length) return;
  const entry = mine[res.selection];
  const cur = gtsList();
  const li = cur.findIndex((e) => e.id === entry.id && e.at === entry.at);
  if (li < 0) return msg(player, "That listing already completed - check your team/PC!");
  cur.splice(li, 1);
  jset("sl:gts", cur);
  suppressCatch(player.id);
  if (!S.givePokemon(player, entry.f)) deliver(player.id, player.name, entry.f);
  msg(player, "Listing cancelled, \u00a7l" + pokeLabel(entry.f) + "\u00a7r returned.");
}

async function openMachine(player) {
  const o = owed()[player.id]?.length ?? 0;
  const form = themedRaw("serp.main.trade")
    .body("\u00a7lTRADE MACHINE+\u00a7r\n\u00a77(tap without sneaking for SERP's own trade)\u00a7r" + (o ? "\n\u00a7a" + o + " Pokemon waiting for you!\u00a7r" : ""))
    .button("Wonder Trade\n\u00a78Send one, get a surprise", "pokedrock/items/poke_ball")
    .button("GTS - Browse listings\n\u00a78Fulfill someone's request", "pokedrock/items/great_ball")
    .button("GTS - List a Pokemon\n\u00a78Set what you want back", "pokedrock/items/ultra_ball")
    .button("GTS - My listings\n\u00a78Cancel / take back", "pokedrock/items/premier_ball");
  form.button("Close", "textures/ui/buttons/bubble_no");
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
        msg(p, "Delivery: \u00a7l" + pokeLabel(q.shift()) + "\u00a7r arrived from a trade!");
        try { p.playSound("random.levelup"); } catch {}
        changed = true;
      }
      if (q.length === 0) { delete o[p.id]; changed = true; }
    }
    if (changed) jset("sl:owed", o);
  }, 200);
}
