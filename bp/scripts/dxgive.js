// dxgive.js - Grant a DX (Sunny Pokedrock DX) Pokemon to a player.
//
// SERP's own `serp:getpokemon` command only knows native SERP species, so DX
// dex IDs silently fail there. DX Pokemon are stored the same way every SERP
// Pokemon is: a player tag of 22 "/"-joined fields. We build a fresh legal
// tag for the requested species into the first free team slot (or the PC "stg"
// storage if the team is full), so the DX Pokemon shows up in the team exactly
// like a caught one. No runCommand needed.

import { world } from "@minecraft/server";
import { DX_SPECIES } from "./data_species.js";
import { SERP_N } from "./serpdex.js";

// The 4 level-up moves a species knows at a given level (last 4 learned).
function movesFor(dex, level) {
  const lm = (SERP_N[dex] && SERP_N[dex].lm) || [];
  const learned = lm.filter((p) => p[0] <= level).map((p) => p[1]);
  const last4 = learned.slice(-4);
  while (last4.length < 4) last4.push(0);
  return last4;
}

export function isDXSpecies(dex) {
  return DX_SPECIES.has(Number(dex));
}

function partyTags(player) {
  return player.getTags().filter((t) => /^team[1-6]\//.test(t));
}
function usedSlots(player) {
  const s = new Set();
  for (const t of partyTags(player)) s.add(Number(t[4]));
  return s;
}
function stgCount(player) {
  return player.getTags().filter((t) => t.startsWith("stg")).length;
}

// A minimal but complete legal SERP Pokemon record.
function buildTag(dex, slotField, level, opts = {}) {
  const exp = Math.max(100, (level | 0) * 100);
  const f = new Array(22).fill("0");
  f[0] = slotField;          // team1-6 or stg<id>
  f[1] = "1";                // ball = pokeball
  f[2] = String(dex);        // dex number
  f[3] = (opts.shiny || Math.random() < 1 / 512) ? "4" : "1"; // variant (>2 = shiny)
  f[4] = "0";                // form
  f[5] = "0";                // HP lost
  f[6] = "0";                // status
  f[7] = "70";               // friendship
  f[8] = String(exp);        // exp (level = floor/100)
  f[9] = String(1 + Math.floor(Math.random() * 25)); // nature 1-25
  f[10] = "0";               // ability
  f[11] = "0";               // held item
  f[12] = "0";
  f[13] = "0";               // dynamax candy
  const mv = movesFor(dex, level | 0);
  f[14] = mv.join("%");      // moves from the species' level-up learnset
  // PP = each move's default (SERP tops it up); set a sane non-zero value.
  f[15] = "0%0%0%0";         // PP used (none)
  f[16] = "1%1%1%1";         // PP ups
  f[17] = String(Math.floor(Math.random() * 1000000)); // seed
  f[18] = "0";               // size
  // IVs h%a%d%sa%sd%s
  const iv = () => Math.floor(Math.random() * 32);
  f[19] = [iv(), iv(), iv(), iv(), iv(), iv()].join("%");
  f[20] = "0%0%0%0%0%0";     // EVs
  f[21] = "0";               // nickname (0 = none)
  return f.join("/");
}

// Also flip the SERP dex bit so the DX species registers as "seen/caught".
const GEN_STARTS = [1, 152, 252, 387, 494, 650, 722, 810, 906];
function markDex(player, dex) {
  let gen = 1;
  for (let g = 8; g >= 0; g--) {
    if (dex >= GEN_STARTS[g]) { gen = g + 1; break; }
  }
  const start = GEN_STARTS[gen - 1];
  const bit = dex - start;
  const key = "dex" + gen;
  const tag = player.getTags().find((t) => t.startsWith(key + "/"));
  let bits = tag ? tag.split("/")[1].split("") : [];
  while (bits.length <= bit) bits.push("0");
  if (bits[bit] === "1") return; // already recorded
  bits[bit] = "1";
  if (tag) player.removeTag(tag);
  player.addTag(key + "/" + bits.join(""));
}

// Grant the DX Pokemon. Returns { ok, where } where where is "team"|"pc".
export function giveDXPokemon(player, dex, level = 5) {
  dex = Number(dex);
  if (!isDXSpecies(dex)) return { ok: false, reason: "not a DX species" };
  return givePokemon(player, dex, level);
}

// Grant ANY Pokemon (native SERP species or DX) at a chosen level by writing a
// legal team tag directly - same mechanism SERP uses, so it shows up in the team
// like a caught one and SERP fills real moves on first summon. This gives us
// full level control that serp:getpokemon does not offer.
export function givePokemon(player, dex, level = 5, opts = {}) {
  dex = Number(dex);
  const used = usedSlots(player);
  let slotField = null;
  let where = "team";
  for (let s = 1; s <= 6; s++) {
    if (!used.has(s)) { slotField = "team" + s; break; }
  }
  if (!slotField) {
    if (stgCount(player) >= 250) return { ok: false, reason: "team and PC are full" };
    slotField = "stg" + (100000 + Math.floor(Math.random() * 899999));
    where = "pc";
  }
  try {
    player.addTag(buildTag(dex, slotField, level, opts));
    markDex(player, dex);
    return { ok: true, where };
  } catch (e) {
    return { ok: false, reason: "tag write failed" };
  }
}
