import { world, EntityComponentTypes } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { addCoins, fmt } from "./economy.js";
import { boostMult } from "./events.js";

function isWild(entity) {
    try {
        const tame = entity.getComponent(EntityComponentTypes.Tameable);
        return !tame || !tame.isTamed;
    } catch { return true; }
}

function creditPlayer(damagingEntity) {
    if (!damagingEntity) return undefined;
    if (damagingEntity.typeId === "minecraft:player") return damagingEntity;
    try {
        const tame = damagingEntity.getComponent(EntityComponentTypes.Tameable);
        if (tame && tame.isTamed) return tame.tamedToPlayer;
    } catch { /* ignore */ }
    return undefined;
}

export function registerEarning() {
    // Thuong diet Pokemon hoang da: cong thang vao old moneya SERP
    world.afterEvents.entityDie.subscribe((ev) => {
        const dead = ev.deadEntity;
        if (!CONFIG.pokemonPrefixes.some((p) => dead.typeId.startsWith(p))) return;
        if (!isWild(dead)) return;
        const player = creditPlayer(ev.damageSource.damagingEntity);
        if (!player) return;
        const { min, max } = CONFIG.killReward;
        const amount = (min + Math.floor(Math.random() * (max - min + 1))) * boostMult("coins");
        addCoins(player, amount);
        try { player.onScreenDisplay.setActionBar("§6+" + fmt(amount)); } catch { /* ignore */ }
    });
}
