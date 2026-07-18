// pokedex.js - Pokedex progress + completion rewards for SunnyX
// SERP registers every seen/caught species as bits inside player tags
// "dex1/<bits>" .. "dex9/<bits>" (one tag per generation block). This module
// reads those bits, shows per-generation progress and pays milestone rewards.

import { SERP_N } from "./serpdex.js";
import { world, system } from "@minecraft/server";
import { themedRaw } from "./ui.js";
import { ActionFormData } from "@minecraft/server-ui";

const RW_KEY = "sx:dexrw"; // claimed milestone ids

// generation dex blocks used by SERP ($e function): tag "dex<g>" holds one
// bit per species; capacities taken from SERP's own default bit strings (V)
const GEN_TOTAL = [0, 151, 100, 135, 107, 156, 72, 88, 89, 105];
const GEN_LABEL = ["", "Gen 1 (Kanto)", "Gen 2 (Johto)", "Gen 3 (Hoenn)", "Gen 4 (Sinnoh)", "Gen 5 (Unova)", "Gen 6 (Kalos)", "Gen 7 (Alola)", "Gen 8 (Galar)", "Gen 9 (Paldea)"];

// [id, label, totalRegistered, moneyReward]
const MILESTONES = [
  ["m25", "Register 25 species", 25, 3000],
  ["m50", "Register 50 species", 50, 6000],
  ["m100", "Register 100 species", 100, 15000],
  ["m151", "Register 151 species", 151, 30000],
  ["m200", "Register 200 species", 200, 45000],
  ["m250", "Register 250 species", 250, 60000],
  ["m300", "Register 300 species", 300, 100000],
];

function genBits(player, gen) {
  const tag = player.getTags().find((t) => t.startsWith("dex" + gen + "/"));
  if (!tag) return null;
  return tag.split("/")[1] ?? "";
}

function genProgress(player, gen) {
  const total = GEN_TOTAL[gen];
  const bits = genBits(player, gen);
  if (!bits) return [0, total];
  let count = 0;
  for (let i = 0; i < bits.length; i++) if (bits[i] === "1") count++;
  return [count, total];
}

export function totalRegistered(player) {
  let sum = 0;
  for (let g = 1; g <= 9; g++) sum += genProgress(player, g)[0];
  return sum;
}

function jload(player, key, fb) {
  const raw = player.getDynamicProperty(key);
  if (typeof raw !== "string" || !raw) return fb;
  try {
    return JSON.parse(raw);
  } catch {
    return fb;
  }
}

export function openDex(player) {
  const total = totalRegistered(player);
  const claimed = jload(player, RW_KEY, []);
  let body = "Total registered: \u00a7a" + total + "\u00a7r species\n\n";
  for (let g = 1; g <= 9; g++) {
    const [c, t] = genProgress(player, g);
    if (c === 0 && g > 4) continue; // keep the list short until they get there
    const pct = Math.round((c / t) * 100);
    body += GEN_LABEL[g] + ": " + c + "/" + t + " (" + pct + "%)\n";
  }
  const form = themedRaw("serp.main.summary").body("\u00a7lPOKEDEX PROGRESS\u00a7r\n" + body);
  const claimable = [];
  for (const m of MILESTONES) {
    const [id, label, goal, money] = m;
    const got = claimed.includes(id);
    const done = total >= goal;
    claimable.push(m);
    form.button((got ? "\u00a78[DA NHAN] " : done ? "\u00a7a[NHAN " + money + "$] " : "") + label + "\n" + Math.min(total, goal) + "/" + goal);
  }
  form.button("Close");
  form.show(player).then((res) => {
    if (res.canceled || res.selection >= claimable.length) return;
    const [id, label, goal, money] = claimable[res.selection];
    const cur = jload(player, RW_KEY, []);
    if (cur.includes(id) || totalRegistered(player) < goal) return system.run(() => openDex(player));
    cur.push(id);
    player.setDynamicProperty(RW_KEY, JSON.stringify(cur));
    try {
      world.scoreboard.getObjective("money")?.addScore(player, money);
    } catch {}
    player.sendMessage("\u00a76[Pokedex] " + label + " - claim " + money + " money!");
    try {
      player.playSound("random.levelup");
    } catch {}
    system.run(() => openDex(player));
  });
}


