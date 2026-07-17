import { CONFIG } from "./config.js";
import { isPokemon, isWild, displayName, dirTo } from "./poke.js";
import { actionMenu } from "./forms.js";

export async function openRadar(player) {
    const entities = player.dimension.getEntities({
        location: player.location,
        maxDistance: CONFIG.radarRadius
    }).filter((e) => isPokemon(e.typeId) && isWild(e));

    if (entities.length === 0) {
        player.sendMessage("§b[SunHub] Radar: no Pokemon wild any trong " + CONFIG.radarRadius + " block.");
        return;
    }

    // Gom theo loai, giu con gan nhat
    const groups = new Map();
    for (const e of entities) {
        const name = displayName(e);
        const d = dirTo(player.location, e.location);
        const g = groups.get(name);
        if (!g || d.dist < g.dist) groups.set(name, { name, count: (g?.count ?? 0) + 1, dist: d.dist, dir: d.dir });
        else g.count += 1;
    }
    const list = [...groups.values()].sort((a, b) => a.dist - b.dist);

    await actionMenu(player, "Radar (" + entities.length + " Pokemon)",
        "Trong radius " + CONFIG.radarRadius + " block:",
        list.map((g) => ({ label: "§f" + g.name + " §7x" + g.count + "\n§b" + g.dist + "m huong " + g.dir })), "pokedex_cyan");
}
