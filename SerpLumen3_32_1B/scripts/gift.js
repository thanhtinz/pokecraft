// Gift Code: reward coins + item (searchable) + Pokemon (searchable, with level).
import { world } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { addCoins, fmt } from "./economy.js";
import { actionMenu, confirmForm, giveItem } from "./forms.js";
import { SERP_ITEMS } from "./items.js";
import { givePokemon } from "./dxgive.js";
import { POKENAMES } from "./pokenames.js";
import { pickPokemonSearch } from "./pokepick.js";
import { allTitles, renderTitle, grantById } from "./titles.js";

const PROP = "se:gcodes";
const REDEEMED = "se:redeemed";
const MAX_RESULTS = 24;

function getCodes() {
    try {
        const raw = world.getDynamicProperty(PROP);
        if (typeof raw === "string") return JSON.parse(raw);
    } catch { /* ignore */ }
    return {};
}
function setCodes(c) { world.setDynamicProperty(PROP, JSON.stringify(c)); }

// gift.pokemon is { dex, level } (older codes may store a bare dex number).
function pkDex(p) { return typeof p === "object" && p ? p.dex : Number(p); }
function pkLevel(p) { return typeof p === "object" && p && p.level ? p.level : 5; }

function rewardText(g) {
    const parts = [];
    if (g.coins > 0) parts.push(fmt(g.coins));
    if (g.itemId) parts.push(g.qty + "x " + g.itemName);
    if (g.pokemon) {
        const d = pkDex(g.pokemon);
        parts.push((POKENAMES[d] || ("#" + d)) + " Lv." + pkLevel(g.pokemon));
    }
    if (g.titleId) {
        const t = allTitles().find((x) => x.id === g.titleId);
        parts.push(t ? "title " + renderTitle(t) + "§r" : "a title");
    }
    return parts.join(" + ");
}

// ---------- Player redeems a code ----------
export async function redeemGift(player) {
    const form = await new ModalFormData().title("Gift Code")
        .textField("Enter code", "e.g. SUNNY2026").show(player);
    if (form.canceled || !form.formValues) return;
    const code = String(form.formValues[0]).trim().toUpperCase();
    if (!code) return;

    const codes = getCodes();
    const gift = codes[code];
    if (!gift || gift.uses <= 0) {
        player.sendMessage("§c[SunHub] Code invalid or used up.");
        return;
    }
    let redeemed = [];
    try {
        const raw = player.getDynamicProperty(REDEEMED);
        if (typeof raw === "string") redeemed = JSON.parse(raw);
    } catch { /* ignore */ }
    if (redeemed.includes(code)) {
        player.sendMessage("§e[SunHub] You already redeemed this code.");
        return;
    }
    gift.uses -= 1;
    setCodes(codes);
    redeemed.push(code);
    player.setDynamicProperty(REDEEMED, JSON.stringify(redeemed));

    if (gift.coins > 0) addCoins(player, gift.coins);
    if (gift.itemId) giveItem(player, gift.itemId, gift.qty);
    if (gift.pokemon) {
        const r = givePokemon(player, pkDex(gift.pokemon), pkLevel(gift.pokemon));
        if (!r.ok) player.sendMessage("§c[SunHub] Could not grant Pokemon: " + (r.reason || "error"));
        else if (r.where === "pc") player.sendMessage("§e[SunHub] Team full - the Pokemon went to your PC.");
    }
    if (gift.titleId) grantById(player, gift.titleId, true);
    player.playSound("random.levelup");
    player.sendMessage("§d[SunHub] Gift Code! You received: §f" + rewardText(gift));
    if (gift.titleId) player.sendMessage("§d[SunHub] Equip your new title in Hub -> Titles!");
}

