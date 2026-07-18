// ============================================================
// SunHub v1.4.0 - ALL-IN-ONE cho SERP Pokedrock
// Gop: SunnyUI + SunnyEco + SunnyDex + Essentials-style
// 1 item menu duy nhat, du lieu cu (coin/home/friendship) giu nguyen
// ============================================================
import { world, system, ItemStack, EntityComponentTypes } from "@minecraft/server";
import { CONFIG } from "./config.js";
// Display name shown as the hub title (mockup uses the server name up top).
// Change this one line to rebrand the hub. Falls back to "SunHub".
const SERVER_NAME = (CONFIG && CONFIG.serverName) ? CONFIG.serverName : "SunHub";
import { isAdmin, actionMenu, defer } from "./forms.js";
import { getCoins, fmt } from "./economy.js";
import { registerEarning } from "./earn.js";
import { registerQuestTracking, getQuestView, claimQuest } from "./quests.js";
import { dailyStatus, claimDaily } from "./daily.js";
import { openBank } from "./bank.js";
import { redeemGift } from "./gift.js";
import { openKits } from "./kits.js";
import { openNavigator, openAdminTP, registerNav } from "./nav.js";
import { registerCare } from "./care.js";
import { registerRoll } from "./roll.js";
import { registerInteract } from "./interact.js";
import { healPartyNearby, healCooldownLeft, markHealUsed } from "./partyheal.js";
import { openSerpAdmin } from "./serpadmin.js";
import { openEcoAdmin } from "./ecoadmin.js";
import { openAdminMode, registerAdminMode } from "./adminmode.js";
import { registerVillage } from "./village.js";
import { openAnnounces, adminAnnounces, hasUnread } from "./announce.js";
import { lbData } from "./tracker.js";
import { activeGift, giftClaimed, claimMysteryGift, openEventsAdmin } from "./events.js";
import { seasonScores, weekNum, daysLeft, rewardsCfg, rankLabel, CATS, seasonEnabled } from "./season.js";
import { openTitles, openTitlesAdmin, titlePrefix } from "./titles.js";
import { openJobs, maxJobLevel } from "./jobs.js";
import { t, getLang, toggleLang, langName } from "./i18n.js";
import { openClaims, openClaimsAdmin, openMachinePurge, openGuardCapAdmin } from "./claims.js";
import { platesOn, setPlates } from "./nameplates.js";
import { isLow, setLow } from "./perf.js";
import { openBuddy } from "./buddy.js";
import { openVehAdmin } from "./vehicles.js";
import { openMachineAnchors, machineCount } from "./machines.js";

registerEarning();
registerQuestTracking();
registerCare();
registerRoll();
registerNav();
registerInteract();
registerAdminMode();
registerVillage();

function doHealParty(player) {
    const left = healCooldownLeft(player);
    if (left > 0) {
        player.sendMessage(`§e[SunHub] Healing over time, wait §f${left}s§e.`);
        return;
    }
    const healed = healPartyNearby(player);
    if (healed === 0) {
        player.sendMessage("§e[SunHub] None of your Pokemon nearby need healing.");
        return;
    }
    markHealUsed(player);
    player.playSound("random.levelup");
    player.sendMessage(`§a[SunHub] hoi mau §f${healed}§a Pokemon!`);
}

async function openQuests(player) {
    const view = getQuestView(player);
    const sel = await actionMenu(player, "Quests Today", "3 quests refreshed daily:",
        view.map((v) => {
            let status = v.claimed ? "§8[Claimed]" : v.done ? "§a[CLAIM]" : "§e[" + v.progress + "/" + v.quest.count + "]";
            let rw = "§6" + fmt(v.quest.reward.coins);
            if (v.quest.reward.item) rw += " + " + v.quest.reward.item.qty + "x " + v.quest.reward.item.name;
            return { label: v.quest.desc + "\n" + status + " §r| " + rw };
        }), "pokedex_green");
    if (sel < 0) return;
    const v = view[sel];
    if (v.claimed) player.sendMessage("§e[SunHub] Claimed thuong already.");
    else if (v.done) {
        const msg = claimQuest(player, sel);
        if (msg) player.sendMessage("§a[SunHub] Claim reward: " + msg);
    } else player.sendMessage("§e[SunHub] Not done yet: " + v.progress + "/" + v.quest.count);
    return openQuests(player);
}

