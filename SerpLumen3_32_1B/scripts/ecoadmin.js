// Admin kinh te: so du / cong tru / bank / market / gift code
import { world } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { getCoins, addCoins, spendCoins, fmt } from "./economy.js";
import { getBank, setBank } from "./bank.js";
import { adminGifts } from "./gift.js";
import { actionMenu, confirmForm } from "./forms.js";

async function pickAmount(admin, title, label) {
    const players = world.getAllPlayers();
    const res = await new ModalFormData().title(title)
        .dropdown("player", players.map((p) => p.name))
        .textField(label, "e.g. 500").show(admin);
    if (res.canceled || !res.formValues) return null;
    const target = players[res.formValues[0]];
    const amount = parseInt(String(res.formValues[1]).trim(), 10);
    if (!target || isNaN(amount) || amount < 0) {
        admin.sendMessage("§c[SunHub] Price tri is invalid.");
        return null;
    }
    return { target, amount };
}

export async function openEcoAdmin(admin) {
    const sel = await actionMenu(admin, "Manage Money", "Select an action:", [

        { label: "View everyone's money (Wallet + Bank)", icon: "textures/items/book_writable" },
        { label: "Add money to player", icon: "textures/items/emerald" },
        { label: "Subtract money player", icon: "textures/items/redstone_dust" },
        { label: "Set bank balance", icon: "textures/items/gold_ingot" },
        { label: "Manage gift codes", icon: "textures/items/paper" },
        { label: "Convert old money to SERP money\n§8Run once is enough", icon: "textures/items/gold_nugget" },
        { label: "§cWipe a player's money", icon: "textures/blocks/barrier" },
        { label: "§4RESET ALL BALANCES\n§8Everyone -> set value (inflation fix)", icon: "textures/blocks/tnt_side" }
    ]);
    switch (sel) {
        case 0: {
            const lines = world.getAllPlayers().map((p) =>
                "§e" + p.name + ": Wallet §6" + fmt(getCoins(p)) + "§e | Bank §b" + fmt(getBank(p)));
            admin.sendMessage("§6[SunHub] Balance online:\n" + lines.join("\n"));
            break;
        }
        case 1: {
            const r = await pickAmount(admin, "Add money", "Amount to add");
            if (!r) return;
            addCoins(r.target, r.amount);
            admin.sendMessage(`§a[SunHub] +§6${fmt(r.amount)}§a to §f${r.target.name}§a -> §6${fmt(getCoins(r.target))}`);
            r.target.sendMessage("§6[SunHub] You received §f" + fmt(r.amount));
            break;
        }
        case 2: {
            const r = await pickAmount(admin, "Subtract money", "Amount tru");
            if (!r) return;
            if (!spendCoins(r.target, r.amount)) {
                admin.sendMessage(`§c[SunHub] ${r.target.name} only has ${fmt(getCoins(r.target))}.`);
                return;
            }
            admin.sendMessage(`§a[SunHub] -§6${fmt(r.amount)}§a from §f${r.target.name}§a -> §6${fmt(getCoins(r.target))}`);
            break;
        }
        case 3: {
            const r = await pickAmount(admin, "Set Bank", "New bank balance");
            if (!r) return;
            setBank(r.target, r.amount);
            admin.sendMessage(`§a[SunHub] Bank of §f${r.target.name}§a = §b${fmt(r.amount)}`);
            break;
        }
        case 4: await adminGifts(admin); break;
        case 5: {
            const old = world.scoreboard.getObjective("sunny_coin");
            if (!old) {
                admin.sendMessage("§e[SunHub] No sunny_coin scoreboard found - nothing to convert.");
                break;
            }
            let converted = 0, players2 = 0;
            for (const p of world.getAllPlayers()) {
                let bal = 0;
                try { bal = old.getScore(p) ?? 0; } catch { continue; }
                if (bal <= 0) continue;
                addCoins(p, bal);
                try { old.setScore(p, 0); } catch { /* ignore */ }
                converted += bal;
                players2 += 1;
                p.sendMessage("§6[SunHub] Your old money was converted: +§f" + fmt(bal) + "§6 to money SERP.");
            }
            admin.sendMessage(`§a[SunHub] Converted §6${fmt(converted)}§a for ${players2} online players. Offline players convert on next login.`);
            break;
        }
        case 6: {
            const players = world.getAllPlayers();
            const psel = await actionMenu(admin, "Reset Balance", "Select:",
                players.map((p) => ({ label: p.name + "\n§6" + fmt(getCoins(p)) })));
            if (psel < 0) return;
            const t = players[psel];
            const ok = await confirmForm(admin, `Reset §c${t.name}§r's wallet to 0?`);
            if (!ok) return;
            spendCoins(t, getCoins(t));
            admin.sendMessage("§a[SunHub] Reset wallet of " + t.name);
            break;
        }
        case 7: {
            const form = await new ModalFormData().title("RESET ALL BALANCES")
                .textField("Set every wallet to:", "e.g. 500", { defaultValue: "0" })
                .toggle("Also reset all BANK deposits to 0", { defaultValue: true })
                .show(admin);
            if (form.canceled || !form.formValues) return;
            const value = Math.max(0, Math.floor(Number(form.formValues[0]) || 0));
            const alsoBank = !!form.formValues[1];
            if (!(await confirmForm(admin, "§4RESET THE WHOLE ECONOMY?§r\nEvery player's wallet -> §6" + fmt(value) + "§r" + (alsoBank ? "\nEvery bank deposit -> §60§r" : "") + "\n\n§cThis cannot be undone."))) return;
            if (!(await confirmForm(admin, "§4Really sure?§r Last chance."))) return;
            let n = 0;
            try {
                const ob = world.scoreboard.getObjective("money");
                if (ob) for (const part of ob.getParticipants()) { try { ob.setScore(part, value); n++; } catch { /* ignore */ } }
            } catch { /* ignore */ }
            if (alsoBank) {
                world.setDynamicProperty("sl:bankreset", String(Date.now()));
                for (const p of world.getAllPlayers()) { try { p.setDynamicProperty("se:bank", 0); } catch { /* ignore */ } }
            }
            world.sendMessage("§4[ECONOMY] §fAll balances have been reset by an admin. Every wallet is now §6" + fmt(value) + "§f." + (alsoBank ? " Bank deposits were cleared." : ""));
            admin.sendMessage("§a[SunHub] Reset " + n + " wallet entries" + (alsoBank ? " + bank epoch set (offline banks clear on next join)" : "") + ".");
            break;
        }
    }
}
