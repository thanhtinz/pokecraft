// tracker.js - central catch detector. SERP stores caught Pokemon as player
// tags, so a new team*/stg* tag appearing = a new Pokemon obtained. We diff
// tag snapshots every 5s and feed everything that needs it:
//   - per-player stats  (sx:stats: catch / shiny counts)
//   - caught-dex set    (sl:dex: array of dex ints)
//   - shiny broadcast   (server-wide message + sound)
//   - bounty quests     (progress "catch_species" / "catch_any")
//   - leaderboard snapshot (world sl:lb, survives players going offline)
import { world, system } from "@minecraft/server";
import * as S from "./serpdata.js";
import { progress } from "./quests.js";

const snapshots = new Map(); // playerId -> Set of pokemon tags
let suppress = new Map(); // playerId -> until-ts: our own trades/GTS must not count as catches

let _tp=null;
function require_titlePrefix(p){ if(!_tp){ /* set by titles.js at init */ } return _tp? _tp(p):""; }
export function setTitlePrefixFn(f){ _tp=f; }

export function suppressCatch(playerId, ms = 8000) {
  suppress.set(playerId, Date.now() + ms);
}

function pokeTags(player) {
  const out = new Set();
  for (const t of player.getTags()) if (S.isPartyTag(t) || S.isPCTag(t)) out.add(t);
  return out;
}

function stats(player) {
  try { return JSON.parse(player.getDynamicProperty("sx:stats") ?? "{}"); } catch { return {}; }
}
function setStats(player, st) {
  try { player.setDynamicProperty("sx:stats", JSON.stringify(st)); } catch {}
}
export function dexSet(player) {
  try { return new Set(JSON.parse(player.getDynamicProperty("sl:dex") ?? "[]")); } catch { return new Set(); }
}
function setDexSet(player, s) {
  try { player.setDynamicProperty("sl:dex", JSON.stringify([...s])); } catch {}
}

function onNewPokemon(player, fields, counted, tag) {
  const dex = Number(fields[2]);
  const ds = dexSet(player);
  if (!ds.has(dex)) { ds.add(dex); setDexSet(player, ds); }
  if (!counted) return; // trade/GTS arrivals still register the dex, not the catch
  const st = stats(player);
  st.catch = (st.catch ?? 0) + 1;
  if (S.isShiny(fields)) {
    st.shiny = (st.shiny ?? 0) + 1;
    try {
      let pre = "";
      try { pre = require_titlePrefix(player); } catch {}
      world.sendMessage({
        rawtext: [
          { text: "\u00a76\u2605 " + pre + "\u00a7l" + player.name + "\u00a7r\u00a76 caught a SHINY \u00a7l" },
          S.displayName(fields),
          { text: "\u00a7r\u00a76 \uE132 Lv." + S.level(fields) + "!" },
        ],
      });
      for (const p of world.getAllPlayers()) p.playSound("random.levelup");
    } catch {}
  }
  setStats(player, st);
  progress(player, "catch_any", "");
  progress(player, "catch_species", "pokemon:p" + dex);

}

// ---------- leaderboard snapshot ----------
export function lbData() {
  try { return JSON.parse(world.getDynamicProperty("sl:lb") ?? "{}"); } catch { return {}; }
}
function updateLb() {
  const lb = lbData();
  for (const p of world.getAllPlayers()) {
    const st = stats(p);
    let maxLvl = 0;
    try { for (const t of S.getParty(p)) maxLvl = Math.max(maxLvl, S.level(t.fields)); } catch {}
    lb[p.name] = { c: st.catch ?? 0, s: st.shiny ?? 0, d: dexSet(p).size, l: maxLvl };
  }
  try { world.setDynamicProperty("sl:lb", JSON.stringify(lb)); } catch {}
}

export function initTracker() {
  system.runInterval(() => {
    const now = Date.now();
    const online = new Set();
    for (const p of world.getAllPlayers()) {
      online.add(p.id);
      let cur;
      try { cur = pokeTags(p); } catch { continue; }
      const prev = snapshots.get(p.id);
      snapshots.set(p.id, cur);
      if (!prev) { // first sight: baseline dex set from what they already own
        const ds = dexSet(p);
        let grew = false;
        for (const t of cur) { const f = S.decode(t); if (f && !ds.has(Number(f[2]))) { ds.add(Number(f[2])); grew = true; } }
        if (grew) setDexSet(p, ds);
        continue;
      }
      const counted = (suppress.get(p.id) ?? 0) < now;
      for (const t of cur) {
        if (prev.has(t)) continue;
        const f = S.decode(t);
        if (f) try { onNewPokemon(p, f, counted, t); } catch {}
      }
    }
    for (const id of snapshots.keys()) if (!online.has(id)) snapshots.delete(id);
  }, 100);
  system.runInterval(updateLb, 100 * 60 * 3); // snapshot every 5 min
  system.runTimeout(updateLb, 200);
}
