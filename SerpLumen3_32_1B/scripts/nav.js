// Navigator: Homes / TPA / Spawn / Back / Random TP
import { world, system } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm } from "./forms.js";
import { tpVillage, setVillageHere } from "./village.js";
import { t } from "./i18n.js";

const HOMES_PROP = "su:homes";
import { homeClaims } from "./claims.js";
const BACK_PROP = "su:back";
const MAX_HOMES = 3;
const TPA_EXPIRE_MS = 60000;
const RTP_RANGE = 3000;

// TPA requests trong RAM: targetId -> { fromId, fromName, time }
const tpaRequests = new Map();

function getHomes(player) {
    try {
        const raw = player.getDynamicProperty(HOMES_PROP);
        if (typeof raw === "string") return JSON.parse(raw);
    } catch { /* ignore */ }
    return [];
}
function setHomes(player, homes) {
    player.setDynamicProperty(HOMES_PROP, JSON.stringify(homes));
}

function setBack(player) {
    const l = player.location;
    player.setDynamicProperty(BACK_PROP, JSON.stringify({
        x: l.x, y: l.y, z: l.z, dim: player.dimension.id
    }));
}

function tpTo(player, pos, dimId) {
    setBack(player);
    try {
        const dim = world.getDimension(dimId ?? player.dimension.id);
        player.teleport({ x: pos.x, y: pos.y, z: pos.z }, { dimension: dim });
        player.playSound("mob.endermen.portal");
        return true;
    } catch {
        player.sendMessage(t(player, "nav.tp.fail"));
        return false;
    }
}

// ---------- Homes ----------
async function homesMenu(player) {
    // your claimed land IS your home - listed first, no set-home needed
    const lands = homeClaims(player);
    const homes = getHomes(player);
    const buttons = lands.map((cl) => ({
        label: t(player, "nav.home.land.label", {
            who: cl.owner === player.id ? t(player, "nav.home.yourland") : t(player, "nav.home.otherland", { name: cl.ownerName }),
            x: Math.floor((cl.x1 + cl.x2) / 2), z: Math.floor((cl.z1 + cl.z2) / 2)
        }),
        icon: "textures/blocks/grass_side_carried",
    }));
    buttons.push(...homes.map((h) => ({
        label: h.name + "\n§7" + Math.floor(h.x) + " " + Math.floor(h.y) + " " + Math.floor(h.z)
    })));
    if (homes.length < MAX_HOMES) buttons.push({ label: t(player, "nav.home.sethere"), icon: "textures/items/bed_red" });

    const sel = await actionMenu(player, t(player, "nav.home.title"),
        (lands.length ? t(player, "nav.home.body.lands") : t(player, "nav.home.body.none")), buttons);
    if (sel < 0) return;

    if (sel < lands.length) {
        const cl = lands[sel];
        const dest = { x: Math.floor((cl.x1 + cl.x2) / 2) + 0.5, y: (cl.y ?? 100) + 1, z: Math.floor((cl.z1 + cl.z2) / 2) + 0.5 };
        if (cl.y === undefined) { try { player.addEffect("minecraft:slow_falling", 300, { amplifier: 0 }); } catch {} }
        tpTo(player, dest, cl.dim);
        return;
    }
    const landOff = lands.length;

    if (sel - landOff >= homes.length) {
        const form = await new ModalFormData().title(t(player, "nav.home.setform.title"))
            .textField(t(player, "nav.home.name.label"), t(player, "nav.home.name.ph")).show(player);
        if (form.canceled || !form.formValues) return;
        const name = String(form.formValues[0]).trim() || ("Home " + (homes.length + 1));
        const l = player.location;
        homes.push({ name, x: l.x, y: l.y, z: l.z, dim: player.dimension.id });
        setHomes(player, homes);
        player.sendMessage(t(player, "nav.home.set.ok", { name }));
        return;
    }

    const h = homes[sel - landOff];
    const act = await actionMenu(player, h.name, t(player, "nav.home.action.body"), [

        { label: t(player, "nav.home.tp") },
        { label: t(player, "nav.home.delete") }
    ]);
    if (act === 0) tpTo(player, h, h.dim);
    else if (act === 1) {
        const ok = await confirmForm(player, t(player, "nav.home.delete.confirm", { name: h.name }));
        if (!ok) return;
        homes.splice(sel - landOff, 1);
        setHomes(player, homes);
        player.sendMessage(t(player, "nav.home.deleted"));
    }
}

