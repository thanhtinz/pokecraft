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
// i18n imported as T so it doesn't clash with the `t` title-object variable
import { t as T } from "./i18n.js";

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

function rewardText(viewer, g) {
    const parts = [];
    if (g.coins > 0) parts.push(fmt(g.coins));
    if (g.itemId) parts.push(g.qty + "x " + g.itemName);
    if (g.pokemon) {
        const d = pkDex(g.pokemon);
        parts.push((POKENAMES[d] || ("#" + d)) + " Lv." + pkLevel(g.pokemon));
    }
    if (g.titleId) {
        const t = allTitles().find((x) => x.id === g.titleId);
        parts.push(t ? T(viewer, "gift.reward.title", { title: renderTitle(t) }) : T(viewer, "gift.reward.atitle"));
    }
    return parts.join(" + ");
}

// ---------- Player redeems a code ----------
export async function redeemGift(player) {
    const form = await new ModalFormData().title(T(player, "gift.redeem.title"))
        .textField(T(player, "gift.redeem.field"), T(player, "gift.redeem.ph")).show(player);
    if (form.canceled || !form.formValues) return;
    const code = String(form.formValues[0]).trim().toUpperCase();
    if (!code) return;

    const codes = getCodes();
    const gift = codes[code];
    if (!gift || gift.uses <= 0) {
        player.sendMessage(T(player, "gift.invalid"));
        return;
    }
    let redeemed = [];
    try {
        const raw = player.getDynamicProperty(REDEEMED);
        if (typeof raw === "string") redeemed = JSON.parse(raw);
    } catch { /* ignore */ }
    if (redeemed.includes(code)) {
        player.sendMessage(T(player, "gift.already"));
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
        if (!r.ok) player.sendMessage(T(player, "gift.poke.fail", { reason: r.reason || "error" }));
        else if (r.where === "pc") player.sendMessage(T(player, "gift.poke.pc"));
    }
    if (gift.titleId) grantById(player, gift.titleId, true);
    player.playSound("random.levelup");
    player.sendMessage(T(player, "gift.received", { reward: rewardText(player, gift) }));
    if (gift.titleId) player.sendMessage(T(player, "gift.title.hint"));
}

// ---------- Admin create/delete codes ----------
async function pickItemBySearch(admin) {
    const form = await new ModalFormData().title(T(admin, "gift.item.title"))
        .textField(T(admin, "gift.item.search"), T(admin, "gift.item.search.ph"))
        .slider(T(admin, "gift.item.qty"), 1, 64, { defaultValue: 1 })
        .show(admin);
    if (form.canceled || !form.formValues) return null;
    const kw = String(form.formValues[0]).trim().toLowerCase();
    const qty = Math.floor(Number(form.formValues[1]));
    if (!kw) return null;
    const results = SERP_ITEMS
        .filter((it) => it.name.toLowerCase().includes(kw) || it.id.includes(kw))
        .slice(0, MAX_RESULTS);
    if (results.length === 0) {
        admin.sendMessage(T(admin, "gift.item.nomatch", { kw }));
        return pickItemBySearch(admin);
    }
    const sel = await actionMenu(admin, T(admin, "gift.item.results", { kw }), T(admin, "gift.item.select"),
        results.map((it) => ({ label: it.name + "\n§8" + it.id })));
    if (sel < 0) return null;
    return { id: results[sel].id, name: results[sel].name, qty };
}

export async function adminGifts(admin) {
    const codes = getCodes();
    const keys = Object.keys(codes);
    const sel = await actionMenu(admin, T(admin, "gift.admin.title"),
        keys.length ? T(admin, "gift.admin.body", { n: keys.length }) : T(admin, "gift.admin.none"),
        [
            { label: T(admin, "gift.create.btn"), icon: "textures/items/paper" },
            ...keys.map((k) => ({
                label: T(admin, "gift.admin.item", { code: k, reward: rewardText(admin, codes[k]), uses: codes[k].uses })
            }))
        ]);
    if (sel < 0) return;

    if (sel === 0) {
        const form = await new ModalFormData().title(T(admin, "gift.create.title"))
            .textField(T(admin, "gift.create.field"), T(admin, "gift.create.ph"))
            .slider(T(admin, "gift.create.coins"), 0, 5000, { valueStep: 50, defaultValue: 200 })
            .dropdown(T(admin, "gift.create.extra"), [T(admin, "gift.extra.0"), T(admin, "gift.extra.1"), T(admin, "gift.extra.2"), T(admin, "gift.extra.3"), T(admin, "gift.extra.4"), T(admin, "gift.extra.5")])
            .slider(T(admin, "gift.create.uses"), 1, 500, { defaultValue: 20 })
            .show(admin);
        if (form.canceled || !form.formValues) return;
        const code = String(form.formValues[0]).trim().toUpperCase();
        if (!code) { admin.sendMessage(T(admin, "gift.create.empty")); return; }
        const coins = Math.floor(Number(form.formValues[1]));
        const extra = Number(form.formValues[2]);
        const uses = Math.floor(Number(form.formValues[3]));

        let item = null, pokemon = null, titleId = null;
        if (extra === 1 || extra === 3 || extra === 5) {
            item = await pickItemBySearch(admin);
            if (!item && extra === 1 && coins <= 0) return;
        }
        if (extra === 2 || extra === 3 || extra === 5) {
            pokemon = await pickPokemonSearch(admin, T(admin, "gift.create.pokemon"));
            if (!pokemon && extra === 2 && coins <= 0) return;
        }
        if (extra === 4 || extra === 5) {
            const ts = allTitles();
            if (ts.length === 0) admin.sendMessage(T(admin, "gift.notitles"));
            else {
                const tsel = await actionMenu(admin, T(admin, "gift.title.attach"), T(admin, "gift.title.pick"),
                    ts.map((t) => ({ label: renderTitle(t), icon: "textures/items/name_tag" })), "pokedex_purple");
                if (tsel >= 0) titleId = ts[tsel].id;
            }
            if (!titleId && extra === 4 && coins <= 0) return;
        }
        if (coins <= 0 && !item && !pokemon && !titleId) {
            admin.sendMessage(T(admin, "gift.create.noreward"));
            return;
        }
        codes[code] = {
            coins,
            itemId: item?.id ?? null, itemName: item?.name ?? null, qty: item?.qty ?? 0,
            pokemon, titleId, uses
        };
        setCodes(codes);
        admin.sendMessage(T(admin, "gift.created", { code, reward: rewardText(admin, codes[code]), uses }));
    } else {
        const key = keys[sel - 1];
        const ok = await confirmForm(admin, T(admin, "gift.delete.confirm", { key, reward: rewardText(admin, codes[key]) }));
        if (!ok) return;
        delete codes[key];
        setCodes(codes);
        admin.sendMessage(T(admin, "gift.deleted", { key }));
    }
}
