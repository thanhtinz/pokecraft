// Thong bao server: admin viet, nguoi choi doc, co cham (!) khi co tin moi
import { world } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm } from "./forms.js";

const PROP = "su:announces";
const READ = "su:annread";

function getAll() {
    try {
        const raw = world.getDynamicProperty(PROP);
        if (typeof raw === "string") return JSON.parse(raw);
    } catch { /* ignore */ }
    return [];
}
function setAll(list) { world.setDynamicProperty(PROP, JSON.stringify(list)); }

function fmtDate(ts) {
    const d = new Date(ts);
    return d.getDate() + "/" + (d.getMonth() + 1) + "/" + d.getFullYear();
}

/** Co announcement nguoi choi chua doc khong? */
export function hasUnread(player) {
    const list = getAll();
    if (list.length === 0) return false;
    const last = player.getDynamicProperty(READ);
    const lastTs = typeof last === "number" ? last : 0;
    return list.some((a) => a.ts > lastTs);
}

export async function openAnnounces(player) {
    const list = getAll().sort((a, b) => b.ts - a.ts);
    if (list.length === 0) {
        player.sendMessage("§e[SunHub] No announcements from admins yet.");
        return;
    }
    const last = player.getDynamicProperty(READ);
    const lastTs = typeof last === "number" ? last : 0;

    const sel = await actionMenu(player, "Announcements", "Tap to read details:",
        list.map((a) => ({
            label: (a.ts > lastTs ? "§e(!) " : "") + a.title + "\n§7" + fmtDate(a.ts) + " - " + a.author,
            icon: "textures/items/paper"
        })), "pokedex_orange");
    // Danh dau da doc het khi mo danh sach
    player.setDynamicProperty(READ, Date.now());
    if (sel < 0) return;

    const a = list[sel];
    await actionMenu(player, a.title,
        "§7" + fmtDate(a.ts) + " - " + a.author + "§r\n\n" + a.body,
        [{ label: "Dong", icon: "textures/blocks/barrier" }]);
    return openAnnounces(player);
}

export async function adminAnnounces(admin) {
    const list = getAll().sort((a, b) => b.ts - a.ts);
    const sel = await actionMenu(admin, "Manage Announcements",
        list.length ? list.length + " announcements (tap to delete):" : "No announcements.",
        [
            { label: "§aWrite a new announcement", icon: "textures/items/book_writable" },
            ...list.map((a) => ({ label: a.title + "\n§7" + fmtDate(a.ts) + " (tap to delete)", icon: "textures/items/paper" }))
        ]);
    if (sel < 0) return;

    if (sel === 0) {
        const form = await new ModalFormData().title("Write announcement")
            .textField("Title", "e.g. Server maintenance tonight")
            .textField("Content", "Write announcement content...")
            .toggle("Broadcast to everyone online", { defaultValue: true })
            .show(admin);
        if (form.canceled || !form.formValues) return;
        const title = String(form.formValues[0]).trim();
        const body = String(form.formValues[1]).trim();
        if (!title || !body) {
            admin.sendMessage("§c[SunHub] Title and content cannot be empty.");
            return;
        }
        const all = getAll();
        all.push({ title, body, author: admin.name, ts: Date.now() });
        setAll(all);
        admin.sendMessage("§a[SunHub] Posted announcement: §f" + title);
        if (form.formValues[2]) {
            world.sendMessage("§6[announcement] §f" + title + "\n§7" + body + "\n§8- " + admin.name);
            for (const p of world.getAllPlayers()) p.playSound("random.orb");
        }
    } else {
        const a = list[sel - 1];
        const ok = await confirmForm(admin, "Delete announcement §c" + a.title + "§r?");
        if (!ok) return;
        const all = getAll().filter((x) => x.ts !== a.ts);
        setAll(all);
        admin.sendMessage("§a[SunHub] Deleted announcement.");
    }
}
