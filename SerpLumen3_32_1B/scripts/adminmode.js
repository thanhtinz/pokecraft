// Admin mode: Bay / Bat tu (mob danh khong trung) / Tang hinh
import { world, system, EntityComponentTypes } from "@minecraft/server";
import { actionMenu } from "./forms.js";

const TAG_FLY = "adm_fly";
const TAG_GOD = "adm_god";
const TAG_INVIS = "adm_invis";

function setFly(player, on) {
    try {
        player.runCommand("ability @s mayfly " + (on ? "true" : "false"));
        return true;
    } catch {
        player.sendMessage("§c[SunHub] Could not enable fly (world needs cheats on).");
        return false;
    }
}

export async function openAdminMode(admin) {
    const fly = admin.hasTag(TAG_FLY);
    const god = admin.hasTag(TAG_GOD);
    const invis = admin.hasTag(TAG_INVIS);
    const st = (b) => b ? "§a[ON]" : "§7[OFF]";

    const sel = await actionMenu(admin, "Admin Mode", "Tap to toggle:", [

        { label: "Fly " + st(fly) + "\n§8Double-jump to fly like creative", icon: "textures/items/elytra" },
        { label: "Enable tu " + st(god) + "\n§8Mobs deal no damage, no fire spread", icon: "textures/items/apple_golden" },
        { label: "Invisible " + st(invis) + "\n§8Mobs and players can't see you", icon: "textures/items/potion_bottle_invisibility" },
        { label: "§cTurn all off", icon: "textures/blocks/barrier" }
    ]);
    if (sel < 0) return;

    switch (sel) {
        case 0:
            if (fly) { admin.removeTag(TAG_FLY); setFly(admin, false); admin.sendMessage("§e[SunHub] Fly off."); }
            else if (setFly(admin, true)) { admin.addTag(TAG_FLY); admin.sendMessage("§a[SunHub] Fly on - double-jump."); }
            break;
        case 1:
            if (god) {
                admin.removeTag(TAG_GOD);
                try { admin.removeEffect("resistance"); admin.removeEffect("fire_resistance"); } catch { /* ignore */ }
                admin.sendMessage("§e[SunHub] God mode off.");
            } else {
                admin.addTag(TAG_GOD);
                admin.sendMessage("§a[SunHub] God mode on.");
            }
            break;
        case 2:
            if (invis) {
                admin.removeTag(TAG_INVIS);
                try { admin.removeEffect("invisibility"); } catch { /* ignore */ }
                admin.sendMessage("§e[SunHub] Invisibility off.");
            } else {
                admin.addTag(TAG_INVIS);
                admin.sendMessage("§a[SunHub] Invisibility on.");
            }
            break;
        case 3:
            admin.removeTag(TAG_FLY); admin.removeTag(TAG_GOD); admin.removeTag(TAG_INVIS);
            setFly(admin, false);
            try {
                admin.removeEffect("resistance");
                admin.removeEffect("fire_resistance");
                admin.removeEffect("invisibility");
            } catch { /* ignore */ }
            admin.sendMessage("§e[SunHub] All admin modes off.");
            break;
    }
}

export function registerAdminMode() {
    // Duy tri hieu ung moi 5 giay (hidden particles)
    system.runInterval(() => {
        for (const p of world.getAllPlayers()) {
            try {
                if (p.hasTag(TAG_GOD)) {
                    p.addEffect("resistance", 200, { amplifier: 4, showParticles: false });
                    p.addEffect("fire_resistance", 200, { amplifier: 0, showParticles: false });
                }
                if (p.hasTag(TAG_INVIS)) {
                    p.addEffect("invisibility", 200, { amplifier: 0, showParticles: false });
                }
            } catch { /* ignore */ }
        }
    }, 100);

    // Bat tu tuyet doi: neu van dinh dame thi hoi day mau day
    world.afterEvents.entityHurt.subscribe((ev) => {
        const e = ev.hurtEntity;
        if (e.typeId !== "minecraft:player" || !e.hasTag(TAG_GOD)) return;
        try {
            const hp = e.getComponent(EntityComponentTypes.Health);
            if (hp) hp.resetToMaxValue();
        } catch { /* ignore */ }
    });

    // Vao lai world: bat lai fly neu dang co tag
    world.afterEvents.playerSpawn.subscribe((ev) => {
        if (!ev.initialSpawn) return;
        const p = ev.player;
        if (p.hasTag(TAG_FLY)) {
            system.runTimeout(() => setFly(p, true), 40);
        }
    });
}
