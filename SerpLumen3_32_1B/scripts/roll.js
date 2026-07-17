// Roll ne don kieu Legends: cui + nhay khi dang di chuyen
import { world, system } from "@minecraft/server";
import { CONFIG } from "./config.js";

const cooldown = new Map();

export function registerRoll() {
    if (!CONFIG.rollEnabled) return;
    system.runInterval(() => {
        for (const p of world.getAllPlayers()) {
            if (!p.isSneaking || !p.isJumping) continue;
            const v = p.getVelocity();
            if (Math.abs(v.x) < 0.05 && Math.abs(v.z) < 0.05) continue;

            const last = cooldown.get(p.id) ?? 0;
            if (Date.now() - last < CONFIG.rollCooldownSec * 1000) continue;
            cooldown.set(p.id, Date.now());

            const view = p.getViewDirection();
            const len = Math.sqrt(view.x * view.x + view.z * view.z) || 1;
            try {
                p.applyKnockback({ x: (view.x / len) * CONFIG.rollPower, z: (view.z / len) * CONFIG.rollPower }, 0.35);
                p.playSound("mob.enderdragon.flap");
                p.dimension.spawnParticle("minecraft:explosion_particle", p.location);
            } catch { /* ignore */ }
        }
    }, 5);
}
