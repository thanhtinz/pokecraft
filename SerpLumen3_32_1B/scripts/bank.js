// Bank: gui/rut tien + lai suat 1%/day (tran 500/day) + chuyen tien
import { world } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { getCoins, addCoins, spendCoins, fmt } from "./economy.js";
import { actionMenu, dayNumber } from "./forms.js";

const BANK = "se:bank";
const BANKDAY = "se:bankday";
const INTEREST = 0.01;
const INTEREST_CAP = 500;

export function getBank(player) {
    // economy-wide reset: if an admin reset happened after this player's last
    // sync, their bank clears on first read (covers players who were offline).
    try {
        const epoch = Number(world.getDynamicProperty("sl:bankreset") ?? 0);
        const seen = Number(player.getDynamicProperty("se:bankseen") ?? 0);
        if (epoch > seen) {
            player.setDynamicProperty(BANK, 0);
            player.setDynamicProperty("se:bankseen", epoch);
        }
    } catch { /* ignore */ }
    const v = player.getDynamicProperty(BANK);
    return typeof v === "number" ? Math.floor(v) : 0;
}
export function setBank(player, v) {
    player.setDynamicProperty(BANK, Math.max(0, Math.floor(v)));
}

function applyInterest(player) {
    const today = dayNumber();
    const last = player.getDynamicProperty(BANKDAY);
    const lastDay = typeof last === "number" ? last : today;
    const days = Math.min(30, today - lastDay);
    player.setDynamicProperty(BANKDAY, today);
    if (days <= 0) return 0;
    const bal = getBank(player);
    const gain = Math.min(INTEREST_CAP * days, Math.floor(bal * INTEREST * days));
    if (gain > 0) setBank(player, bal + gain);
    return gain;
}

async function askAmount(player, title, max) {
    const form = await new ModalFormData().title(title)
        .textField("Amount (max " + fmt(max) + ")", "e.g. 500").show(player);
    if (form.canceled || !form.formValues) return 0;
    const n = parseInt(String(form.formValues[0]).trim(), 10);
    return (isNaN(n) || n <= 0 || n > max) ? 0 : n;
}

export async function openBank(player) {
    const gain = applyInterest(player);
    if (gain > 0) player.sendMessage("§6[SunHub] Bank interest: §f+" + fmt(gain));

    const sel = await actionMenu(player, "Sunny Bank",
        "Wallet: §6" + fmt(getCoins(player)) + "§r | Bank: §b" + fmt(getBank(player)) +
        "\n§7Interest 1%/day (max " + fmt(INTEREST_CAP) + "/day)",
        [
            { label: "Deposit to Bank", icon: "textures/items/emerald" },
            { label: "Withdraw to Wallet", icon: "textures/items/gold_ingot" },
            { label: "Transfer money to player", icon: "textures/items/paper" }
        ], "pokedex_yellow");
    switch (sel) {
        case 0: {
            const n = await askAmount(player, "Deposit", getCoins(player));
            if (!n) return;
            if (spendCoins(player, n)) {
                setBank(player, getBank(player) + n);
                player.sendMessage("§a[SunHub] Sent §6" + fmt(n) + "§a. Bank: §b" + fmt(getBank(player)));
            }
            break;
        }
        case 1: {
            const n = await askAmount(player, "Withdraw", getBank(player));
            if (!n) return;
            setBank(player, getBank(player) - n);
            addCoins(player, n);
            player.sendMessage("§a[SunHub] Withdrew §6" + fmt(n) + "§a. Wallet: §6" + fmt(getCoins(player)));
            break;
        }
        case 2: {
            const others = world.getAllPlayers().filter((p) => p.id !== player.id);
            if (others.length === 0) {
                player.sendMessage("§e[SunHub] Nobody else online.");
                return;
            }
            const psel = await actionMenu(player, "Transfer to whom?", "", others.map((p) => ({ label: p.name })));
            if (psel < 0) return;
            const target = others[psel];
            const n = await askAmount(player, "Transfer to " + target.name, getCoins(player));
            if (!n) return;
            if (spendCoins(player, n)) {
                addCoins(target, n);
                player.sendMessage("§a[SunHub] Transferred §6" + fmt(n) + "§a to §f" + target.name);
                target.sendMessage("§6[SunHub] §f" + player.name + "§6 transferred to you §f" + fmt(n));
                target.playSound("random.orb");
            }
            break;
        }
    }
}
