// Tien ich chung ve Pokemon
import { EntityComponentTypes } from "@minecraft/server";
import { CONFIG } from "./config.js";

export function isPokemon(typeId) {
    return CONFIG.pokemonPrefixes.some((p) => typeId.startsWith(p));
}

export function isWild(entity) {
    try {
        const t = entity.getComponent(EntityComponentTypes.Tameable);
        return !t || !t.isTamed;
    } catch { return true; }
}

export function ownerOf(entity) {
    try {
        const t = entity.getComponent(EntityComponentTypes.Tameable);
        if (t && t.isTamed) return t.tamedToPlayer;
    } catch { /* ignore */ }
    return undefined;
}

export function displayName(entity) {
    if (entity.nameTag && entity.nameTag.length > 0) {
        // nameTag SERP co the nhieu dong -> lay dong dau, bo ma mau
        return entity.nameTag.split("\n")[0].replace(/§./g, "").trim() || entity.typeId;
    }
    const raw = entity.typeId.split(":")[1] ?? entity.typeId;
    return raw;
}

export function hp(entity) {
    try {
        const h = entity.getComponent(EntityComponentTypes.Health);
        if (h) return { cur: Math.round(h.currentValue), max: Math.round(h.effectiveMax), comp: h };
    } catch { /* ignore */ }
    return null;
}

export function friendship(entity) {
    const v = entity.getDynamicProperty("sd:fr");
    return typeof v === "number" ? v : 0;
}

export function friendshipRank(points) {
    if (points >= 200) return "§dTri ky";
    if (points >= 100) return "§cThan thiet";
    if (points >= 50) return "§6You tot";
    if (points >= 20) return "§eQuen thuoc";
    return "§7Moi quen";
}

export function dirTo(from, to) {
    const dx = to.x - from.x, dz = to.z - from.z;
    const dist = Math.round(Math.sqrt(dx * dx + dz * dz));
    const ns = dz < 0 ? "Bac" : "Nam";
    const ew = dx < 0 ? "Tay" : "Dong";
    const dir = Math.abs(dx) > Math.abs(dz) * 2 ? ew
        : Math.abs(dz) > Math.abs(dx) * 2 ? ns
        : ns + " " + ew;
    return { dist, dir };
}
