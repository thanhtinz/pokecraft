// Cham Pokemon bang item menu: the thong tin hop nhat
import { world, system, EntityComponentTypes } from "@minecraft/server";
import { CONFIG } from "./config.js";
import { isAdmin, run, confirmForm, actionMenu, defer } from "./forms.js";
import { isPokemon, isWild, ownerOf, displayName, hp, friendship, friendshipRank } from "./poke.js";

const TMP_TAG = "sunnyui_target";

function infoLines(target) {
    const h = hp(target);
    const wild = isWild(target);
    const owner = ownerOf(target);
    const fr = friendship(target);
    return [
        "§eLoai: §f" + target.typeId,
        h ? "§eHP: §f" + h.cur + " / " + h.max : "",
        "§eStatus: " + (wild ? "§aWild" : "§b" + (owner ? "Owner: " + owner.name : "Owned")),
        !wild ? "§eThan thiet: " + friendshipRank(fr) + " §7(" + fr + " diem)" : ""
    ].filter(Boolean).join("\n");
}

function pet(player, target, name) {
    try {
        for (let i = 0; i < 5; i++) {
            target.dimension.spawnParticle("minecraft:heart_particle", {
                x: target.location.x + (Math.random() - 0.5),
                y: target.location.y + 1 + Math.random() * 0.5,
                z: target.location.z + (Math.random() - 0.5)
            });
        }
        player.playSound("random.pop");
        target.setDynamicProperty("sd:fr", friendship(target) + 1);
        player.sendMessage("§d[SunHub] " + name + " really likes being petted! (+1 friendship)");
    } catch { /* ignore */ }
}

function healOne(player, target, name) {
    const h = hp(target);
    if (!h) return;
    if (h.cur >= h.max) {
        player.sendMessage("§e[SunHub] " + name + " is at full HP.");
        return;
    }
    try {
        h.comp.resetToMaxValue();
        target.dimension.spawnParticle("minecraft:heart_particle", {
            x: target.location.x, y: target.location.y + 1, z: target.location.z
        });
        player.playSound("random.levelup");
        player.sendMessage("§a[SunHub] Healed mau day to §f" + name);
    } catch { /* ignore */ }
}

async function ownerCard(player, target) {
    const name = displayName(target);
    const sel = await actionMenu(player, name, infoLines(target), [
        { label: "Heal", icon: "textures/items/potion_bottle_heal" },
        { label: "Pet", icon: "textures/items/feather" }
    ]);
    if (!target.isValid) return;
    if (sel === 0) healOne(player, target, name);
    else if (sel === 1) pet(player, target, name);
}

async function adminCard(player, target) {
    const name = displayName(target);
    const sel = await actionMenu(player, name + " §7(Admin)", infoLines(target), [
        { label: "Heal", icon: "textures/items/potion_bottle_heal" },
        { label: "Pet", icon: "textures/items/feather" },
        { label: "Recall (serp:pokereturn)", icon: "textures/items/snowball" },
        { label: "§cXoa entity", icon: "textures/blocks/barrier" }
    ]);
    if (!target.isValid) return;
    switch (sel) {
        case 0: healOne(player, target, name); break;
        case 1: pet(player, target, name); break;
        case 2: {
            target.addTag(TMP_TAG);
            const ok = run(player, `serp:pokereturn @e[tag=${TMP_TAG},c=1] @s`);
            system.runTimeout(() => { if (target.isValid) target.removeTag(TMP_TAG); }, 2);
            if (ok) player.sendMessage("§a[SunHub] Recalled §f" + name + "§a.");
            break;
        }
        case 3: {
            const ok = await confirmForm(player, "Delete §c" + name + "§r khoi world?");
            if (ok && target.isValid) {
                target.remove();
                player.sendMessage("§a[SunHub] Deleted " + name);
            }
            break;
        }
    }
}

export function registerInteract() {
    world.beforeEvents.playerInteractWithEntity.subscribe((ev) => {
        const { player, target, itemStack } = ev;
        if (!itemStack || itemStack.typeId !== CONFIG.hubItem) return;
        if (!isPokemon(target.typeId)) return;
        ev.cancel = true;

        if (isAdmin(player)) {
            defer(() => adminCard(player, target));
            return;
        }
        const owner = ownerOf(target);
        if (owner && owner.id === player.id) defer(() => ownerCard(player, target));
        else defer(() => player.sendMessage("§b[SunHub] " + displayName(target) + "\n" + infoLines(target)));
    });
}
