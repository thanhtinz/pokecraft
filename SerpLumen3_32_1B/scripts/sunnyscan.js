// sunnyscan.js - the SunHub Menu (sunhub:sunny).
// Works like SERP's own Pokedex-pointing, but ours:
//   Tap a Pokemon (wild or sent-out) -> info card in SERP's skinned frame
//   Tap a player                     -> trade request board (pick / respond / confirm)
// Zero commands. Trade swaps the raw 22-field team tags directly (lossless:
// moves, IVs, EVs, shiny, nature, nickname all travel with the tag).
import { world, system } from "@minecraft/server";
import { themedRaw } from "./ui.js";
import * as S from "./serpdata.js";
import { SPECIES } from "./speciesdata.js";
import { CATCH_RATE, DEX_EXTRAS } from "./speciesdata.js";

export const SUNNY_ITEM = "sunhub:menu"; // the hub item does it all now

// Tapping an entity while holding the hub item also fires itemUse right
// after on Bedrock - itemtools checks this so the hub menu doesn't open
// on top of the scan/trade form.
const lastEntityTap = new Map();
export function justTappedEntity(playerId) {
  return Date.now() - (lastEntityTap.get(playerId) ?? 0) < 400;
}

const M = "\uE130"; // male glyph (SERP font, blue)
const F = "\uE131"; // female glyph (red)
const SH = "\uE132"; // shiny glyph (green)

const TYPE_NAMES = ["", "Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass", "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water"];

function genderMark(v) {
  if (v === 1 || v === 4) return " " + M;
  if (v === 2 || v === 5) return " " + F;
  return "";
}

// IV rating: SERP IVs are 1-31 each. Color per stat + overall verdict.
const IV_STATS = ["HP", "Atk", "Def", "SpA", "SpD", "Spe"];
export function ivRating(fields) {
  const ivs = fields[19].split("%").map(Number);
  const total = ivs.reduce((a, b) => a + b, 0);
  const maxed = ivs.filter((v) => v >= 31).length;
  const colored = ivs.map((v, i) =>
    (v >= 31 ? "\u00a76" : v >= 25 ? "\u00a7a" : v >= 15 ? "\u00a7e" : "\u00a77") + IV_STATS[i] + " " + v + "\u00a7r"
  ).join(" ");
  const pct = total / 186;
  const verdict = maxed >= 4 ? "\u00a76Outstanding \u2605" : pct >= 0.8 ? "\u00a7aExcellent" : pct >= 0.6 ? "\u00a7eGood" : pct >= 0.4 ? "\u00a7fAverage" : "\u00a77Modest";
  return { line: colored, verdict: verdict + "\u00a7r (" + total + "/186, " + maxed + "x31)" };
}

function dexOf(entity) {
  return Number(entity.typeId.slice(9)) || 0;
}

// Wild level estimate from max HP (SERP HP formula inverted, iv~15 ev=0).
function estLevel(dex, maxHp) {
  const sp = SPECIES[String(dex)];
  if (!sp || !maxHp) return 0;
  const base = sp[3][0];
  const lvl = Math.round((maxHp - 10) / ((2 * base + 15) / 100 + 1));
  return Math.max(1, Math.min(100, lvl));
}

// ---------- SCAN ----------

