// Utils hop nhat
import { world, system, ItemStack } from "@minecraft/server";
import { ActionFormData, ModalFormData, MessageFormData } from "@minecraft/server-ui";
import { CONFIG } from "./config.js";

export function defer(fn) { system.run(fn); }

// SERP gates its admin features (getpokemon, getbadge, pokeditor...) by the
// built-in operator permission level (GameDirectors = commandPermissionLevel
// >= 1) and Creative mode - it uses NO custom tag. We match that exactly so the
// same people who can use SERP admin can use SunHub admin: OP level first, then
// Creative, then the legacy tag as a manual fallback.
export function isAdmin(player) {
    try {
        if (player.commandPermissionLevel >= 1) return true;
    } catch {}
    try {
        if (player.getGameMode && player.getGameMode() === "Creative") return true;
    } catch {}
    return player.hasTag(CONFIG.adminTag);
}

// Kept for the few OP-only commands with no Script API path (mayfly,
// spreadplayers, serp:getpokemon). Everything else uses the API.
export function run(player, cmd) {
    try { player.runCommand(cmd); return true; }
    catch (e) {
        player.sendMessage("§c[SunHub] Command error: §7" + cmd);
        return false;
    }
}

// Give an item via the Script API (no runCommand). Splits into 64-stacks and
// drops any overflow at the player if the inventory is full.
export function giveItem(player, id, qty) {
    try {
        const inv = player.getComponent("inventory")?.container;
        if (!inv) return false;
        let left = Math.max(1, qty | 0);
        while (left > 0) {
            const n = Math.min(64, left);
            const stack = new ItemStack(id, n);
            const leftover = inv.addItem(stack);
            if (leftover) {
                try { player.dimension.spawnItem(leftover, player.location); } catch {}
            }
            left -= n;
        }
        return true;
    } catch {
        // last-resort fallback so admins are never blocked
        try { player.runCommand(`give @s ${id} ${qty}`); return true; } catch { return false; }
    }
}

// SERP-skinned menu. When `skin` is given, the title becomes "serp.main.<skin>"
// which triggers SERP's resource-pack panel (pokesv.main): it hides the default
// form chrome and draws the "pokedrock/ui/<skin>" frame with the body + buttons
// laid out inside it. Use a pokedex frame (e.g. "pokedex_red",
// "pokedex_data_blue") for the classic Pokedex panel look. Buttons with no icon
// get SERP's pokeball so no row is ever blank.
// SERP-skinned menu (stable). Every page renders inside SERP's frame; the
// default frame is our "sunhub". The skinned frame hides the native title bar,
// so the title moves into the body's first bold line.
const DEFAULT_ICON = "pokedrock/items/pokeball";
export async function actionMenu(player, title, body, buttons, skin) {
    // skin "slhub" routes to our custom 3-column grid UI (ui/slhub.json); every
    // other skin uses SERP's framed panel via "serp.main.<skin>".
    const formTitle = skin === "slhub" ? "slhub.main" : "serp.main." + (skin || "sunhub");
    const form = new ActionFormData().title(formTitle);
    const head = title ? "\u00a7l" + title + "\u00a7r" : "";
    if (typeof body === "string" || !body) form.body(head + (body ? "\n" + body : ""));
    else if (body && body.rawtext) form.body({ rawtext: [{ text: head + "\n" }, ...body.rawtext] });
    else form.body(body);
    for (const b of buttons) {
        form.button(b.label, b.icon || DEFAULT_ICON);
    }
    const res = await form.show(player);
    return (res.canceled || res.selection === undefined) ? -1 : res.selection;
}

export async function confirmForm(player, body) {
    const res = await new ActionFormData().title("serp.main.sunhub")
        .body("\u00a7lConfirm\u00a7r\n" + body)
        .button("Confirm", "textures/ui/buttons/bubble_yes")
        .button("Cancel", "textures/ui/buttons/bubble_no")
        .show(player);
    return !res.canceled && res.selection === 0;
}

export async function pickPlayer(player, title) {
    const players = world.getAllPlayers();
    const res = await new ModalFormData().title(title)
        .dropdown("player", players.map((p) => p.name)).show(player);
    if (res.canceled || !res.formValues) return null;
    return players[res.formValues[0]] ?? null;
}

export async function pickPlayerAndNumber(player, title, label, min, max) {
    const players = world.getAllPlayers();
    const res = await new ModalFormData().title(title)
        .dropdown("player", players.map((p) => p.name))
        .textField(label + " (" + min + "-" + max + ")", String(min))
        .show(player);
    if (res.canceled || !res.formValues) return null;
    const target = players[res.formValues[0]];
    const num = parseInt(String(res.formValues[1]).trim(), 10);
    if (!target || isNaN(num) || num < min || num > max) {
        player.sendMessage("§c[SunHub] Price tri is invalid (" + min + "-" + max + ").");
        return null;
    }
    return { target, num };
}

export function dayNumber() { return Math.floor(Date.now() / 86400000); }

export function strHash(s) {
    let h = 5381;
    for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) >>> 0;
    return h;
}