async function openAdmin(admin) {
    const sel = await actionMenu(admin, "Server Admin", "Select a tool group:", [
        { label: "Items & Pokemon\n§8Items, starter, badges, reset", icon: "textures/items/experience_bottle" },
        { label: "Manage Money\n§8View / add-remove money, bank, gift codes", icon: "textures/items/emerald" },
        { label: "Teleport Admin\n§8Go to player, pull them, view home", icon: "textures/items/ender_pearl" },
        { label: "Admin Mode\n§8Fly, invulnerable, invisible", icon: "textures/items/elytra" },
        { label: "Manage Announcements\n§8Write / delete server-wide announcements", icon: "textures/items/book_writable" },
        { label: "Grant / revoke admin\n§8For Realms - give SunHub admin to a member", icon: "textures/items/name_tag" },
        { label: "Events & Gifts\n§8x2 events, Mystery Gift", icon: "textures/items/cake" },
        { label: "Titles\n§8Create name titles + auto-grant rules", icon: "textures/items/name_tag" },
        { label: "Land Claims\n§8View / teleport / delete any claim", icon: "textures/blocks/grass_side_carried" },
        { label: "Purge machines\n§8Delete stray SERP machines (PC, healer...)", icon: "textures/blocks/barrier" },
        { label: "Guard limit\n§8Fewer guards = more wild Pokemon", icon: "textures/items/carrot_on_a_stick" },
        { label: "Reset Vehicles\n§8Fix a player whose rides disappeared", icon: "textures/items/boat_oak" },
        { label: "Restore Machines\n§8Anchor PC/heal/etc - auto-respawn if lost", icon: "textures/items/repeater" }
    ], "pokedex_black");
    switch (sel) {
        case 0: await openSerpAdmin(admin); break;
        case 1: await openEcoAdmin(admin); break;
        case 2: await openAdminTP(admin); break;
        case 3: await openAdminMode(admin); break;
        case 4: await adminAnnounces(admin); break;
        case 5: await openGrantAdmin(admin); break;
        case 6: await openEventsAdmin(admin); break;
        case 7: await openTitlesAdmin(admin); break;
        case 8: await openClaimsAdmin(admin); break;
        case 9: await openMachinePurge(admin); break;
        case 10: await openGuardCapAdmin(admin); break;
        case 11: await openVehAdmin(admin); break;
        case 12: await openMachineAnchors(admin); break;
    }
}

// On Realms you cannot /op or /tag to delegate. This lets an existing admin add
// the SunHub admin tag to any online member, so they get admin without full
// Realm operator rights.
async function openGrantAdmin(admin) {
    const players = world.getAllPlayers();
    const buttons = players.map((p) => {
        let op = false;
        try { op = p.commandPermissionLevel >= 1; } catch {}
        const has = p.hasTag(CONFIG.adminTag);
        return { label: p.name + "\n" + (op ? "§bOperator (always admin)" : has ? "§aAdmin ✔ (tap to revoke)" : "§8Not admin (tap to grant)"), icon: "textures/items/name_tag" };
    });
    buttons.push({ label: "Back", icon: "textures/ui/arrow_left" });
    const sel = await actionMenu(admin, "Grant / revoke admin",
        "§7Operators are always admin. This adds the admin tag for non-operators (useful on Realms).",
        buttons, "pokedex_black");
    if (sel < 0 || sel >= players.length) return openAdmin(admin);
    const t = players[sel];
    try {
        let op = false; try { op = t.commandPermissionLevel >= 1; } catch {}
        if (op) {
            admin.sendMessage("§e[SunHub] " + t.name + " is already an operator - no tag needed.");
        } else if (t.hasTag(CONFIG.adminTag)) {
            t.removeTag(CONFIG.adminTag);
            admin.sendMessage("§e[SunHub] Revoked admin from " + t.name + ".");
            t.sendMessage("§e[SunHub] Your admin access was removed.");
        } else {
            t.addTag(CONFIG.adminTag);
            admin.sendMessage("§a[SunHub] Granted admin to " + t.name + ".");
            t.sendMessage("§a[SunHub] You now have SunHub admin access.");
        }
    } catch {}
    return openGrantAdmin(admin);
}

