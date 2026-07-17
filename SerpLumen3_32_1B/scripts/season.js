// season.js - weekly leaderboard seasons. Every Monday the boards reset:
// scores become "this week's" (delta vs. a baseline snapshot), and the top 3
// of each category from the finished week are paid automatically with
// ADMIN-CONFIGURED coin rewards (Events & Gifts -> Season Rewards).
// Offline winners get their coins queued and paid on next join. No new
// currency - everything goes to SERP's 'money' scoreboard via addCoins.
import { world, system } from "@minecraft/server";
import { lbData } from "./tracker.js";
import { addCoins, fmt } from "./economy.js";
import { dayNumber, giveItem } from "./forms.js";
import { POKENAMES } from "./pokenames.js";
import { givePokemon } from "./dxgive.js";
import { suppressCatch } from "./tracker.js";
import { grantByName, top1Titles } from "./titles.js";

const SEASON = "sl:season";     // { w }
const BASE = "sl:lb_base";      // snapshot of sl:lb at week start
const REWARDS = "sl:searewards";// { r1: {coins,item?,poke?}, r2, r3 } per rank, per category
const ENABLED = "sl:seaon";     // "0" = leaderboards & payouts off
const OWED = "sl:owedprize";    // { playerName: {coins, items:[{id,name,qty}], pokes:[{dex,lvl,shiny}]} }

export const CATS = [
  ["Pokedex completion", "d", "species"],
  ["Shiny caught", "s", "shiny"],
  ["Total caught", "c", "caught"],
  ["Highest level", "l", "Lv"],
];

function jget(k, fb) { try { return JSON.parse(world.getDynamicProperty(k) ?? fb); } catch { return JSON.parse(fb); } }
function jset(k, v) { try { world.setDynamicProperty(k, JSON.stringify(v)); } catch {} }

export function seasonEnabled() { return world.getDynamicProperty(ENABLED) !== "0"; }
export function setSeasonEnabled(on) {
  try { world.setDynamicProperty(ENABLED, on ? "1" : "0"); } catch {}
  if (on) { // fresh restart: new baseline, current week, no back-payout for the off period
    jset(BASE, lbData());
    jset(SEASON, { w: weekNum() });
    world.sendMessage("\u00a7d[SEASON] \u00a7fLeaderboards are \u00a7aON\u00a7f - new race starts now!");
  } else {
    world.sendMessage("\u00a7d[SEASON] \u00a7fLeaderboards are \u00a7cOFF\u00a7f - no weekly race for now.");
  }
}

export function weekNum() { return Math.floor((dayNumber() + 3) / 7); } // Monday-aligned
export function daysLeft() { return 7 - ((dayNumber() + 3) % 7); }
function normRank(v) {
  if (typeof v === "number") return { coins: v };            // old numeric format
  return v && typeof v === "object" ? v : { coins: 0 };
}
export function rewardsCfg() {
  const r = jget(REWARDS, "null") ?? {};
  return { r1: normRank(r.r1), r2: normRank(r.r2), r3: normRank(r.r3) };
}
export function setRewardsCfg(r) { jset(REWARDS, r); }
export function rankLabel(rk) {
  const parts = [];
  if (rk.coins > 0) parts.push(fmt(rk.coins));
  if (rk.item) parts.push(rk.item.qty + "x " + rk.item.name);
  if (rk.poke) parts.push((POKENAMES[String(rk.poke.dex)] ?? ("#" + rk.poke.dex)) + " Lv." + rk.poke.lvl + (rk.poke.shiny ? " \uE132" : ""));
  return parts.length ? parts.join(" + ") : "none";
}

// weekly score = lifetime - baseline (level stays "current highest")
export function seasonScores() {
  const lb = lbData();
  const base = jget(BASE, "{}");
  const out = {};
  for (const [name, v] of Object.entries(lb)) {
    const b = base[name] ?? { c: 0, s: 0, d: 0 };
    out[name] = {
      c: Math.max(0, (v.c ?? 0) - (b.c ?? 0)),
      s: Math.max(0, (v.s ?? 0) - (b.s ?? 0)),
      d: Math.max(0, (v.d ?? 0) - (b.d ?? 0)),
      l: v.l ?? 0,
    };
  }
  return out;
}