function openScan(player, target) {
  if (!target.isValid) return;
  const dex = dexOf(target);
  let v = 0;
  try { v = Number(target.getProperty("serp:v")) || 0; } catch {}
  let hp = null;
  try {
    const h = target.getComponent("minecraft:health");
    if (h) hp = { cur: Math.round(h.currentValue), max: Math.round(h.effectiveMax) };
  } catch {}
  const tamed = target.getTags().includes("tamed");
  const sp = SPECIES[String(dex)];
  const shiny = v > 2;

  const rt = [
    { text: "\u00a7l" },
    target.nameTag ? { text: "\u00a7z" + target.nameTag } : { translate: "entity.pokemon:p" + dex + ".name" },
    { text: "\u00a7r" + genderMark(v) + (shiny ? " " + SH + " \u00a76SHINY\u00a7r" : "") + "\n\n" },
    { text: "Dex: #" + dex + "  \u00a77(" + (tamed ? "trainer's" : "wild") + ")\u00a7r\n" },
  ];
  if (hp) {
    const est = !tamed ? estLevel(dex, hp.max) : 0;
    rt.push({ text: "HP: " + hp.cur + "/" + hp.max + (est ? "  \u00a77~Lv." + est + "\u00a7r" : "") + "\n" });
  }
  if (sp) {
    const [, , , stats, t1, t2] = sp;
    rt.push({ text: "Type: " + TYPE_NAMES[t1] + (t2 ? " / " + TYPE_NAMES[t2] : "") + "\n" });
    rt.push({ text: "Base: " + stats.join("/") + "  (BST " + stats.reduce((a, b) => a + b, 0) + ")\n" });
  }
  const cr = CATCH_RATE[String(dex)];
  if (cr && !tamed) rt.push({ text: "Catch rate: " + cr + "/255\n" });
  // If it's one of the scanner's own sent-out Pokemon, we can read its full
  // data from their party tag -> show the IV potential rating.
  if (tamed) {
    const own = S.getParty(player).filter((p) => Number(p.fields[2]) === dex);
    if (own.length === 1) {
      const r = ivRating(own[0].fields);
      rt.push({ text: "\nPotential: " + r.verdict + "\n" + r.line + "\n" });
    }
  }
  const ex = DEX_EXTRAS[String(dex)];
  if (ex) rt.push({ text: "\u00a77" + ex[0] + " kg, " + ex[1] + " m - " + ex[2] + ex[3] + (ex[4] === "empty" ? "" : ex[4]) + "\u00a7r" });

  themedRaw("serp.main.pokedex_cyan")
    .body({ rawtext: rt })
    .button({ translate: "entity.pokemon:p" + dex + ".name" }, "pokedrock/pokedex/" + dex)
    .button("Close", "textures/ui/buttons/bubble_no")
    .show(player)
    .catch(() => {});
}

// ---------- TRADE ----------
// trades: key = a.id + "|" + b.id  (a = requester)
// { a, b, aName, bName, aTag, bTag, stage: "offered"|"picked", at }
const trades = new Map();
const TTL = 120000;

function sweep() {
  const now = Date.now();
  for (const [k, t] of trades) if (now - t.at > TTL) trades.delete(k);
}

function findByPlayerId(id) {
  for (const p of world.getAllPlayers()) if (p.id === id) return p;
  return null;
}

function pokeButton(form, fields) {
  const lvl = S.level(fields);
  form.button({
    rawtext: [
      S.displayName(fields),
      { text: " Lv." + lvl + genderMark(Number(fields[3])) + (S.isShiny(fields) ? " " + SH : "") },
    ],
  }, "pokedrock/pokedex/" + fields[2]);
}

function offerBody(name, fields) {
  const ivs = fields[19].split("%");
  const r = ivRating(fields);
  return {
    rawtext: [
      { text: "\u00a7l" + name + "\u00a7r offers:\n\n\u00a7l" },
      S.displayName(fields),
      { text: "\u00a7r Lv." + S.level(fields) + genderMark(Number(fields[3])) + (S.isShiny(fields) ? " " + SH + " \u00a76SHINY\u00a7r" : "") },
      { text: "\nIVs: " + ivs.join("/") + "\nPotential: " + r.verdict + "\nFriendship: " + fields[7] + "/255\n" },
    ],
  };
}

// Guard: the traded Pokemon must not be sent out (its world entity would
// orphan if we swap the tag underneath SERP). Best-effort check nearby.
function sentOutNearby(player, dex) {
  try {
    return [...player.dimension.getEntities({ location: player.location, maxDistance: 48 })]
      .some((e) => e.isValid && e.typeId === "pokemon:p" + dex && e.getTags().includes("tamed"));
  } catch { return false; }
}

function msg(p, text) { try { p.sendMessage("\u00a7e[Sunny]\u00a7r " + text); } catch {} }
function ding(p) { try { p.playSound("random.orb"); } catch {} }

// A taps B: start a new offer
function openOffer(a, b) {
  sweep();
  const party = S.getParty(a);
  if (party.length === 0) return msg(a, "You have no Pokemon to trade.");
  const form = themedRaw("serp.main.trade").body("\u00a7lTRADE with " + b.name + "\u00a7r\nPick the Pokemon you offer.\n\u00a7cRecall it to its ball first!\u00a7r");
  for (const p of party) pokeButton(form, p.fields);
  form.button("Cancel", "textures/ui/buttons/bubble_no");
  form.show(a).then((res) => {
    if (res.canceled || res.selection >= party.length) return;
    const pick = party[res.selection];
    if (sentOutNearby(a, Number(pick.fields[2]))) return msg(a, "That Pokemon looks sent out - recall it to its ball first.");
    trades.set(a.id + "|" + b.id, { a: a.id, b: b.id, aName: a.name, bName: b.name, aTag: pick.tag, bTag: null, stage: "offered", at: Date.now() });
    msg(a, "Offer sent to \u00a7l" + b.name + "\u00a7r. Waiting...");
    ding(b);
    msg(b, "\u00a7l" + a.name + "\u00a7r wants to trade! Tap them with your SunHub Menu to answer.");
    system.run(() => openRespond(b, a));
  }).catch(() => {});
}

