// serpdata.js - SERP Pokedrock data layer API (schema reverse-engineered from v1.32.5)
// Pokemon data lives as PLAYER TAGS: "/"-joined array of 22 fields.
// Party slots: tag starts with "team1".."team6". PC storage: tag starts with "stg<100000-999999>" (max 250).
//
// Field schema:
//  [0]  slot key        team{1-6} | stg{n}
//  [1]  pokeball type   1-28
//  [2]  species dex id
//  [3]  variant         1-2 normal, +3 = shiny (4-5)
//  [4]  subspecies/form (ssp)
//  [5]  damage taken (HP lost)
//  [6]  status condition (0 = healthy)
//  [7]  friendship 0-255
//  [8]  exp (level = floor(exp/100))
//  [9]  nature 1-25
//  [10] ability index
//  [11] held item index (0 = none)
//  [12] reserved (0)
//  [13] dynamax candy level 0-10
//  [14] moves        "a%b%c%d" (0 = empty)
//  [15] PP used      "0%0%0%0"
//  [16] PP ups       "1%1%1%1"
//  [17] seed 1-18
//  [18] size         xs|s|m|l|xl
//  [19] IVs          "h%a%d%sa%sd%s" each 1-31
//  [20] EVs          "0%0%0%0%0%0" (max 510)
//  [21] nickname     "0" = none

export const SIZES = ["xs", "s", "m", "l", "xl"];

export function rand(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function decode(tag) {
  const f = tag.split("/");
  if (f.length < 22) return null;
  return f;
}

export function encode(fields) {
  return fields.join("/");
}

export function isPartyTag(tag) {
  return tag.startsWith("team");
}

export function isPCTag(tag) {
  return tag.startsWith("stg");
}

// Returns [{slot, tag, fields}] for the player's party (up to 6)
export function getParty(player) {
  const out = [];
  for (const tag of player.getTags()) {
    if (!isPartyTag(tag)) continue;
    const fields = decode(tag);
    if (!fields) continue;
    out.push({ slot: Number(tag[4]), tag, fields });
  }
  out.sort((a, b) => a.slot - b.slot);
  return out;
}

// Returns [{tag, fields}] for the player's PC storage
export function getPC(player) {
  const out = [];
  for (const tag of player.getTags()) {
    if (!isPCTag(tag)) continue;
    const fields = decode(tag);
    if (!fields) continue;
    out.push({ tag, fields });
  }
  return out;
}

export function level(fields) {
  return Math.floor(Number(fields[8]) / 100);
}

export function isShiny(fields) {
  return Number(fields[3]) > 2;
}

export function displayName(fields) {
  if (fields[21] && fields[21] !== "0") return { text: "\u00a7z" + fields[21] };
  return { translate: "entity.pokemon:p" + fields[2] + ".name" };
}

// Removes a pokemon (by exact tag) from the player. Returns fields or null.
export function takePokemon(player, tag) {
  if (!player.hasTag(tag)) return null;
  const fields = decode(tag);
  if (!fields) return null;
  player.removeTag(tag);
  return fields;
}

// Gives a pokemon to the player: first free party slot, else PC (max 250).
// Returns "team" | "pc" | null (both full). Mirrors SERP internal xe().
export function givePokemon(player, fields) {
  const tags = player.getTags();
  const teamCount = tags.filter((t) => t.startsWith("team")).length;
  if (teamCount < 6) {
    for (let s = 1; s <= 6; s++) {
      const key = "team" + s;
      if (!tags.some((t) => t.startsWith(key))) {
        fields[0] = key;
        player.addTag(encode(fields));
        return "team";
      }
    }
  }
  if (tags.filter((t) => t.startsWith("stg")).length < 250) {
    fields[0] = "stg" + rand(100000, 999999);
    player.addTag(encode(fields));
    return "pc";
  }
  return null;
}

// Authentic stat formula used by SERP (matches internal fe()).
// statIndex: 0=HP 1=Atk 2=Def 3=SpA 4=SpD 5=Spe
// NATURE_MOD[nature] = [raisedStat, loweredStat] (1-5), [] = neutral
export const NATURE_MOD = [
  [], [5, 2], [4, 2], [2, 4], [3, 5], [2, 5], [3, 2], [1, 5], [], [5, 1],
  [], [2, 3], [4, 3], [4, 5], [3, 1], [4, 1], [5, 3], [], [1, 2], [2, 1],
  [], [3, 4], [1, 3], [1, 4], [5, 4], [],
];

export function calcStat(statIndex, base, lvl, nature, stage, iv, ev) {
  if (statIndex < 1) {
    return Math.floor(((2 * base + (iv ?? 0) + (ev ?? 0) / 4) * lvl) / 100 + lvl + 10);
  }
  let mod = 1;
  const nm = NATURE_MOD[nature] ?? [];
  if (statIndex === nm[0]) mod = 1.1;
  else if (statIndex === nm[1]) mod = 0.9;
  let v = (((2 * base + (iv ?? 0) + (ev ?? 0) / 4) * lvl) / 100 + 5) * mod;
  if (stage > 0) v = (v / 5) * (stage + 5);
  else if (stage < 0) v = (v / (5 - stage)) * 5;
  return Math.floor(v);
}

// Builds a fresh 22-field pokemon data array.
export function makePokemon(dex, lvl, opts = {}) {
  const shiny = opts.shiny ?? false;
  const baseVariant = opts.variant ?? rand(1, 2);
  const moves = opts.moves ?? [0, 0, 0, 0];
  const m4 = [moves[0] ?? 0, moves[1] ?? 0, moves[2] ?? 0, moves[3] ?? 0];
  const ivs = opts.ivs ?? [rand(1, 31), rand(1, 31), rand(1, 31), rand(1, 31), rand(1, 31), rand(1, 31)];
  return [
    "built",
    String(opts.ball ?? rand(1, 28)),
    String(dex),
    String(shiny ? baseVariant + 3 : baseVariant),
    String(opts.ssp ?? 0),
    "0",
    "0",
    String(opts.friendship ?? 70),
    String(lvl * 100),
    String(opts.nature ?? rand(1, 25)),
    String(opts.ability ?? 0),
    "0",
    "0",
    "0",
    m4.join("%"),
    "0%0%0%0",
    "1%1%1%1",
    String(rand(1, 18)),
    opts.size ?? SIZES[rand(0, SIZES.length - 1)],
    ivs.join("%"),
    "0%0%0%0%0%0",
    opts.nickname ?? "0",
  ];
}

// ---------- Badges (SERP stores them as a player tag "badges/<18-bit string>") ----------

const BADGE_DEFAULT = "000000000000000000";

export function getBadges(player) {
  const tag = player.getTags().find((t) => t.startsWith("badges/"));
  if (!tag) return BADGE_DEFAULT.split("");
  const bits = (tag.split("/")[1] ?? BADGE_DEFAULT).split("");
  while (bits.length < 18) bits.push("0");
  return bits;
}

export function hasBadge(player, id) {
  return getBadges(player)[id - 1] === "1";
}

export function badgeCount(player) {
  return getBadges(player).filter((b) => b === "1").length;
}

export function giveBadge(player, id) {
  if (id < 1 || id > 18) return false;
  const bits = getBadges(player);
  if (bits[id - 1] === "1") return false;
  bits[id - 1] = "1";
  const old = player.getTags().find((t) => t.startsWith("badges/"));
  if (old) player.removeTag(old);
  player.addTag("badges/" + bits.join(""));
  return true;
}
