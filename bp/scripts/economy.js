// Currency: uses SERP Pokedrock's 'money' scoreboard DIRECTLY
import { world } from "@minecraft/server";
import { CONFIG } from "./config.js";

function obj() {
    let o = world.scoreboard.getObjective(CONFIG.coinObjective);
    if (!o) o = world.scoreboard.addObjective(CONFIG.coinObjective, CONFIG.coinName);
    return o;
}

export function getCoins(player) {
    try { return obj().getScore(player) ?? 0; } catch { return 0; }
}

export function addCoins(player, amount) {
    try {
        obj().setScore(player, Math.max(0, getCoins(player) + Math.floor(amount)));
        return true;
    } catch { return false; }
}

export function spendCoins(player, amount) {
    const bal = getCoins(player);
    if (bal < amount) return false;
    try { obj().setScore(player, bal - Math.floor(amount)); return true; } catch { return false; }
}

export function fmt(amount) {
    return CONFIG.coinSymbol + amount.toLocaleString("en-US");
}