// B answers A's offer
function openRespond(b, a) {
  const t = trades.get(a.id + "|" + b.id);
  if (!t || t.stage !== "offered") return;
  if (!a.hasTag(t.aTag)) { trades.delete(a.id + "|" + b.id); return msg(b, "The offered Pokemon is gone."); }
  const fields = S.decode(t.aTag);
  const party = S.getParty(b);
  const form = themedRaw("serp.main.trade").body(offerBody(t.aName, fields));
  for (const p of party) pokeButton(form, p.fields);
  form.button("\u00a7cDecline\u00a7r", "textures/ui/buttons/bubble_no");
  form.show(b).then((res) => {
    if (res.canceled) return; // leave pending; B can tap A again
    if (res.selection >= party.length) {
      trades.delete(a.id + "|" + b.id);
      msg(b, "Trade declined.");
      const ap = findByPlayerId(a.id);
      if (ap) msg(ap, "\u00a7l" + b.name + "\u00a7r declined your trade.");
      return;
    }
    const pick = party[res.selection];
    if (sentOutNearby(b, Number(pick.fields[2]))) return msg(b, "That Pokemon looks sent out - recall it first.");
    t.bTag = pick.tag; t.stage = "picked"; t.at = Date.now();
    msg(b, "Waiting for \u00a7l" + t.aName + "\u00a7r to confirm...");
    const ap = findByPlayerId(a.id);
    if (ap) { ding(ap); msg(ap, "\u00a7l" + b.name + "\u00a7r answered! Tap them with your SunHub Menu to confirm."); system.run(() => openConfirm(ap, b)); }
  }).catch(() => {});
}

// A confirms the final swap
function openConfirm(a, b) {
  const t = trades.get(a.id + "|" + b.id);
  if (!t || t.stage !== "picked") return;
  const aF = a.hasTag(t.aTag) ? S.decode(t.aTag) : null;
  const bF = b.hasTag(t.bTag) ? S.decode(t.bTag) : null;
  if (!aF || !bF) { trades.delete(a.id + "|" + b.id); return msg(a, "Trade expired - a Pokemon moved."); }
  themedRaw("serp.main.trade")
    .body({
      rawtext: [
        { text: "\u00a7lFINAL CONFIRM\u00a7r\n\nYou give: \u00a7l" }, S.displayName(aF),
        { text: "\u00a7r Lv." + S.level(aF) + (S.isShiny(aF) ? " " + SH : "") + "\nYou get:  \u00a7l" }, S.displayName(bF),
        { text: "\u00a7r Lv." + S.level(bF) + (S.isShiny(bF) ? " " + SH : "") + "\n\nFrom: " + t.bName },
      ],
    })
    .button("\u00a7aConfirm trade\u00a7r", "textures/ui/buttons/bubble_yes")
    .button("\u00a7cCancel\u00a7r", "textures/ui/buttons/bubble_no")
    .show(a)
    .then((res) => {
      if (res.canceled) return;
      if (res.selection !== 0) {
        trades.delete(a.id + "|" + b.id);
        msg(a, "Trade cancelled."); const bp = findByPlayerId(b.id); if (bp) msg(bp, "Trade cancelled.");
        return;
      }
      commit(a, b, t);
    }).catch(() => {});
}