function giveRankNow(p, rk) {
  if (rk.coins > 0) addCoins(p, rk.coins);
  if (rk.item) giveItem(p, rk.item.id, rk.item.qty);
  if (rk.poke) { suppressCatch(p.id); givePokemon(p, rk.poke.dex, rk.poke.lvl, { shiny: !!rk.poke.shiny }); }
  try { p.playSound("random.levelup"); } catch {}
}

function payPrize(name, rk) {
  for (const p of world.getAllPlayers()) {
    if (p.name === name) { giveRankNow(p, rk); return; }
  }
  const o = jget(OWED, "{}");
  const q = o[name] ?? { coins: 0, items: [], pokes: [] };
  q.coins += rk.coins ?? 0;
  if (rk.item) q.items.push(rk.item);
  if (rk.poke) q.pokes.push(rk.poke);
  o[name] = q;
  jset(OWED, o);
}

function finalizeWeek(oldWeek) {
  const cfg = rewardsCfg();
  const pot = [cfg.r1, cfg.r2, cfg.r3];
  const scores = seasonScores();
  const lines = [];
  for (const [title, key, unit] of CATS) {
    const rows = Object.entries(scores)
      .map(([n, v]) => [n, v[key] ?? 0])
      .filter(([, v]) => v > 0)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3);
    if (rows.length === 0) continue;
    const medals = ["\u00a76#1", "\u00a77#2", "\u00a7c#3"];
    lines.push("\u00a7l" + title + "\u00a7r");
    rows.forEach(([n, v], i) => {
      if (i === 0) for (const t of top1Titles()) grantByName(n, t.id); // weekly #1 titles
      const rk = pot[i] ?? { coins: 0 };
      const has = (rk.coins > 0) || rk.item || rk.poke;
      if (has) payPrize(n, rk);
      lines.push(medals[i] + " \u00a7f" + n + " \u00a78- \u00a7b" + v + " " + unit + (has ? " \u00a76+" + rankLabel(rk) : ""));
    });
  }
  if (lines.length > 0) {
    world.sendMessage("\u00a7d\u00a7l=== SEASON WEEK " + oldWeek + " RESULTS ===\u00a7r\n" + lines.join("\n") + "\n\u00a7dNew season starts NOW - boards are reset. Good luck!");
    for (const p of world.getAllPlayers()) { try { p.playSound("random.levelup"); } catch {} }
  } else {
    world.sendMessage("\u00a7d[SEASON] New week - leaderboards reset!");
  }
}

function ensureSeason() {
  const w = weekNum();
  const st = jget(SEASON, "null");
  if (st && st.w === w) return;
  if (st) finalizeWeek(st.w); // pay out the week that just ended
  jset(BASE, lbData());       // new baseline = current lifetime totals
  jset(SEASON, { w });
}

export function initSeason() {
  system.runInterval(() => {
    try {
      if (world.getAllPlayers().length === 0) return;
      if (seasonEnabled()) ensureSeason(); // paused = no reset, no payout; owed delivery below still runs
      // deliver owed coins to whoever is online now
      const o = jget(OWED, "{}");
      let changed = false;
      for (const p of world.getAllPlayers()) {
        const q = o[p.name];
        if (!q) continue;
        if (q.coins > 0) addCoins(p, q.coins);
        for (const it of q.items ?? []) giveItem(p, it.id, it.qty);
        for (const pk of q.pokes ?? []) { suppressCatch(p.id); givePokemon(p, pk.dex, pk.lvl, { shiny: !!pk.shiny }); }
        p.sendMessage("\u00a7d[SEASON] \u00a7fYour prize from last week arrived: \u00a76" + rankLabel({ coins: q.coins, item: null, poke: null }) + ((q.items?.length || q.pokes?.length) ? " \u00a7f+ items/Pokemon (check inventory & team)!" : "\u00a7f!"));
        try { p.playSound("random.levelup"); } catch {}
        delete o[p.name];
        changed = true;
      }
      if (changed) jset(OWED, o);
    } catch {}
  }, 200);
}
