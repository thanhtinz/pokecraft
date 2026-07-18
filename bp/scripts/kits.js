// Kits: bo qua nhan theo cooldown, item thuong max 5
import { addCoins, fmt } from "./economy.js";
import { actionMenu, giveItem, dayNumber } from "./forms.js";
import { t } from "./i18n.js";

const PROP = "se:kits";

const KITS = [
    {
        id: "starter", name: "Starter Kit", cooldownDays: -1, // -1 = one-time only
        coins: 200,
        items: [{ id: "serp:pokeball", name: "Poke Ball", qty: 5 }, { id: "serp:potion", name: "Potion", qty: 3 }]
    },
    {
        id: "weekly", name: "Weekly Kit", cooldownDays: 7,
        coins: 500,
        items: [{ id: "serp:greatball", name: "Great Ball", qty: 3 }, { id: "serp:super_potion", name: "Super Potion", qty: 2 }]
    }
];

function getState(player) {
    try {
        const raw = player.getDynamicProperty(PROP);
        if (typeof raw === "string") return JSON.parse(raw);
    } catch { /* ignore */ }
    return {};
}

export async function openKits(player) {
    const st = getState(player);
    const today = dayNumber();
    const view = KITS.map((k) => {
        const last = st[k.id];
        let status;
        if (last === undefined) status = t(player, "kits.received");
        else if (k.cooldownDays < 0) status = t(player, "kits.claimed");
        else if (today - last >= k.cooldownDays) status = t(player, "kits.received");
        else status = t(player, "kits.cooldown", { n: k.cooldownDays - (today - last) });
        const rw = [fmt(k.coins), ...k.items.map((i) => i.qty + "x " + i.name)].join(" + ");
        return { kit: k, status, rw, name: t(player, "kits.name." + k.id), ready: last === undefined || (k.cooldownDays >= 0 && today - last >= k.cooldownDays) };
    });

    const sel = await actionMenu(player, t(player, "kits.title"), t(player, "kits.body"),
        view.map((v) => ({ label: t(player, "kits.item.btn", { name: v.name, status: v.status, rw: v.rw }) })), "pokedex_purple");
    if (sel < 0) return;
    const v = view[sel];
    if (!v.ready) {
        player.sendMessage(t(player, "kits.notready"));
        return;
    }
    st[v.kit.id] = today;
    player.setDynamicProperty(PROP, JSON.stringify(st));
    addCoins(player, v.kit.coins);
    for (const it of v.kit.items) giveItem(player, it.id, it.qty);
    player.playSound("random.levelup");
    player.sendMessage(t(player, "kits.claimedmsg", { name: v.name, rw: v.rw }));
}