function commit(a, b, t) {
  trades.delete(a.id + "|" + b.id);
  const bp = findByPlayerId(b.id);
  if (!bp) return msg(a, t.bName + " went offline - trade cancelled.");
  try {
    const dx = a.location.x - bp.location.x, dz = a.location.z - bp.location.z;
    if (a.dimension.id !== bp.dimension.id || dx * dx + dz * dz > 24 * 24) return msg(a, "Too far apart - stand closer and retry.");
  } catch {}
  const aFields = S.takePokemon(a, t.aTag);
  const bFields = S.takePokemon(bp, t.bTag);
  if (!aFields || !bFields) { // one side vanished - full rollback
    if (aFields) a.addTag(t.aTag);
    if (bFields) bp.addTag(t.bTag);
    msg(a, "Trade failed - a Pokemon moved. Nothing was exchanged.");
    msg(bp, "Trade failed - a Pokemon moved. Nothing was exchanged.");
    return;
  }
  const r1 = S.givePokemon(a, bFields);
  const r2 = S.givePokemon(bp, aFields);
  if (!r1 || !r2) { // storage full - undo everything
    if (r1) { const back = S.getParty(a).concat(S.getPC(a)).find((x) => x.fields[2] === bFields[2] && x.fields[8] === bFields[8]); if (back) a.removeTag(back.tag); }
    if (r2) { const back = S.getParty(bp).concat(S.getPC(bp)).find((x) => x.fields[2] === aFields[2] && x.fields[8] === aFields[8]); if (back) bp.removeTag(back.tag); }
    a.addTag(t.aTag); bp.addTag(t.bTag);
    msg(a, "Trade failed - storage full. Nothing was exchanged.");
    msg(bp, "Trade failed - storage full. Nothing was exchanged.");
    return;
  }
  for (const [p, got] of [[a, bFields], [bp, aFields]]) {
    try { p.playSound("random.levelup"); } catch {}
    msg(p, "Trade complete! You received a Pokemon (sent to " + ((p === a ? r1 : r2) === "team" ? "your team" : "your PC") + ").");
  }
}

// ---------- Trainer Card ----------
let _titlesMod = null;
function requireTitles() {
  if (!_titlesMod) throw 0;
  return _titlesMod;
}
export function setTitlesModule(m) { _titlesMod = m; }


function openCard(viewer, target) {
  if (!target.isValid) return;
  let st = {};
  try { st = JSON.parse(target.getDynamicProperty("sx:stats") ?? "{}"); } catch {}
  let dexCount = 0;
  try { dexCount = JSON.parse(target.getDynamicProperty("sl:dex") ?? "[]").length; } catch {}
  const badges = S.badgeCount(target);
  const party = S.getParty(target);
  let titleLine = "";
  try { const { titlePrefix } = requireTitles(); titleLine = titlePrefix(target); } catch {}
  const rt = [
    { text: "\u00a7lTRAINER CARD\u00a7r\n" + titleLine + "\u00a7l" + target.name + "\u00a7r\n\n" },
    { text: "Badges: \u00a76" + badges + "/18\u00a7r   Dex: \u00a7b" + dexCount + "\u00a7r\n" },
    { text: "Caught: " + (st.catch ?? 0) + "   Shiny: \u00a76" + (st.shiny ?? 0) + " " + SH + "\u00a7r\n\nTeam:\n" },
  ];
  if (party.length === 0) rt.push({ text: "\u00a77(empty)\u00a7r" });
  for (const p of party) {
    rt.push(S.displayName(p.fields));
    rt.push({ text: " Lv." + S.level(p.fields) + genderMark(Number(p.fields[3])) + (S.isShiny(p.fields) ? " " + SH : "") + "\n" });
  }
  themedRaw("serp.main.summary")
    .body({ rawtext: rt })
    .button("Close", "textures/ui/buttons/bubble_no")
    .show(viewer)
    .catch(() => {});
}

// ---------- entry point ----------

export function initSunnyScan() {
  world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
    try {
      const { player, target, itemStack } = ev;
      if (!player || !target || !itemStack || itemStack.typeId !== SUNNY_ITEM) return;
      if (typeof target.typeId === "string" && target.typeId.startsWith("pokemon:p")) {
        ev.cancel = true;
        lastEntityTap.set(player.id, Date.now());
        system.run(() => openScan(player, target));
      } else if (target.typeId === "minecraft:player") {
        ev.cancel = true;
        lastEntityTap.set(player.id, Date.now());
        system.run(() => {
          sweep();
          // If the tapped player has something pending toward me, answer it.
          const incoming = trades.get(target.id + "|" + player.id);
          if (incoming && incoming.stage === "offered") return openRespond(player, target);
          const mine = trades.get(player.id + "|" + target.id);
          if (mine && mine.stage === "picked") return openConfirm(player, target);
          if (mine && mine.stage === "offered") return msg(player, "Still waiting for \u00a7l" + target.name + "\u00a7r to answer.");
          // nothing pending: Trade or Trainer Card
          themedRaw("serp.main.sunhub")
            .body("\u00a7l" + target.name + "\u00a7r")
            .button("Trade Pokemon", "pokedrock/items/poke_ball")
            .button("Trainer Card", "textures/items/name_tag")
            .button("Close", "textures/ui/buttons/bubble_no")
            .show(player)
            .then((res) => {
              if (res.canceled) return;
              if (res.selection === 0) return openOffer(player, target);
              if (res.selection === 1) return openCard(player, target);
            }).catch(() => {});
        });
      }
    } catch {}
  });
}