async function openLeaderboards(player) {
    if (!seasonEnabled()) {
        player.sendMessage(t(player, "season.hub.off"));
        return;
    }
    const cfg = rewardsCfg();
    const hasPrize = (rk) => rk && (rk.coins > 0 || rk.item || rk.poke);
    const prizeLine = (hasPrize(cfg.r1) || hasPrize(cfg.r2) || hasPrize(cfg.r3))
        ? t(player, "season.hub.prizeline", { r1: rankLabel(player, cfg.r1), r2: rankLabel(player, cfg.r2), r3: rankLabel(player, cfg.r3) })
        : t(player, "season.hub.noprize");
    const medals = ["§6#1", "§7#2", "§c#3"];
    const lb = seasonScores();
    // One scrollable list: every category with its top 5, laid out as text rows
    // (no per-category buttons - the whole board reads top to bottom).
    const sections = [];
    for (const [, key] of CATS) {
        const rows = Object.entries(lb)
            .map(([name, v]) => [name, v[key] ?? 0])
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5)
            .filter(([, v]) => v > 0);
        const lines = rows.length === 0
            ? "  " + t(player, "season.hub.noscores")
            : rows.map(([n, v], i) => t(player, "season.hub.row", {
                medal: (medals[i] ?? "§f#" + (i + 1)), name: n, score: v,
                unit: t(player, "season.unit." + key),
                you: (n === player.name ? t(player, "season.hub.you") : "")
            })).join("\n");
        sections.push(t(player, "season.cattitle", { title: t(player, "season.cat." + key) }) + "\n" + lines);
    }
    const body = t(player, "season.hub.body", { d: daysLeft(), prize: prizeLine }) + "\n\n" + sections.join("\n\n");
    await actionMenu(player, t(player, "season.hub.title", { w: weekNum() }), body,
        [{ label: t(player, "common.close"), icon: "textures/ui/buttons/bubble_no" }], "pokedex_yellow");
}