// ---------- Full Pokedex browser ----------

import { SPECIES, EXPANSION, MOVES, MOVE_POOLS, LEARNSETS, EVOS, CATCH_RATE, DEX_EXTRAS, DEX_COLORS, MEGAS, BASE_EXP, MEGA_SPRITES, MEGA_DOUBLE } from "./speciesdata.js";

const GEN_RANGES = [[1,151],[152,251],[252,386],[387,493],[494,649],[650,721],[722,809],[810,905],[906,1025]];
const TYPE_NAMES = ["", "Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass", "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water"];
// SERP's own TM item names double as type labels ("Bug type", etc.)
const TYPE_LANG = TYPE_NAMES.map((_, t) => "item.serp:tm_" + t + ".name");
const STAT_SHORT6 = ["HP", "Atk", "Def", "SpA", "SpD", "Spe"];
function STAT_LINE(st) {
  let s = "";
  for (let i = 0; i < 6; i++) s += STAT_SHORT6[i] + " " + st[i] + (i === 2 ? "\n" : "  ");
  return s;
}
const EXDEX_KEY = "sx:exdex"; // caught expansion species ids

// Full national dex. SERP registers every species from #1 to #1025 (it has a
// name entry entity.pokemon:p<dex>.name for each), and the DX expansion species
// are a subset of that range (SERP names them, DX only adds the models). So the
// COMPLETE SERP+DX list is simply dex 1..1025 - we must NOT reduce it to the
// species that happen to be in our local SPECIES stat table (that was cutting
// the list from 1025 down to ~412).
const NATIONAL_MAX = 1025;
const ALL_DEX = Array.from({ length: NATIONAL_MAX }, (_, i) => i + 1);

export function recordExpansionCaught(player, dex) {
  try {
    const cur = jload(player, EXDEX_KEY, []);
    if (!cur.includes(dex)) {
      cur.push(dex);
      player.setDynamicProperty(EXDEX_KEY, JSON.stringify(cur));
    }
    setSerpDexBit(player, dex);
  } catch {}
}

// flip the bit in SERP's own dexN/ tag so their gen counters count it too
function setSerpDexBit(player, dex) {
  try {
    const g = genOf(dex);
    const prefix = "dex" + g + "/";
    const tag = player.getTags().find((t) => t.startsWith(prefix));
    if (!tag) return;
    const bits = tag.slice(prefix.length).split("");
    const idx = dex - GEN_RANGES[g - 1][0];
    if (idx < 0 || idx >= bits.length || bits[idx] === "1") return;
    bits[idx] = "1";
    player.removeTag(tag);
    player.addTag(prefix + bits.join(""));
  } catch {}
}

function genOf(dex) {
  for (let g = 1; g <= 9; g++) if (dex >= GEN_RANGES[g-1][0] && dex <= GEN_RANGES[g-1][1]) return g;
  return 1;
}

function isCaught(player, dex) {
  if (EXPANSION.has(dex)) return jload(player, EXDEX_KEY, []).includes(dex);
  const g = genOf(dex);
  const bits = genBits(player, g);
  if (!bits) return false;
  return bits[dex - GEN_RANGES[g-1][0]] === "1";
}

function dexColor(player) {
  try {
    const it = player.getComponent("inventory")?.container?.getItem(player.selectedSlotIndex);
    const c = it?.getComponent("dyeable")?.color;
    if (!c) return "red";
    let best = "red", bd = 999;
    for (const [name, [r, g, b]] of DEX_COLORS) {
      const d = Math.sqrt((r - c.red) ** 2 + (g - c.green) ** 2 + (b - c.blue) ** 2);
      if (d < bd) { bd = d; best = name; }
    }
    return best;
  } catch {
    return "red";
  }
}

const TYPE_ICON = (t) => "pokedrock/type/" + t;

