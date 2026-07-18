// Sunny Pokedrock v4.8.0 - SERP Pokedrock enhancement suite (standalone, runs alongside SERP)
// Direct interactions over menus:
//   Sneak + tap SERP PC Box        -> Advanced PC (search/filter/favorite)
//   Sneak + tap SERP Trade Machine -> Trade hub (direct / Wonder Trade / GTS / Auction)
// The item menu only keeps what has no natural machine to attach to.
// Zero commands - pure Script API everywhere.

import { world, system, ItemStack } from "@minecraft/server";
import { themedForm, themedRaw } from "./ui.js";
import { ActionFormData, uiManager } from "@minecraft/server-ui";
import * as S from "./serpdata.js";
import { openDex, openFullDex } from "./pokedex.js";
import { openAdmin, isAdmin } from "./admin.js";
import { initItemTools } from "./itemtools.js";
import { initPerks } from "./perks.js";
import { initNpcGuard } from "./npcguard.js";

const ITEM_ID = "sunnyx:daycare";

const HELP_TEXT =
  "SUNHUB MENU ITEM:\n" +
  "- Use in the air -> the Hub (bank, daily, quests, kits, travel, heal, Guide).\n" +
  "- Tap a Pokemon -> info card (gender, shiny, HP, ~level, types, stats, catch rate).\n" +
  "- Tap a player -> trade (pick, they answer, you confirm - lossless: moves/IVs/shiny/nickname all travel).\n\n" +
  "RADAR ITEM: scans wild Pokemon within 80 blocks, with distance and direction.\n\n" +
  "PROFESSOR NPC (sneak + tap): Profile -> My Team, Full Pokedex, Rewards, Stats, Guide, Admin.\n\n" +
  "Full how-to for every feature: SunHub Menu -> Guide.";

function hasItem(player, id) {
  const inv = player.getComponent("inventory")?.container;
  if (!inv) return false;
  for (let i = 0; i < inv.size; i++) {
    if (inv.getItem(i)?.typeId === id) return true;
  }
  return false;
}

const NATURE_NAMES = ["", "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed", "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky"];

function openTeam(player) {
  const party = S.getParty(player);
  const form = themedRaw("serp.main.summary").body("\u00a7lMY TEAM\u00a7r\n" + party.length + "/6 Pokemon\n(Summoning and held items: use the Pokedex normally - SERP screen)");
  for (const p of party) {
    const lvl = S.level(p.fields);
    const hpLost = Number(p.fields[5]);
    form.button({
      rawtext: [S.displayName(p.fields), { text: " Lv." + lvl + (Number(p.fields[3]) > 2 ? " \u00a76\u2605" : "") + (hpLost > 0 ? " \u00a7c(-" + hpLost + " HP)" : "") }],
    }, "pokedrock/pokedex/" + p.fields[2]);
  }
  form.button("Back");
  form.show(player).then((res) => {
    if (res.canceled || res.selection >= party.length) return res?.selection === party.length ? openProfile(player) : undefined;
    const p = party[res.selection];
    const f = p.fields;
    const ivs = f[19].split("%");
    const moves = f[14].split("%").map(Number).filter(Boolean);
    themedRaw("serp.main.summary")
      .body({
        rawtext: [
          { text: "\u00a7l" }, S.displayName(f),
          { text: "\u00a7r Lv." + S.level(f) + "\n\nNature: " + (NATURE_NAMES[Number(f[9])] ?? f[9]) +
              "\nFriendship: " + f[7] + "/255" +
              "\nIVs: " + ivs.join("/") +
              "\nHP lost: " + f[5] + "  Status: " + f[6] +
              "\nMoves: " },
          ...moves.flatMap((mid, i) => [{ translate: "attack." + mid }, { text: i < moves.length - 1 ? ", " : "" }]),
        ],
      })
      .button("Back")
      .show(player)
      .then(() => openTeam(player));
  });
}

function openProfile(player) {
  const admin = isAdmin(player);
  const form = themedRaw("serp.main.summary")
    .body("\u00a7lYOUR PROFILE\u00a7r")
    .button("My Team")
    .button("Full Pokedex (all species)")
    .button("Pokedex Rewards")
    .button("Stats")
    .button("Guide");
  if (admin) form.button("Admin (OP)");
  form.show(player).then((res) => {
    if (res.canceled) return;
    if (res.selection === 0) openTeam(player);
    else if (res.selection === 1) openFullDex(player);
    else if (res.selection === 2) openDex(player);
    else if (res.selection === 3) openStats(player);
    else if (res.selection === 4)
      themedForm("summary", "Sunny Pokedrock Guide").body(HELP_TEXT).button("Close").show(player).then(() => {});
    else if (admin && res.selection === 5) openAdmin(player);
  });
}

function openStats(player) {
  let st = {};
  try {
    st = JSON.parse(player.getDynamicProperty("sx:stats") ?? "{}");
  } catch {}
  const body =
    "Caught: " + (st.catch ?? 0) + " (shiny " + (st.shiny ?? 0) + ")\n" +
    "Defeated: " + (st.defeat ?? 0) + "\n" +
    "Alpha: " + (st.alpha ?? 0) + "  Raid: " + (st.raid ?? 0) + "\n" +
    "Distance walked: " + (st.walk ?? 0) + " blocks";
  themedRaw("serp.main.summary").body("\u00a7lYOUR STATS\u00a7r\n" + body).button("Close").show(player).then(() => {});
}


// start screen with the full dex on the Pokedex button; sneak = SERP original).

// ---------- direct machine interactions ----------

world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
  try {
    const { player, target } = ev;
    if (!player || !target) return;
    if (!player.isSneaking) return;
    if (target.typeId === "serp:profs") {
      ev.cancel = true;
      system.run(() => openProfile(player));
    }
  } catch {}
});

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  try {
    if (!initialSpawn || !player) return;
  } catch {}
});

// start screen with team Pokemon in the slots; sneak = SERP original). Do NOT
// also open the Profile here - that would replace the start screen. Profile is
// reached by sneak-interacting the Professor NPC.

// One faulty module must never take down the whole pack again.
for (const init of [initNpcGuard, initItemTools, initPerks]) {
  try {
    init();
  } catch (e) {
    console.warn("SunnyPokedrock init failed: " + (e && e.message));
  }
}

// DX systems (boss / wild spawner / raid / DX battle / DX capture) are gone -
// all Pokemon behaviour is left entirely to SERP. This pack now only adds the
// Hub, the Radar item, the Hub item and the companion perks.
