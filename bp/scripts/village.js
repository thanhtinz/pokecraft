// Diem Lang: spawn khi vao sv lan dau / khi chet, va nut ve lang
import { world, system } from "@minecraft/server";

const PROP = "su:village";
const JOINED = "su:joined";

export function getVillage() {
    try {
        const raw = world.getDynamicProperty(PROP);
        if (typeof raw === "string") return JSON.parse(raw);
    } catch { /* ignore */ }
    return null;
}

export function setVillageHere(admin) {
    const l = admin.location;
    world.setDynamicProperty(PROP, JSON.stringify({
        x: Math.floor(l.x) + 0.5, y: Math.floor(l.y), z: Math.floor(l.z) + 0.5,
        dim: admin.dimension.id
    }));
    admin.sendMessage("§a[SunHub] dat diem Village tai " + Math.floor(l.x) + " " + Math.floor(l.y) + " " + Math.floor(l.z)
        + ". player chet / first join the server will spawn here.");
}

export function tpVillage(player, silent) {
    const v = getVillage();
    if (!v) {
        if (!silent) player.sendMessage("§e[SunHub] Village hasn't been set up by an admin yet.");
        return false;
    }
    try {
        player.teleport({ x: v.x, y: v.y, z: v.z }, { dimension: world.getDimension(v.dim) });
        // Dat luon spawn point ca nhan tai lang de vanilla dong bo
        player.setSpawnPoint({ x: Math.floor(v.x), y: Math.floor(v.y), z: Math.floor(v.z), dimension: world.getDimension(v.dim) });
        if (!silent) player.playSound("mob.endermen.portal");
        return true;
    } catch {
        if (!silent) player.sendMessage("§c[SunHub] Teleport to village failed.");
        return false;
    }
}

export function registerVillage() {
    world.afterEvents.playerSpawn.subscribe((ev) => {
        const p = ev.player;
        if (!getVillage()) return;

        if (ev.initialSpawn) {
            // Chi cuong che voi nguoi vao SERVER LAN DAU TIEN
            const joined = p.getDynamicProperty(JOINED);
            if (joined === true) return;
            p.setDynamicProperty(JOINED, true);
            system.runTimeout(() => {
                if (p.isValid && tpVillage(p, true))
                    p.sendMessage("§6[SunHub] Welcome to the server! You're at the Village.");
            }, 40);
        } else {
            // Chet -> hoi sinh o lang
            system.runTimeout(() => {
                if (p.isValid) tpVillage(p, true);
            }, 10);
        }
    });
}