function openAttackDex(player, color) {
  const form = themedRaw("serp.main.pokedex_" + color).body("ATTACKDEX\n");
  const types = Object.keys(MOVE_POOLS).map(Number).sort((a, b) => a - b);
  for (const t of types) form.button(TYPE_NAMES[t] + "\n" + MOVE_POOLS[t].length + " moves", TYPE_ICON(t));
  form.show(player).then((res) => {
    if (res.canceled || res.selection >= types.length) return openFullDex(player);
    const t = types[res.selection];
    const mform = themedRaw("serp.main.pokedex_" + color).body(TYPE_NAMES[t] + " moves\n");
    const ids = MOVE_POOLS[t];
    for (const mid of ids) {
      const mv = MOVES[mid];
      mform.button({
        rawtext: [{ translate: "attack." + mid }, { text: "\nPow " + mv[2] + " / Acc " + mv[1] + " / PP " + mv[3] + " / " + (mv[4] === 2 ? "Special" : "Physical") }],
      }, TYPE_ICON(t));
    }
    mform.show(player).then(() => openAttackDex(player, color));
  });
}

const NATURES = [["Hardy",0,0],["Lonely",1,2],["Brave",1,5],["Adamant",1,3],["Naughty",1,4],["Bold",2,1],["Docile",0,0],["Relaxed",2,5],["Impish",2,3],["Lax",2,4],["Timid",5,1],["Hasty",5,2],["Serious",0,0],["Jolly",5,3],["Naive",5,4],["Modest",3,1],["Mild",3,2],["Quiet",3,5],["Bashful",0,0],["Rash",3,4],["Calm",4,1],["Gentle",4,2],["Sassy",4,5],["Careful",4,3],["Quirky",0,0]];
const STAT_SHORT = ["HP", "Atk", "Def", "SpA", "SpD", "Spe"];

function openNatureDex(player, color) {
  let body = "NATUREDEX\n\n";
  for (let i = 0; i < NATURES.length; i++) {
    const [n, up, dn] = NATURES[i];
    body += (i + 1) + ". " + n + (up ? "  \u00a7a+" + STAT_SHORT[up] + " \u00a7c-" + STAT_SHORT[dn] + "\u00a7r" : "  \u00a77neutral\u00a7r") + "\n";
  }
  themedRaw("serp.main.pokedex_data_" + color).body(body).button("Back").show(player).then(() => openFullDex(player));
}

export function openFullDex(player) {
  // Gen picker exactly like SERP's pokedexRoot: serp.genN labels, [caught/total],
  // gen icons, red dex frame - then the full species list per gen.
  const color = dexColor(player);
  const form = themedRaw("serp.main.pokedex_" + color);
  form.body("");
  const gens = [];
  for (let g = 1; g <= 9; g++) {
    const [lo, hi] = GEN_RANGES[g - 1];
    const ids = ALL_DEX.filter((d) => d >= lo && d <= hi);
    if (ids.length === 0) continue;
    const caught = ids.filter((d) => isCaught(player, d)).length;
    gens.push(ids);
    form.button({
      rawtext: [{ translate: "serp.gen" + g }, { text: "\n[" + caught + "/" + ids.length + "]" }],
    }, "pokedrock/generations/" + g + "_generation");
  }
  form.button({ rawtext: [{ translate: "serp.attackdex" }] }, "pokedrock/items/tm_13");
  form.button({ rawtext: [{ translate: "serp.naturedex" }] }, "textures/ui/naturedex");
  form.button({ rawtext: [{ translate: "menu.howToPlay" }] }, "textures/ui/playdex");
  form.show(player).then((res) => {
    if (res.canceled) return;
    if (res.selection < gens.length) return genPage(player, res.selection + 1, gens[res.selection], color);
    if (res.selection === gens.length) return openAttackDex(player, color);
    if (res.selection === gens.length + 1) return openNatureDex(player, color);
    if (res.selection === gens.length + 2)
      themedRaw("serp.main.pokedex_" + color)
        .body("\u00a7lHOW TO PLAY\u00a7r\n\nTap a generation to browse its species. Green entries are caught, grey are still missing. Catch Pokemon to fill the dex.\n\nAttackDex: browse moves. NatureDex: browse natures.")
        .button("Back")
        .show(player)
        .then((r) => (r.canceled ? undefined : openFullDex(player)));
  });
}

