// ============================================================
// SunnyCore - Config hop nhat (UI + Eco + Dex + Essentials-style)
// ============================================================
export const CONFIG = {
    // Item duy nhat mo moi menu
    hubItem: "sunhub:menu",
    adminTag: "sunnyui_admin",
    pokemonPrefixes: ["pokemon:"],

    // --- Currency: uses SERP's 'money' scoreboard (PokeDollar) ---
    coinObjective: "money",
    coinName: "PokeDollar",
    coinSymbol: "$",
    killReward: { min: 3, max: 8 },

    // --- Hoi mau toan doi (nut trong menu) ---
    healRadius: 32,
    healCooldownSec: 60,

    // --- Cham soc tu dong (ngoai bong) ---
    healIntervalSec: 10,
    healAmount: 1,
    friendshipRadius: 64,
    dropEvery: 10,
    dropItems: [
        { id: "serp:oran_berry", name: "Oran Berry" },
        { id: "serp:cheri_berry", name: "Cheri Berry" },
        { id: "serp:razz_berry", name: "Razz Berry" }
    ],

    // --- Radar / Roll ---
    radarRadius: 64,
    rollEnabled: true,
    rollCooldownSec: 3,
    rollPower: 2.2,

    // --- Nhiem vu ---
    questsPerDay: 3,
    ballItems: [
        "serp:pokeball", "serp:greatball", "serp:ultraball", "serp:healball",
        "serp:duskball", "serp:quickball", "serp:timerball", "serp:netball",
        "serp:nestball", "serp:diveball", "serp:repeatball", "serp:luxuryball",
        "serp:premierball", "serp:lureball", "serp:levelball", "serp:heavyball",
        "serp:fastball", "serp:friendball", "serp:loveball", "serp:moonball"
    ],

    // --- SERP admin ---
    maxBadge: 18,
    maxDexId: 1025
};
