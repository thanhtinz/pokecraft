import { EntityComponentTypes } from "@minecraft/server";
import { CONFIG } from "./config.js";

const cooldowns = new Map(); // playerId -> timestamp ms

/** Kiem tra Pokemon co thuoc so huu cua player khong (qua minecraft:tameable). */
export function isOwnedBy(entity, player) {
    try {
        const tame = entity.getComponent(EntityComponentTypes.Tameable);
        if (!tame || !tame.isTamed) return false;
        const owner = tame.tamedToPlayer;
        return owner !== undefined && owner.id === player.id;
    } catch {
        return false;
    }
}

/** Hoi mau 1 entity ve max HP. Tra ve true neu co hoi. */
export function healEntity(entity) {
    const hp = entity.getComponent(EntityComponentTypes.Health);
    if (!hp) return false;
    if (hp.currentValue >= hp.effectiveMax) return false;
    hp.resetToMaxValue();
    return true;
}

/** Cooldown hoi mau toan doi (chong spam). Tra ve so giay con lai, 0 = san sang. */
export function healCooldownLeft(player) {
    const last = cooldowns.get(player.id) ?? 0;
    const left = CONFIG.healCooldownSec * 1000 - (Date.now() - last);
    return left > 0 ? Math.ceil(left / 1000) : 0;
}

export function markHealUsed(player) {
    cooldowns.set(player.id, Date.now());
}

/**
 * Hoi mau tat ca Pokemon cua player trong ban kinh CONFIG.healRadius.
 * @returns {number} so Pokemon da duoc hoi
 */
export function healPartyNearby(player) {
    const entities = player.dimension.getEntities({
        location: player.location,
        maxDistance: CONFIG.healRadius
    });
    let healed = 0;
    for (const e of entities) {
        if (!CONFIG.pokemonPrefixes.some((p) => e.typeId.startsWith(p))) continue;
        if (!isOwnedBy(e, player)) continue;
        if (healEntity(e)) {
            healed++;
            try {
                e.dimension.spawnParticle("minecraft:heart_particle", {
                    x: e.location.x, y: e.location.y + 1, z: e.location.z
                });
            } catch { /* ignore */ }
        }
    }
    return healed;
}