// ---------- TPA ----------
async function tpaMenu(player) {
    // Don yeu cau het han
    for (const [k, v] of tpaRequests) {
        if (Date.now() - v.time > TPA_EXPIRE_MS) tpaRequests.delete(k);
    }
    const incoming = tpaRequests.get(player.id);
    const buttons = [{ label: t(player, "nav.tpa.send.btn"), icon: "textures/items/ender_pearl" }];
    if (incoming) buttons.push({ label: t(player, "nav.tpa.accepted.btn", { name: incoming.fromName }) });

    const sel = await actionMenu(player, t(player, "nav.tpa.title"), t(player, "nav.tpa.body"), buttons);
    if (sel < 0) return;

    if (sel === 0) {
        const others = world.getAllPlayers().filter((p) => p.id !== player.id);
        if (others.length === 0) {
            player.sendMessage(t(player, "common.none.online"));
            return;
        }
        const psel = await actionMenu(player, t(player, "nav.tpa.who"), "", others.map((p) => ({ label: p.name })));
        if (psel < 0) return;
        const target = others[psel];
        tpaRequests.set(target.id, { fromId: player.id, fromName: player.name, time: Date.now() });
        player.sendMessage(t(player, "nav.tpa.sent", { name: target.name }));
        target.sendMessage(t(target, "nav.tpa.incoming", { name: player.name }));
        target.playSound("random.orb");
    } else if (incoming) {
        tpaRequests.delete(player.id);
        const from = world.getAllPlayers().find((p) => p.id === incoming.fromId);
        if (!from) {
            player.sendMessage(t(player, "nav.tpa.sender.off"));
            return;
        }
        tpTo(from, player.location, player.dimension.id);
        from.sendMessage(t(from, "nav.tpa.accepted.msg", { name: player.name }));
        player.sendMessage(t(player, "nav.tpa.onway", { name: from.name }));
    }
}

// ---------- Navigator chinh ----------
export async function openNavigator(player) {
    const sel = await actionMenu(player, t(player, "nav.title"), t(player, "nav.where"), [

        { label: t(player, "nav.village"), icon: "textures/blocks/bell_side" },
        { label: t(player, "nav.homes"), icon: "textures/items/bed_red" },
        { label: t(player, "nav.tpa.btn"), icon: "textures/items/ender_pearl" },
        { label: t(player, "nav.spawn"), icon: "textures/blocks/beacon" },
        { label: t(player, "nav.back"), icon: "textures/items/arrow" },
        { label: t(player, "nav.rtp"), icon: "textures/items/map_filled" },
        { label: t(player, "nav.pillars"), icon: "textures/blocks/lodestone_top" },
        { label: t(player, "nav.vehicles"), icon: "textures/items/boat_oak" }
    ], "pokedex_light_blue");
    switch (sel) {
        case 0: tpVillage(player); break;
        case 1: await homesMenu(player); break;
        case 2: await tpaMenu(player); break;
        case 3: {
            const s = world.getDefaultSpawnLocation();
            tpTo(player, { x: s.x + 0.5, y: s.y < -60 ? 100 : s.y, z: s.z + 0.5 }, "minecraft:overworld");
            break;
        }
        case 4: {
            try {
                const raw = player.getDynamicProperty(BACK_PROP);
                if (typeof raw !== "string") throw 0;
                const b = JSON.parse(raw);
                tpTo(player, b, b.dim);
            } catch {
                player.sendMessage(t(player, "nav.back.none"));
            }
            break;
        }
        case 5: {
            setBack(player);
            try {
                player.runCommand(`spreadplayers 0 0 200 ${RTP_RANGE} @s`);
                player.playSound("mob.endermen.portal");
                player.sendMessage(t(player, "nav.rtp.ok"));
            } catch {
                player.sendMessage(t(player, "nav.rtp.fail"));
            }
            break;
        }
        case 6: { const { openWaypoints } = await import("./waypoints.js"); await openWaypoints(player); break; }
        case 7: { const { openVehicles } = await import("./vehicles.js"); await openVehicles(player); break; }
    }
}

// ---------- Admin TP ----------
export async function openAdminTP(admin) {
    const sel = await actionMenu(admin, t(admin, "nav.admin.title"), t(admin, "nav.admin.body"), [

        { label: t(admin, "nav.admin.toplayer"), icon: "textures/items/ender_pearl" },
        { label: t(admin, "nav.admin.pull"), icon: "textures/items/lead" },
        { label: t(admin, "nav.admin.homes"), icon: "textures/items/bed_red" },
        { label: t(admin, "nav.admin.setvillage"), icon: "textures/blocks/bell_side" }
    ]);
    if (sel < 0) return;
    if (sel === 3) { setVillageHere(admin); return; }
    const others = world.getAllPlayers().filter((p) => p.id !== admin.id);
    if (others.length === 0) {
        admin.sendMessage(t(admin, "common.none.online"));
        return;
    }
    const psel = await actionMenu(admin, t(admin, "nav.admin.pick"), "", others.map((p) => ({ label: p.name })));
    if (psel < 0) return;
    const target = others[psel];

    if (sel === 0) tpTo(admin, target.location, target.dimension.id);
    else if (sel === 1) {
        tpTo(target, admin.location, admin.dimension.id);
        target.sendMessage(t(target, "nav.admin.pulled"));
    } else {
        const homes = getHomes(target);
        if (homes.length === 0) {
            admin.sendMessage(t(admin, "nav.admin.nohomes", { name: target.name }));
            return;
        }
        const hsel = await actionMenu(admin, t(admin, "nav.admin.homesof", { name: target.name }), t(admin, "nav.admin.homesbody"),
            homes.map((h) => ({ label: h.name + "\n§7" + Math.floor(h.x) + " " + Math.floor(h.y) + " " + Math.floor(h.z) })));
        if (hsel < 0) return;
        tpTo(admin, homes[hsel], homes[hsel].dim);
    }
}

// ---------- Back khi chet ----------
export function registerNav() {
    world.afterEvents.entityDie.subscribe((ev) => {
        if (ev.deadEntity.typeId !== "minecraft:player") return;
        try { setBack(ev.deadEntity); } catch { /* ignore */ }
    });
}