async function openGuide(player) {
    const PAGES = [
        ["Scanner: Pokemon info", "textures/items/spyglass",
            "§lPOKEMON SCANNER§r\n\n" +
            "Hold the §eSunHub Menu§r item and §etap any Pokemon§r (wild or sent out) to open its info card:\n\n" +
            "- Name, gender \uE130/\uE131, §aSHINY\uE132§r mark\n" +
            "- HP and estimated level (wild)\n" +
            "- Type, base stats, catch rate\n" +
            "- Weight, height, spawn biome\n\n" +
            "§7Gender/shiny only shows on caught Pokemon that are sent out - wild ones don't carry that data yet.§r"],
        ["Trading with players", "textures/items/diamond",
            "§lTRADING§r\n\n" +
            "§e1.§r Hold the SunHub Menu item and §etap the other player§r.\n" +
            "§e2.§r Pick the Pokemon you offer from your team.\n" +
            "§e3.§r They see your offer (level, IVs, shiny) and pick one back - or decline.\n" +
            "§e4.§r You confirm - both Pokemon swap instantly.\n\n" +
            "Everything travels with the Pokemon: §amoves, IVs, EVs, nature, shiny, nickname§r.\n\n" +
            "§c! Recall the Pokemon to its ball before trading.§r\n" +
            "§7Closed the form by accident? Just tap each other again - it reopens at the same step. Offers expire after 2 minutes. Stand within 24 blocks to finish.§r"],
        ["Radar", "textures/items/compass_item",
            "§lPOKEMON RADAR§r\n\n" +
            "Use the §eRadar§r item to scan §e80 blocks§r around you:\n\n" +
            "- Every wild species nearby, grouped\n" +
            "- Distance + compass direction (N/NE/E...)\n" +
            "- Sorted closest first\n\n" +
            "§7Great for hunting a specific spawn or checking if anything rare is around.§r"],
        ["Wonder Trade & GTS", "textures/items/compass_item",
            "§lTRADE MACHINE+§r\n\n" +
            "§eSneak + tap§r the Trade Machine in any Pokemon Center (normal tap = SERP's own trade):\n\n" +
            "§eWonder Trade§r - send one Pokemon, receive a random one from another trainer. Works even if you deposit first and they come later.\n\n" +
            "§eGTS§r - list a Pokemon + what you want back (species, min level, shiny). Anyone can fulfill it any time - you get the trade even while offline. Max 3 listings. Cancel any time from My Listings."],
        ["Bounties, Card & Rankings", "textures/items/gold_ingot",
            "§lBOUNTY QUESTS§r\nDaily Quests now include bounties: catch a specific species for a big coin reward.\n\n" +
            "§lTRAINER CARD§r\nTap a player with the hub item -> Trainer Card: badges, dex, shinies, team.\n\n" +
            "§lIV POTENTIAL§r\nScan your own sent-out Pokemon or view a trade offer to see its IV rating (Outstanding ★ = 4+ perfect stats).\n\n" +
            "§lLEADERBOARDS§r\nHub -> Leaderboards: top dex, shiny, catches, level. Shiny catches get announced server-wide!"],
        ["Roaming Legendary & Events", "textures/items/nether_star",
            "\u00a7lDAILY LEGENDARY\u00a7r\nEvery day a \u00a7edifferent\u00a7r legendary appears \u00a7e1000-2000 blocks\u00a7r from the village, at a random huge size. Follow the server hints (direction + distance, sharper each time) and get within 60 blocks - it shows itself.\n\n" +
            "\u00a76It CAN be caught\u00a7r - but the capture rate is \u00a7c0.01%\u00a7r (1 in 10,000): almost every ball breaks and it pops right back. \u00a7eWhoever finally lands it keeps it\u00a7r and the hunt ends for everyone. \u00a7cYou only get 10 minutes\u00a7r from the moment it appears before it vanishes until tomorrow.\n\n" +
            "\u00a7lMYSTERY GIFT\u00a7r\nWhen admins run a gift, the Hub's Gifts button glows \u00a7d(!)\u00a7r - claim a free Pokemon once per player.\n\n" +
            "\u00a7lx2 EVENTS\u00a7r\nAdmins can double kill-coins or quest rewards for a few hours - watch chat for the announcement!"],
        ["Titles", "textures/items/name_tag",
            "\u00a7lTITLES\u00a7r\n\nWear a decorated badge on your name - it shows above your head AND in chat.\n\n" +
            "\u00a7eHub -> Titles\u00a7r to equip one you own (or go plain).\n\n" +
            "How to earn them: some are auto-granted when you hit a goal (Pokedex size, money, catches, shinies, check-in streak), some go to the weekly \u00a76season #1\u00a7r, and some are handed out by admins for special occasions. New titles get announced server-wide."],
        ["Land Claims", "textures/blocks/grass_side_carried",
            "\u00a7lLAND CLAIMS\u00a7r\n\nStand in the middle of your base, open \u00a7eHub -> Land Claims -> Claim land HERE\u00a7r, pick a radius (8-32) - done. Up to \u00a7e2 claims\u00a7r each, overworld only, not near spawn.\n\n" +
            "On your land, outsiders \u00a7ccannot\u00a7r break or place blocks, open chests/furnaces, or press buttons - and \u00a7cexplosions can't damage it\u00a7r. Walking and Pokemon battles are unaffected.\n\n" +
            "Add \u00a7emembers\u00a7r so friends can build with you. The action bar shows whose land you're standing on.\n\n" +
            "\u00a7lPOKEMON GUARDS\u00a7r\nIn Land Claims -> \u00a7ePokemon Guards\u00a7r: deploy up to 6 Pokemon from your PC around your land. They auto-attack hostile mobs nearby (damage scales with level) but \u00a7anever\u00a7r players or Pokemon. Defeated guards respawn at their post after 5 min - nothing is ever lost, and nobody can catch them."],
        ["Jobs", "textures/items/iron_pickaxe",
            "\u00a7lJOBS\u00a7r\n\nPick one: \u00a7eMiner\u00a7r (ores), \u00a7eFarmer\u00a7r (grown crops), \u00a7eLumberjack\u00a7r (logs). Working pays a little and gives \u00a7bjob XP\u00a7r - the real money is in the 2 \u00a7edaily contracts\u00a7r.\n\n" +
            "Perk milestones: Lv5 +15% pay, Lv10 double-drops, Lv15 work buffs, Lv20 a 3rd contract, Lv25 \u00a76MASTER\u00a7r (+50% pay, server-wide shoutout, and admins can make \u00a7ejob-level titles\u00a7r!).\n\n" +
            "\u00a7cNo cheesing:\u00a7r blocks you placed yourself pay nothing, and crops must be fully grown. Switching jobs keeps each job's level."],
        ["Pokemon Guards", "textures/items/carrot_on_a_stick",
            "\u00a7lPOKEMON GUARDS\u00a7r (Land Claims -> Pokemon Guards)\n\nDeploy up to \u00a7e20\u00a7r Pokemon on your claimed land - from your \u00a7bparty\u00a7r or your \u00a7bPC\u00a7r. They walk around with their normal AI and \u00a7aauto-attack hostile mobs\u00a7r within 10 blocks (never players or Pokemon).\n\n" +
            "They \u00a7acan't be caught, stolen or lost\u00a7r - the Pokemon you deploy is a projection, its party/PC record never changes. If one is defeated it comes back after 5 minutes, and if it wanders off your land it quietly returns to its post.\n\n" +
            "\u00a7cImportant:\u00a7r Pokemon share Minecraft's mob cap, so \u00a7eevery guard you deploy is one wild Pokemon that can't spawn\u00a7r. If wild spawns feel thin, recall a few guards."],
        ["Workshop & Buddy", "textures/items/iron_pickaxe",
            "\u00a7lPOKEMON WORKSHOP\u00a7r (Land Claims -> Pokemon Workshop)\nDeploy Pokemon as Guards on your land, then \u00a7eassign them jobs\u00a7r. Every minute each worker eats 1 food from your \u00a7eFood Chest\u00a7r and produces resources into your \u00a7eOutput Chest\u00a7r.\n\n" +
            "Jobs come from the Pokemon's \u00a7btype\u00a7r: Ground/Rock/Steel mine, Grass farms and logs, Fire smelts, Electric powers, Ice cools, Water waters, Poison/Fairy/Psychic make medicine, Normal/Flying haul, Bug/Fairy ranch. Higher level = more output, plus rare bonus items.\n\nOutput mixes \u00a7bMinecraft resources\u00a7r (ores, wood, crops, redstone) and \u00a7bSERP items\u00a7r (berries, potions, Poke Balls, evolution stones, Moomoo Milk). Workers eat vanilla food \u00a7eor SERP berries\u00a7r.\n\n" +
            "\u00a7lBUDDY\u00a7r (Hub -> Buddy)\nPick any Pokemon as your partner. You get a \u00a7etype-based passive\u00a7r (Electric = Speed, Flying = glide, Water = water breathing, Ground/Rock = Haste, Dark/Ghost = night vision, Fighting = Strength, Steel = Resistance...), an \u00a7eitem magnet\u00a7r that pulls drops to you, and \u00a7ebonus drops\u00a7r when you mine."],
        ["Fixes", "textures/items/fishing_rod",
            "\u00a7lSMARTER POKEMON AI\u00a7r\nSerpLumen rewrites the AI of \u00a7eall 331 species\u00a7r:\n\u00a77- Birds and dragons \u00a7bactually fly\u00a7r (48 species)\n\u00a77- Water Pokemon \u00a7bswim\u00a7r instead of walking onto the beach (51)\n\u00a77- Strong ones \u00a7cfight back\u00a7r when you attack them (210); timid ones \u00a7ebolt away\u00a7r (121)\n\u00a77- Grass types graze, Fire types are fireproof, Rock/Steel resist knockback\n\u00a77- No more stuttering: the old AI dragged every script-spawned Pokemon toward world origin\n\u00a7aThey never attack you unprovoked - walk up and throw a ball exactly like before.\u00a7r\n\nGuards hunt and melee hostile mobs with \u00a7creal AI\u00a7r instead of a script loop - smoother, and lighter on your phone.\n\n" +
            "\u00a7lFISH STAY IN WATER\u00a7r\nSERP's water Pokemon (Magikarp, Goldeen, Tentacool, Staryu...) had a spawn bug that let them appear on dry land. SerpLumen fixes their spawn rules: pure water species now spawn \u00a7bunderwater only\u00a7r, while amphibious ones (Psyduck, Poliwag, Slowpoke, Krabby...) can still be found on beaches - just far less often."],
                        ["My Vehicles", "textures/items/boat_oak",
            "\u00a7lMY VEHICLES\u00a7r\n\nEvery boat, minecart or tamed ride (horse, donkey, mule, llama, strider, camel, pig) you place is \u00a7eregistered to you\u00a7r automatically - up to 10.\n\n\u00a7eHub -> Travel -> My Vehicles\u00a7r: \u00a7aBring it to me\u00a7r teleports it to you from anywhere, or \u00a7aSend it to my land\u00a7r parks it at your claim.\n\nIf a boat/minecart of yours is destroyed, \u00a7athe item comes straight back to your inventory\u00a7r (queued if you're offline) - no more re-crafting the same boat."],
        ["Teleport Pillars", "textures/blocks/lodestone_top",
            "\u00a7lTELEPORT PILLARS\u00a7r\n\nPlace a \u00a7bLODESTONE\u00a7r anywhere to create a personal teleport pillar - up to \u00a7e10\u00a7r. Open \u00a7eHub -> Travel -> Teleport Pillars\u00a7r to warp between them, rename them, or forget one.\n\nBreaking the lodestone destroys the pillar (protect important ones on claimed land!). Lodestones are craftable (chiseled stone bricks + netherite ingot) and admins can hand them out via kits or gift codes."],
        ["Coins & Daily", "textures/items/emerald",
            "§lECONOMY§r\n\n" +
            "Earn coins by §ecatching and defeating Pokemon§r and finishing §eDaily Quests§r (3 new ones every day).\n\n" +
            "- §eDaily Check-in§r: streak = bigger rewards; streak 7/14/30 can earn a free Pokemon (admin-set)!\n" +
            "- §eBank§r: deposit for interest, transfer to friends\n" +
            "- §eGift codes§r: redeem from the hub\n" +
            "- §ePeriodic Packs§r: starter + weekly freebies"],
        ["Getting around & healing", "textures/items/ender_pearl",
            "§lTRAVEL & HEAL§r\n\n" +
            "§eQuick travel§r: set a Home, warp to the Village, back to Spawn, or your den.\n\n" +
            "§eHeal whole team§r: heals every one of your Pokemon standing nearby - has a cooldown, so recall the healthy ones first if you're rationing it."],
    ];
    while (true) {
        const sel = await actionMenu(player, "Guide", "Pick a topic:",
            PAGES.map(([label, icon]) => ({ label, icon })), "pokedex_green");
        if (sel < 0) return;
        const back = await actionMenu(player, PAGES[sel][0], PAGES[sel][2],
            [{ label: "Back to topics", icon: "textures/items/book_normal" }], "pokedex_green");
        if (back < 0) return;
    }
}