// ---------- Admin create/delete codes ----------
async function pickItemBySearch(admin) {
    const form = await new ModalFormData().title("Attach an item")
        .textField("Search (name or id)", "e.g. pokeball, candy, stone...")
        .slider("Quantity", 1, 64, { defaultValue: 1 })
        .show(admin);
    if (form.canceled || !form.formValues) return null;
    const kw = String(form.formValues[0]).trim().toLowerCase();
    const qty = Math.floor(Number(form.formValues[1]));
    if (!kw) return null;
    const results = SERP_ITEMS
        .filter((it) => it.name.toLowerCase().includes(kw) || it.id.includes(kw))
        .slice(0, MAX_RESULTS);
    if (results.length === 0) {
        admin.sendMessage("§c[SunHub] No matching item: " + kw);
        return pickItemBySearch(admin);
    }
    const sel = await actionMenu(admin, "Results: " + kw, "Select item:",
        results.map((it) => ({ label: it.name + "\n§8" + it.id })));
    if (sel < 0) return null;
    return { id: results[sel].id, name: results[sel].name, qty };
}

export async function adminGifts(admin) {
    const codes = getCodes();
    const keys = Object.keys(codes);
    const sel = await actionMenu(admin, "Manage Gift Codes",
        keys.length ? keys.length + " active codes:" : "No codes yet.",
        [
            { label: "§aCreate new code", icon: "textures/items/paper" },
            ...keys.map((k) => ({
                label: k + "\n§7" + rewardText(codes[k]) + " | " + codes[k].uses + " uses left (tap to delete)"
            }))
        ]);
    if (sel < 0) return;

    if (sel === 0) {
        const form = await new ModalFormData().title("Create Gift Code")
            .textField("Code (your choice)", "e.g. SUNNY2026")
            .slider("Coin reward", 0, 5000, { valueStep: 50, defaultValue: 200 })
            .dropdown("Extra reward", ["Nothing extra", "Item (search)", "Pokemon (search + level)", "Item + Pokemon", "Title", "Item + Pokemon + Title"])
            .slider("Number of uses", 1, 500, { defaultValue: 20 })
            .show(admin);
        if (form.canceled || !form.formValues) return;
        const code = String(form.formValues[0]).trim().toUpperCase();
        if (!code) { admin.sendMessage("§c[SunHub] Code is empty."); return; }
        const coins = Math.floor(Number(form.formValues[1]));
        const extra = Number(form.formValues[2]);
        const uses = Math.floor(Number(form.formValues[3]));

        let item = null, pokemon = null, titleId = null;
        if (extra === 1 || extra === 3 || extra === 5) {
            item = await pickItemBySearch(admin);
            if (!item && extra === 1 && coins <= 0) return;
        }
        if (extra === 2 || extra === 3 || extra === 5) {
            pokemon = await pickPokemonSearch(admin, "Pokemon for this code");
            if (!pokemon && extra === 2 && coins <= 0) return;
        }
        if (extra === 4 || extra === 5) {
            const ts = allTitles();
            if (ts.length === 0) admin.sendMessage("§e[SunHub] No titles exist yet - create some in Server admin -> Titles.");
            else {
                const tsel = await actionMenu(admin, "Attach a title", "Pick the title this code grants:",
                    ts.map((t) => ({ label: renderTitle(t), icon: "textures/items/name_tag" })), "pokedex_purple");
                if (tsel >= 0) titleId = ts[tsel].id;
            }
            if (!titleId && extra === 4 && coins <= 0) return;
        }
        if (coins <= 0 && !item && !pokemon && !titleId) {
            admin.sendMessage("§c[SunHub] A code must give at least one reward.");
            return;
        }
        codes[code] = {
            coins,
            itemId: item?.id ?? null, itemName: item?.name ?? null, qty: item?.qty ?? 0,
            pokemon, titleId, uses
        };
        setCodes(codes);
        admin.sendMessage("§a[SunHub] Created code §f" + code + "§a: " + rewardText(codes[code]) + " (" + uses + " uses)");
    } else {
        const key = keys[sel - 1];
        const ok = await confirmForm(admin, "Delete code §c" + key + "§r?\n§7" + rewardText(codes[key]));
        if (!ok) return;
        delete codes[key];
        setCodes(codes);
        admin.sendMessage("§a[SunHub] Deleted code " + key);
    }
}