function genPage(player, g, ids, color = "red") {
  const form = themedRaw("serp.main.pokedex_" + color);
  form.body({ rawtext: [{ translate: "serp.gen" + g }, { text: " [" + ids[0] + "-" + ids[ids.length - 1] + "]\n" }] });
  for (const d of ids) {
    const got = isCaught(player, d);
    // SERP format: '§z' (registered colour) or '§8' (dim/unseen) + '\n[NNNN]. ' + name,
    // sprite icon pokedrock/pokedex/<dex>. Matches pokedexPage in SERP exactly.
    form.button({
      rawtext: [
        { text: (got ? "\u00a7z" : "\u00a78") + "\n[" + String(d).padStart(4, "0") + "]. " },
        { translate: "entity.pokemon:p" + d + ".name" },
      ],
    }, "pokedrock/pokedex/" + d);
  }
  form.show(player).then((res) => {
    if (res.canceled || res.selection >= ids.length) return openFullDex(player);
    dexDetail(player, g, ids, ids[res.selection], color);
  });
}

function dexDetail(player, g, ids, dex, color = "red", form_ = 0) {
  // sp may be undefined for the many SERP species we don't carry a local stat
  // block for - the list still shows them (name + sprite + caught), and here we
  // simply omit the stat/type detail rather than crashing.
  const sp = SPECIES[dex];
  const st = sp ? sp[3] : null;
  const ex = DEX_EXTRAS[dex];
  const megaSet = new Set(MEGA_SPRITES);
  const doubleSet = new Set(MEGA_DOUBLE);
  const hasMegaSprite = megaSet.has(dex);
  const maxForm = doubleSet.has(dex) ? 2 : hasMegaSprite ? 1 : 0;
  const showingMega = form_ > 0 && form_ <= maxForm;

  // ---- body, in SERP's pokedexData order. Prefer the exact SERP data table
  // (SERP_N, extracted from SERP itself) so the 336 species SERP carries detail
  // for render identically; fall back to our local tables for the rest.
  const nd = SERP_N[dex];
  const parts = [
    { text: "\u00a7l" + String(dex).padStart(4, "0") + "\u18d0 " },
    { translate: "entity.pokemon:p" + dex + ".name" },
    { text: (showingMega ? " \u00a7d(Mega)\u00a7r" : "") + "\n" },
  ];
  if (nd) {
    // getting / biomes  (serp.getting serp.biome_<b0> <b1> serp.biome_<b2>)
    parts.push({ translate: "serp.getting" });
    if (nd.b[0]) parts.push({ translate: "serp.biome_" + nd.b[0] });
    if (nd.b[1]) parts.push({ text: nd.b[1] });
    if (nd.b[2] && nd.b[2] !== "empty") parts.push({ translate: "serp.biome_" + nd.b[2] });
    parts.push({ text: "\n" });
    // mount
    parts.push({ translate: "serp.mount" }, { translate: "mount." + nd.mt }, { text: "\n" });
    // weight / height
    parts.push({ translate: "serp.weight" }, { text: nd.wt + " " });
    parts.push({ translate: "serp.height" }, { text: nd.ht + " " });
    // wild level from SERP's base-exp curve
    const wl = Math.round((100 * nd.ex) / (10 - 0.013 * nd.ex) / 100);
    parts.push({ translate: "serp.wildlevel" }, { text: wl + "\n" });
    // types
    parts.push({ translate: TYPE_LANG[nd.ty[0]] });
    if (nd.ty[1]) parts.push({ text: " / " }, { translate: TYPE_LANG[nd.ty[1]] });
    parts.push({ text: "\n" });
    // base stats + BST
    parts.push({ text: "\n" + STAT_LINE(nd.st) + "\n" });
    parts.push({ text: "BST " + nd.st.reduce((a, b) => a + b, 0) + "\n" });
  } else {
    if (ex) {
      parts.push({ translate: "serp.getting" });
      if (ex[2]) parts.push({ translate: "serp.biome_" + ex[2] });
      if (ex[3]) parts.push({ text: ex[3] });
      if (ex[4] && ex[4] !== "empty") parts.push({ translate: "serp.biome_" + ex[4] });
      parts.push({ text: "\n" });
      parts.push({ translate: "serp.weight" }, { text: ex[0] + " " });
      parts.push({ translate: "serp.height" }, { text: ex[1] + " " });
    }
    const be = BASE_EXP[dex] ?? 80;
    const wl = Math.round((100 * be) / (10 - 0.013 * be) / 100);
    parts.push({ translate: "serp.wildlevel" }, { text: wl + "\n" });
    if (sp) {
      parts.push({ translate: TYPE_LANG[sp[4]] });
      if (sp[5]) parts.push({ text: " / " }, { translate: TYPE_LANG[sp[5]] });
      parts.push({ text: "\n" });
      parts.push({ text: "\n" + STAT_LINE(st) + "\n" });
      parts.push({ text: "BST " + st.reduce((a, b) => a + b, 0) + "\n" });
    }
  }
  parts.push({ text: (isCaught(player, dex) ? "\u00a7aCaught \u2714\u00a7r" : "\u00a78Not caught\u00a7r") + "\n" });
  // SERP shows the species' Pokedex flavour text (info.<dex>, provided by SERP's
  // own lang) once the entry is registered - include it for parity.
  if (isCaught(player, dex)) parts.push({ text: "\n" }, { translate: "info." + dex }, { text: "\n" });
  // evolutions
  const evo = EVOS[dex];
  if (evo && evo.length) {
    parts.push({ translate: "serp.evolutions" }, { text: "\n" });
    for (const [tgt, lv] of evo) {
      parts.push({ translate: "entity.pokemon:p" + tgt + ".name" }, { text: " Lv." + lv + "\n" });
    }
  }
  // learnset (TMs / level moves)
  const ls = LEARNSETS[dex];
  if (ls && ls.length) {
    parts.push({ translate: "serp.tms" }, { text: "\n" });
    for (const [lv, mid] of ls.slice(0, 8)) {
      if (!MOVES[mid]) continue;
      parts.push({ text: "\u00a7lL" + lv + ":\u00a7r " }, { translate: "attack." + mid }, { text: "\n" });
    }
  }
  parts.push({ text: EXPANSION.has(dex) ? "\n\u00a77Sunny DX species\u00a7r" : "" });

  const spriteRef = showingMega ? dex + (form_ === 2 ? "_m2" : "_m") : "" + dex;
  const form = themedRaw("serp.main.pokedex_data_" + color).body({ rawtext: parts });
  // Button row identical to SERP: Growl, Radar, [Subspecie if mega], Sprite, Type x1-2
  form.button({ rawtext: [{ translate: "serp.growl" }] }, "textures/ui/buttons/growl");
  form.button({ rawtext: [{ translate: "serp.radar" }] }, "textures/ui/buttons/radar");
  const hasSub = maxForm > 0;
  if (hasSub) form.button({ rawtext: [{ text: showingMega ? (form_ === 2 ? "Base form" : (maxForm === 2 ? "Mega Y" : "Base form")) : "Mega form" }] }, "textures/ui/buttons/subspecie");
  form.button({ rawtext: [{ text: "Sprite" }] }, "pokedrock/pokedex/" + spriteRef);
  if (sp) {
    form.button({ rawtext: [{ translate: TYPE_LANG[sp[4]] }] }, "pokedrock/type/" + sp[4]);
    if (sp[5]) form.button({ rawtext: [{ translate: TYPE_LANG[sp[5]] }] }, "pokedrock/type/" + sp[5]);
  }
  form.show(player).then((res) => {
    if (res.canceled) return genPage(player, g, ids, color);
    let idx = res.selection;
    if (idx === 0) {
      try { player.playSound("mob.pokemon." + dex); } catch {}
      return dexDetail(player, g, ids, dex, color, form_);
    }
    if (idx === 1) {
      try {
        player.setDynamicProperty("searching", dex);
        player.sendMessage({ rawtext: [{ translate: "serp.pokeradar" }, { translate: "entity.pokemon:p" + dex + ".name" }] });
      } catch {}
      return dexDetail(player, g, ids, dex, color, form_);
    }
    // subspecie cycles base -> mega -> (mega Y) -> base
    if (hasSub && idx === 2) {
      const next = form_ + 1 > maxForm ? 0 : form_ + 1;
      return dexDetail(player, g, ids, dex, color, next);
    }
    // sprite / type buttons are non-interactive -> just refresh
    return dexDetail(player, g, ids, dex, color, form_);
  });
}
