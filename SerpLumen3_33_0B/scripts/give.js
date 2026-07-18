import { world, ItemStack } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { SERP_ITEMS } from "./items.js";
import { VANILLA_ITEMS } from "./vanillaitems.js";
import { run, actionMenu, giveItem } from "./forms.js";

const MAX_RESULTS = 24;


const ALL_ITEMS = [...SERP_ITEMS, ...VANILLA_ITEMS, { id: "sunhub:menu", name: "SunHub Menu Item" }, { id: "sunnyx:radar", name: "SunHub Radar" }];

function searchItems(keyword) {
    const kw = keyword.trim().toLowerCase();
    if (kw.length === 0) return [];
    return ALL_ITEMS
        .filter((it) => it.name.toLowerCase().includes(kw) || it.id.includes(kw))
        .slice(0, MAX_RESULTS);
}

/**
  Flow: pick player + type keyword + quantity -> results -> pick item -> give.
  If no results: tell them and let them retype.
 */
export async function openGiveItem(admin) {
    const players = world.getAllPlayers();
    const names = players.map((p) => p.name);

    const form = await new ModalFormData()
        .title("Give items")
        .dropdown("player", names)
        .textField("Search (name or id)", "e.g. pokeball, potion, mega...")
        .textField("Quantity", "1")
        .show(admin);
    if (form.canceled || !form.formValues) return;

    const target = players[form.formValues[0]];
    const keyword = String(form.formValues[1]);
    const amount = Math.max(1, Math.min(6400, parseInt(String(form.formValues[2]).trim(), 10) || 1));
    if (!target) return;

    const results = searchItems(keyword);
    if (results.length === 0) {
        admin.sendMessage("§c[SunHub] No matching item: §f" + keyword);
        return openGiveItem(admin);
    }

    const sel = await actionMenu(
        admin,
        "Results: " + keyword,
        results.length >= 24 ? "Showing first 24 results, type a more specific keyword if needed." : "Select an item:",
        results.map((it) => ({ label: it.name + "\n§8" + it.id }))
    );
    if (sel < 0) return;

    const item = results[sel];
    // validate the item id up front so errors are honest
    let valid = true;
    try { new ItemStack(item.id, 1); } catch { valid = false; }
    if (!valid) {
        admin.sendMessage("\u00a7c[SunHub] Item id kh\u00f4ng h\u1ee3p l\u1ec7 tr\u00ean Bedrock: \u00a7f" + item.id + "\u00a7c - b\u00e1o l\u1ea1i \u0111\u1ec3 fix catalog.");
        return;
    }
    const live = world.getAllPlayers().find((p) => p.name === target.name);
    if (live && giveItem(live, item.id, amount)) {
        admin.sendMessage(`§a[SunHub] Gave §f${amount}x ${item.name}§a to §f${target.name}`);
        live.sendMessage(`§a[SunHub] You received §f${amount}x ${item.name}`);
    } else {
        admin.sendMessage(`§c[SunHub] Send failed: player went offline or inventory unavailable.`);
    }
}
