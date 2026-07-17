import { world } from "@minecraft/server";
import { addCoins, fmt } from "./economy.js";
import { giveItem, dayNumber } from "./forms.js";
import { givePokemon } from "./dxgive.js";
import { suppressCatch } from "./tracker.js";
import { POKENAMES } from "./pokenames.js";

// Streak milestone Pokemon - configured by admins (Events & Gifts -> Streak
// Rewards). m7 fires at streak 7, m14 at 14, m30 at every multiple of 30.
const MILE = "sl:streakpoke"; // { m7: {dex,lvl,shiny}|null, m14, m30 }
export function milestoneCfg() {
  try { return JSON.parse(world.getDynamicProperty(MILE) ?? "{}"); } catch { return {}; }
}
export function setMilestoneCfg(c) { try { world.setDynamicProperty(MILE, JSON.stringify(c)); } catch {} }
export function milestoneLabel(mk) {
  if (!mk) return "none";
  return (POKENAMES[String(mk.dex)] ?? ("#" + mk.dex)) + " Lv." + mk.lvl + (mk.shiny ? " \uE132" : "");
}

function milestoneFor(streak) {
  const cfg = milestoneCfg();
  if (streak === 7 && cfg.m7) return { mk: cfg.m7, name: "7-day" };
  if (streak === 14 && cfg.m14) return { mk: cfg.m14, name: "14-day" };
  if (streak > 0 && streak % 30 === 0 && cfg.m30) return { mk: cfg.m30, name: streak + "-day" };
  return null;
}

const PROP = "sunnyeco:daily";

// Chu ky 7 day, thuong tang theo streak. Item max 5, khong item OP.
const REWARDS = [
    { coins: 100 },
    { coins: 150 },
    { coins: 150, item: { id: "serp:pokeball", name: "Poke Ball", qty: 2 } },
    { coins: 250 },
    { coins: 250, item: { id: "serp:potion", name: "Potion", qty: 2 } },
    { coins: 400 },
    { coins: 500, item: { id: "serp:greatball", name: "Great Ball", qty: 2 } }
];

function getState(player) {
    try {
        const raw = player.getDynamicProperty(PROP);
        if (typeof raw === "string") return JSON.parse(raw);
    } catch { /* fallthrough */ }
    return { last: -1, streak: 0 };
}

function setState(player, st) {
    player.setDynamicProperty(PROP, JSON.stringify(st));
}

export function dailyStatus(player) {
    const st = getState(player);
    const today = dayNumber();
    return {
        claimedToday: st.last === today,
        streak: st.streak,
        nextReward: REWARDS[st.streak % 7]
    };
}

export function claimDaily(player) {
    const st = getState(player);
    const today = dayNumber();
    if (st.last === today) return { ok: false, msg: "You already checked in today - come back tomorrow!" };

    // Bo lo 1 day tro len -> reset streak
    if (st.last !== today - 1) st.streak = 0;

    const reward = REWARDS[st.streak % 7];
    addCoins(player, reward.coins);
    let msg = "Check-in day " + (st.streak % 7 + 1) + "/7: +" + fmt(reward.coins);
    if (reward.item) {
        giveItem(player, reward.item.id, reward.item.qty);
        msg += " va " + reward.item.qty + "x " + reward.item.name;
    }

    st.streak += 1;
    st.last = today;
    setState(player, st);
    player.playSound("random.levelup");

    // streak milestone Pokemon
    const mile = milestoneFor(st.streak);
    if (mile) {
        suppressCatch(player.id);
        const r = givePokemon(player, mile.mk.dex, mile.mk.lvl, { shiny: !!mile.mk.shiny });
        if (r.ok) {
            msg += " §d+ " + mile.name + " streak gift: §l" + milestoneLabel(mile.mk) + "§r§d (" + (r.where === "team" ? "team" : "PC") + ")!";
            world.sendMessage("§d[STREAK] §l" + player.name + "§r§d hit a " + mile.name + " check-in streak and earned §l" + milestoneLabel(mile.mk) + "§r§d!");
        } else {
            msg += " §c(streak gift skipped - team & PC full!)";
        }
    }
    return { ok: true, msg };
}
