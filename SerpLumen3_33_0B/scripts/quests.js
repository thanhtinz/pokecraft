import { world, EntityComponentTypes } from "@minecraft/server";
import { QUESTS } from "./data_quests.js";
import { CONFIG } from "./config.js";
import { addCoins, fmt } from "./economy.js";
import { giveItem, dayNumber, strHash } from "./forms.js";

const PROP = "sunnyeco:quests";

// ---------- Chon nhiem vu moi day: seed = day + playerId ----------
// He thong tu thay doi: moi day everyone choi nhan bo nhiem vu khac nhau,
// chon deterministic tu pool 200 nhiem vu.
function pickQuestIds(player, day) {
    const n = CONFIG.questsPerDay;
    const ids = [];
    let h = strHash(player.id + ":" + day);
    const used = new Set();
    while (ids.length < n) {
        h = (h * 1103515245 + 12345) >>> 0;
        const idx = h % QUESTS.length;
        if (used.has(idx)) continue;
        used.add(idx);
        ids.push(QUESTS[idx].id);
    }
    return ids;
}

function questById(id) {
    return QUESTS.find((q) => q.id === id);
}

function getState(player) {
    const today = dayNumber();
    try {
        const raw = player.getDynamicProperty(PROP);
        if (typeof raw === "string") {
            const st = JSON.parse(raw);
            if (st.d === today) return st;
        }
    } catch { /* fallthrough */ }
    // Ngay moi -> bo nhiem vu moi, tien do 0
    const st = { d: today, ids: pickQuestIds(player, today), p: [0, 0, 0], c: [false, false, false] };
    setState(player, st);
    return st;
}

function setState(player, st) {
    player.setDynamicProperty(PROP, JSON.stringify(st));
}

export function getQuestView(player) {
    const st = getState(player);
    return st.ids.map((id, i) => {
        const q = questById(id);
        return { quest: q, progress: Math.min(st.p[i], q.count), done: st.p[i] >= q.count, claimed: st.c[i], index: i };
    });
}

export function claimQuest(player, index) {
    const st = getState(player);
    const q = questById(st.ids[index]);
    if (!q || st.c[index] || st.p[index] < q.count) return null;
    st.c[index] = true;
    setState(player, st);

    const coins = q.reward.coins * boostMult("quests");
    addCoins(player, coins);
    let msg = "+" + fmt(coins) + (boostMult("quests") > 1 ? " §6(x2 EVENT!)§r" : "");
    if (q.reward.item) {
        giveItem(player, q.reward.item.id, q.reward.item.qty);
        msg += " va " + q.reward.item.qty + "x " + q.reward.item.name;
    }
    player.playSound("random.levelup");
    return msg;
}

// ---------- Ghi nhan tien do ----------
export function progress(player, type, target) {
    const st = getState(player);
    let changed = false;
    st.ids.forEach((id, i) => {
        if (st.c[i]) return;
        const q = questById(id);
        if (!q || q.type !== type) return;
        if (q.target && q.target !== target) return;
        if (st.p[i] >= q.count) return;
        st.p[i] += 1;
        changed = true;
        if (st.p[i] >= q.count) {
            player.sendMessage("§6[SunHub] Completed quest: §f" + q.desc + "§6 - open the menu to claim rewards!");
            player.playSound("random.orb");
        }
    });
    if (changed) setState(player, st);
}

function isWild(entity) {
    try {
        const tame = entity.getComponent(EntityComponentTypes.Tameable);
        return !tame || !tame.isTamed;
    } catch { return true; }
}

function creditPlayer(damagingEntity) {
    if (!damagingEntity) return undefined;
    if (damagingEntity.typeId === "minecraft:player") return damagingEntity;
    // Pokemon cua nguoi choi ha guc -> tinh cho chu
    try {
        const tame = damagingEntity.getComponent(EntityComponentTypes.Tameable);
        if (tame && tame.isTamed) return tame.tamedToPlayer;
    } catch { /* ignore */ }
    return undefined;
}

export function registerQuestTracking() {
    // Diet entity (pokemon hoang da / mob vanilla)
    world.afterEvents.entityDie.subscribe((ev) => {
        const dead = ev.deadEntity;
        const player = creditPlayer(ev.damageSource.damagingEntity);
        if (!player) return;

        if (CONFIG.pokemonPrefixes.some((p) => dead.typeId.startsWith(p))) {
            if (!isWild(dead)) return;
            progress(player, "kill_wild_any", "");
            progress(player, "kill_species", dead.typeId);
        } else {
            progress(player, "kill_mob", dead.typeId);
        }
    });

    // Nem ball
    world.afterEvents.itemUse.subscribe((ev) => {
        if (!CONFIG.ballItems.includes(ev.itemStack.typeId)) return;
        progress(ev.source, "throw_ball", "");
    });

    // Dao/chat khoi
    world.afterEvents.playerBreakBlock.subscribe((ev) => {
        progress(ev.player, "mine_block", ev.brokenBlockPermutation.type.id);
    });
}
