// SERP admin tools: starter / give Pokemon (search + level) / badge / reset / give items
import { CONFIG } from "./config.js";
import { run, confirmForm, pickPlayer, pickPlayerAndNumber, actionMenu } from "./forms.js";
import { openGiveItem } from "./give.js";
import { givePokemon } from "./dxgive.js";
import { pickPokemonSearch } from "./pokepick.js";
import { world } from "@minecraft/server";

export async function openSerpAdmin(admin) {
    const sel = await actionMenu(admin, "Items & Pokemon", "Select an action:", [
        { label: "Give items to player\n§8Type a name to search, 240+ items", icon: "textures/blocks/chest_front" },
        { label: "Let them pick a starter again", icon: "textures/items/egg" },
        { label: "Give Pokemon\n§8Search by name + pick level", icon: "textures/items/experience_bottle" },
        { label: "Give Gym Badge", icon: "textures/items/gold_ingot" },
        { label: "§cWipe all Pokemon of a player\n§8Reset team + PC box", icon: "textures/blocks/barrier" }
    ], "pokedex_black");
    switch (sel) {
        case 0: await openGiveItem(admin); break;
        case 1: {
            const t = await pickPlayer(admin, "Select Starter");
            if (t && run(admin, `serp:pokestarter "${t.name}"`))
                admin.sendMessage("§a[SunHub] Opened starter picker for §f" + t.name);
            break;
        }
        case 2: {
            const t = await pickPlayer(admin, "Give Pokemon - pick player");
            if (!t) break;
            const pick = await pickPokemonSearch(admin, "Give Pokemon to " + t.name);
            if (!pick) break;
            const live = world.getAllPlayers().find((p) => p.name === t.name);
            if (!live) { admin.sendMessage("§c[SunHub] " + t.name + " is offline."); break; }
            const res = givePokemon(live, pick.dex, pick.level);
            if (res.ok) {
                admin.sendMessage(`§a[SunHub] Gave §f#${pick.dex} Lv.${pick.level}§a to §f${t.name}` + (res.where === "pc" ? " §7(went to PC)" : ""));
                live.sendMessage(`§a[SunHub] You received a Lv.${pick.level} Pokemon!`);
            } else {
                admin.sendMessage(`§c[SunHub] Could not give Pokemon: ${res.reason}`);
            }
            break;
        }
        case 3: {
            const r = await pickPlayerAndNumber(admin, "Give Gym Badge", "Badge number", 1, CONFIG.maxBadge);
            if (r && run(admin, `serp:getbadge "${r.target.name}" ${r.num}`))
                admin.sendMessage(`§a[SunHub] Gave badge #${r.num} to §f${r.target.name}`);
            break;
        }
        case 4: {
            const t = await pickPlayer(admin, "Reset Team + PC");
            if (!t) return;
            const ok = await confirmForm(admin, `Wipe ALL team + PC of §c${t.name}§r?\nThis CANNOT be undone.`);
            if (ok && run(admin, `serp:pokereset "${t.name}"`))
                admin.sendMessage("§a[SunHub] Reset " + t.name);
            break;
        }
    }
}
