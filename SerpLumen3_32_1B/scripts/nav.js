// Navigator: Homes / TPA / Spawn / Back / Random TP
import { world, system } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm } from "./forms.js";
import { tpVillage, setVillageHere } from "./village.js";

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
        player.sendMessage("§c[SunHub] Teleport that bai.");
        return false;
    }
}

// ---------- Homes ----------
async function homesMenu(player) {
    // your claimed land IS your home - listed first, no set-home needed
    const lands = homeClaims(player);
    const homes = getHomes(player);
    const buttons = lands.map((cl) => ({
        label: "§a⚑ " + (cl.owner === player.id ? "Your land" : cl.ownerName + "'s land") + "\n§7" + Math.floor((cl.x1 + cl.x2) / 2) + ", " + Math.floor((cl.z1 + cl.z2) / 2) + " §8(claim)",
        icon: "textures/blocks/grass_side_carried",
    }));
    buttons.push(...homes.map((h) => ({
        label: h.name + "\n§7" + Math.floor(h.x) + " " + Math.floor(h.y) + " " + Math.floor(h.z)
    })));
    if (homes.length < MAX_HOMES) buttons.push({ label: "§a+ Set home here", icon: "textures/items/bed_red" });

    const sel = await actionMenu(player, "Homes",
        (lands.length ? "Your claimed land is listed first - tap to go home." : "Select a home to travel to, or set a new one:"), buttons);
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
        const form = await new ModalFormData().title("Set home")
            .textField("Home name", "e.g. home chinh").show(player);
        if (form.canceled || !form.formValues) return;
        const name = String(form.formValues[0]).trim() || ("Home " + (homes.length + 1));
        const l = player.location;
        homes.push({ name, x: l.x, y: l.y, z: l.z, dim: player.dimension.id });
        setHomes(player, homes);
        player.sendMessage("§a[SunHub] dat home §f" + name);
        return;
    }

    const h = homes[sel - landOff];
    const act = await actionMenu(player, h.name, "What do you want to do?", [

        { label: "Teleport den" },
        { label: "§cDelete this home" }
    ]);
    if (act === 0) tpTo(player, h, h.dim);
    else if (act === 1) {
        const ok = await confirmForm(player, "Delete home §c" + h.name + "§r?");
        if (!ok) return;
        homes.splice(sel - landOff, 1);
        setHomes(player, homes);
        player.sendMessage("§a[SunHub] Deleted home.");
    }
}

// ---------- TPA ----------
async function tpaMenu(player) {
    // Don yeu cau het han
    for (const [k, v] of tpaRequests) {
        if (Date.now() - v.time > TPA_EXPIRE_MS) tpaRequests.delete(k);
    }
    const incoming = tpaRequests.get(player.id);
    const buttons = [{ label: "Send a TPA request to another player", icon: "textures/items/ender_pearl" }];
    if (incoming) buttons.push({ label: "§aAccepted: " + incoming.fromName + " to you" });

    const sel = await actionMenu(player, "TPA", "Teleport to another player (needs consent).", buttons);
    if (sel < 0) return;

    if (sel === 0) {
        const others = world.getAllPlayers().filter((p) => p.id !== player.id);
        if (others.length === 0) {
            player.sendMessage("§e[SunHub] Nobody else online.");
            return;
        }
        const psel = await actionMenu(player, "Send TPA to whom?", "", others.map((p) => ({ label: p.name })));
        if (psel < 0) return;
        const target = others[psel];
        tpaRequests.set(target.id, { fromId: player.id, fromName: player.name, time: Date.now() });
        player.sendMessage("§a[SunHub] Sent TPA request to §f" + target.name + "§a (expires in 60s).");
        target.sendMessage("§b[SunHub] §f" + player.name + "§b wants to teleport to you. Open menu > Navigator > TPA to accept.");
        target.playSound("random.orb");
    } else if (incoming) {
        tpaRequests.delete(player.id);
        const from = world.getAllPlayers().find((p) => p.id === incoming.fromId);
        if (!from) {
            player.sendMessage("§e[SunHub] Sender is offline.");
            return;
        }
        tpTo(from, player.location, player.dimension.id);
        from.sendMessage("§a[SunHub] " + player.name + " accepted the TPA.");
        player.sendMessage("§a[SunHub] Accepted, " + from.name + " is on the way.");
    }
}

// ---------- Navigator chinh ----------
export async function openNavigator(player) {
    const sel = await actionMenu(player, "Quick Travel", "Where to?", [

        { label: "To Village\n§8Server gathering point", icon: "textures/blocks/bell_side" },
        { label: "My Homes\n§8Set up to 3 homes; tap to go home", icon: "textures/items/bed_red" },
        { label: "Den to you be\n§8Send request, you dong y la den", icon: "textures/items/ender_pearl" },
        { label: "Return to spawn (Spawn)", icon: "textures/blocks/beacon" },
        { label: "Back to previous position\n§8including your death spot", icon: "textures/items/arrow" },
        { label: "Explore randomly\n§8Fly to new lands to hunt Pokemon", icon: "textures/items/map_filled" },
        { label: "Teleport Pillars\n§8Place lodestones, warp between them", icon: "textures/blocks/lodestone_top" },
        { label: "My Vehicles\n§8Call your boat/cart/horse to you", icon: "textures/items/boat_oak" }
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
                player.sendMessage("§e[SunHub] No previous position yet.");
            }
            break;
        }
        case 5: {
            setBack(player);
            try {
                player.runCommand(`spreadplayers 0 0 200 ${RTP_RANGE} @s`);
                player.playSound("mob.endermen.portal");
                player.sendMessage("§a[SunHub] Randomly teleported! Use Back to return.");
            } catch {
                player.sendMessage("§c[SunHub] Random TP that bai, thu lai.");
            }
            break;
        }
        case 6: { const { openWaypoints } = await import("./waypoints.js"); await openWaypoints(player); break; }
        case 7: { const { openVehicles } = await import("./vehicles.js"); await openVehicles(player); break; }
    }
}

// ---------- Admin TP ----------
export async function openAdminTP(admin) {
    const sel = await actionMenu(admin, "Teleport Admin", "Select an action:", [

        { label: "Den to player", icon: "textures/items/ender_pearl" },
        { label: "Pull a player to you", icon: "textures/items/lead" },
        { label: "View a player's homes", icon: "textures/items/bed_red" },
        { label: "§6Set the Village point here\n§8Players who die / newly join sv spawn here", icon: "textures/blocks/bell_side" }
    ]);
    if (sel < 0) return;
    if (sel === 3) { setVillageHere(admin); return; }
    const others = world.getAllPlayers().filter((p) => p.id !== admin.id);
    if (others.length === 0) {
        admin.sendMessage("§e[SunHub] Nobody else online.");
        return;
    }
    const psel = await actionMenu(admin, "Select a player", "", others.map((p) => ({ label: p.name })));
    if (psel < 0) return;
    const target = others[psel];

    if (sel === 0) tpTo(admin, target.location, target.dimension.id);
    else if (sel === 1) {
        tpTo(target, admin.location, admin.dimension.id);
        target.sendMessage("§e[SunHub] You were teleported by an admin.");
    } else {
        const homes = getHomes(target);
        if (homes.length === 0) {
            admin.sendMessage("§e[SunHub] " + target.name + " no homes.");
            return;
        }
        const hsel = await actionMenu(admin, "Homes of " + target.name, "Tap to teleport to:",
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