// open a category submenu; Back (or cancel) returns to the hub
async function openGroup(player, group) {
    const items = [...group.items, { label: t(player, "common.back"), icon: "textures/ui/buttons/bubble_no", run: () => openHub(player) }];
    const sel = await actionMenu(player, group.label.split("\n")[0], t(player, "group.pick"),
        items.map((i) => ({ label: i.label, icon: i.icon })), "sunhub");
    if (sel < 0) return openHub(player);
    await items[sel].run();
}

export async function openHub(player) {
    const daily = dailyStatus(player);
    const admin = isAdmin(player);
    const unread = hasUnread(player);

    // leaf actions - each carries its own handler (order-independent)
    const A = {
        lang: { label: t(player, "lang.compact"), icon: "textures/items/compass", run: async () => {
            toggleLang(player);
            player.sendMessage(t(player, "lang.switched"));
            await openHub(player); // redraw the whole menu in the new language
        } },
        announces: { label: (unread ? "§e(!) " : "") + t(player, "hub.announces") + (unread ? t(player, "hub.announces.new") : ""), icon: "textures/items/paper", run: () => openAnnounces(player) },
        bank: { label: t(player, "hub.bank"), icon: "textures/items/emerald", run: () => openBank(player) },
        daily: { label: daily.claimedToday ? t(player, "hub.daily.done") : t(player, "hub.daily.ready"), icon: "textures/items/clock_item", run: () => {
            const r = claimDaily(player);
            player.sendMessage((r.ok ? "§a" : "§e") + "[SunHub] " + r.msg);
        } },
        packs: { label: t(player, "hub.packs"), icon: "textures/items/minecart_chest", run: () => openKits(player) },
        gifts: { label: (activeGift() ? t(player, "hub.gifts.live") : t(player, "hub.gifts.code")), icon: "textures/items/cake", run: async () => {
            const g = activeGift();
            if (g && !giftClaimed(player, g)) {
                const s2 = await actionMenu(player, t(player, "hub.gifts.title"), t(player, "hub.gifts.body"), [
                    { label: t(player, "hub.gifts.claim"), icon: "textures/items/cake" },
                    { label: t(player, "hub.gifts.enter"), icon: "textures/items/paper" },
                ], "pokedex_orange");
                if (s2 === 0) await claimMysteryGift(player);
                else if (s2 === 1) await redeemGift(player);
            } else await redeemGift(player);
        } },
        jobs: { label: t(player, "hub.jobs"), icon: "textures/items/iron_pickaxe", run: () => openJobs(player) },
        travel: { label: t(player, "hub.travel"), icon: "textures/items/ender_pearl", run: () => openNavigator(player) },
        heal: { label: t(player, "hub.heal"), icon: "textures/items/potion_bottle_heal", run: () => doHealParty(player) },
        claims: { label: t(player, "hub.claims"), icon: "textures/blocks/grass_side_carried", run: () => openClaims(player) },
        quests: { label: t(player, "hub.quests"), icon: "textures/items/book_written", run: () => openQuests(player) },
        leaderboards: { label: seasonEnabled() ? t(player, "hub.leaderboards.on", { w: weekNum() }) : t(player, "hub.leaderboards.off"), icon: "textures/items/gold_ingot", run: () => openLeaderboards(player) },
        titles: { label: t(player, "hub.titles"), icon: "textures/items/name_tag", run: () => openTitles(player) },
        buddy: { label: t(player, "hub.buddy"), icon: "textures/items/carrot_on_a_stick", run: () => openBuddy(player) },
        plates: { label: platesOn(player) ? t(player, "hub.plates.on") : t(player, "hub.plates.off"), icon: "textures/items/name_tag", run: () => {
            const on = !platesOn(player);
            setPlates(player, on);
            player.sendMessage(on ? t(player, "msg.plates.on") : t(player, "msg.plates.off"));
        } },
        battery: { label: isLow(player) ? t(player, "hub.battery.on") : t(player, "hub.battery.off"), icon: "textures/items/redstone_dust", run: () => {
            const low = !isLow(player);
            setLow(player, low);
            player.sendMessage(low ? t(player, "msg.battery.on") : t(player, "msg.battery.off"));
        } },
        guide: { label: t(player, "hub.guide"), icon: "textures/items/book_normal", run: () => openGuide(player) },
    };

    const groups = [
        { label: t(player, "group.economy"), icon: "textures/items/emerald", items: [A.bank, A.daily, A.packs, A.gifts, A.jobs] },
        { label: t(player, "group.travel"), icon: "textures/items/ender_pearl", items: [A.travel, A.heal, A.claims] },
        { label: t(player, "group.achieve"), icon: "textures/items/gold_ingot", items: [A.quests, A.leaderboards, A.titles, A.buddy] },
        { label: t(player, "group.settings"), icon: "textures/items/redstone_dust", items: [A.plates, A.battery, A.guide] },
    ];

    // top level: small language toggle, announcements, then the category groups
    const top = [A.lang, A.announces, ...groups.map((g) => ({ label: g.label, icon: g.icon, run: () => openGroup(player, g) }))];
    if (admin) top.push(A.admin = { label: t(player, "hub.admin"), icon: "textures/items/netherite_pickaxe", run: () => openAdmin(player) });

    const jobLv = (() => { try { return maxJobLevel(player); } catch { return 1; } })();
    const badge = (() => { try { return titlePrefix(player).trim(); } catch { return ""; } })();
    const rankTag = badge ? " " + badge : "";
    // Header uses real SunHub data; wording follows the player's language.
    const header =
        t(player, "hub.header.bal", { name: player.name, rank: rankTag, coins: fmt(getCoins(player)) }) + "\n" +
        t(player, "hub.header.job", { lv: jobLv }) + "\n" +
        t(player, "hub.header.streak", { n: daily.streak });

    const sel = await actionMenu(player, t(player, "hub.title", { server: (SERVER_NAME || "SUNHUB").toUpperCase() }),
        header,
        top.map((i) => ({ label: i.label, icon: i.icon })), "sunhub");
    if (sel < 0) return;
    await top[sel].run();
}

// The Hub is opened from the Pokedex menu's "Hub" button only - no separate Hub
// item is given or handled anymore.

let announced = false;
system.runInterval(() => {
    const players = world.getAllPlayers();
    if (players.length === 0) return;
    if (!announced) {
        announced = true;
        world.sendMessage("§6[SunHub] §fstarted.");
    }
}, 100);
