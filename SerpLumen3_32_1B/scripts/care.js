// Cham soc: hoi mau theo thoi gian + friendship + rot qua (thay Ranch)
import { world, system, ItemStack } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { isPokemon, ownerOf, hp, friendship, displayName } from "./poke.js";

function ownedPokemonNear(player, radius) {
    return player.dimension.getEntities({
        location: player.location,
        maxDistance: radius
    }).filter((e) => {
        if (!isPokemon(e.typeId)) return false;
        const o = ownerOf(e);
        return o !== undefined && o.id === player.id;
    });
}

export function registerCare() {
    // Hoi mau tu tu cho Pokemon cua ban dang o ngoai bong
    system.runInterval(() => {
        for (const player of world.getAllPlayers()) {
            for (const e of ownedPokemonNear(player, CONFIG.friendshipRadius)) {
                const h = hp(e);
                if (!h || h.cur >= h.max || h.cur <= 0) continue;
                try {
                    h.comp.setCurrentValue(Math.min(h.max, h.cur + CONFIG.healAmount));
                } catch { /* ignore */ }
            }
        }
    }, CONFIG.healIntervalSec * 20);

    // Friendship: +1 diem/phut o ngoai bong gan chu; du moc thi rot berry
    system.runInterval(() => {
        for (const player of world.getAllPlayers()) {
            for (const e of ownedPokemonNear(player, CONFIG.friendshipRadius)) {
                const fr = friendship(e) + 1;
                try { e.setDynamicProperty("sd:fr", fr); } catch { continue; }

                if (fr % CONFIG.dropEvery === 0) {
                    const item = CONFIG.dropItems[Math.floor(Math.random() * CONFIG.dropItems.length)];
                    try {
                        e.dimension.spawnItem(new ItemStack(item.id, 1), e.location);
                        e.dimension.spawnParticle("minecraft:heart_particle", {
                            x: e.location.x, y: e.location.y + 1, z: e.location.z
                        });
                        player.sendMessage("§d[SunHub] " + displayName(e) + " happily found 1x " + item.name + " to you!");
                    } catch { /* ignore */ }
                }
            }
        }
    }, 1200); // moi 60 giay
}
