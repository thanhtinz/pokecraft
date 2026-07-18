// Thong bao server: admin viet, nguoi choi doc, co cham (!) khi co tin moi
import { world } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
import { actionMenu, confirmForm } from "./forms.js";
import { t } from "./i18n.js";

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
        player.sendMessage(t(player, "announce.none"));
        return;
    }
    const last = player.getDynamicProperty(READ);
    const lastTs = typeof last === "number" ? last : 0;

    const sel = await actionMenu(player, t(player, "announce.title"), t(player, "announce.body"),
        list.map((a) => ({
            label: (a.ts > lastTs ? t(player, "announce.unread") : "") + t(player, "announce.item", { title: a.title, date: fmtDate(a.ts), author: a.author }),
            icon: "textures/items/paper"
        })), "pokedex_orange");
    // Danh dau da doc het khi mo danh sach
    player.setDynamicProperty(READ, Date.now());
    if (sel < 0) return;

    const a = list[sel];
    await actionMenu(player, a.title,
        t(player, "announce.detail", { date: fmtDate(a.ts), author: a.author, body: a.body }),
        [{ label: t(player, "common.close"), icon: "textures/blocks/barrier" }]);
    return openAnnounces(player);
}

export async function adminAnnounces(admin) {
    const list = getAll().sort((a, b) => b.ts - a.ts);
    const sel = await actionMenu(admin, t(admin, "announce.admin.title"),
        list.length ? t(admin, "announce.admin.body", { n: list.length }) : t(admin, "announce.admin.none"),
        [
            { label: t(admin, "announce.write.btn"), icon: "textures/items/book_writable" },
            ...list.map((a) => ({ label: t(admin, "announce.admin.item", { title: a.title, date: fmtDate(a.ts) }), icon: "textures/items/paper" }))
        ]);
    if (sel < 0) return;

    if (sel === 0) {
        const form = await new ModalFormData().title(t(admin, "announce.write.title"))
            .textField(t(admin, "announce.field.title"), t(admin, "announce.field.title.ph"))
            .textField(t(admin, "announce.field.body"), t(admin, "announce.field.body.ph"))
            .toggle(t(admin, "announce.field.broadcast"), { defaultValue: true })
            .show(admin);
        if (form.canceled || !form.formValues) return;
        const title = String(form.formValues[0]).trim();
        const body = String(form.formValues[1]).trim();
        if (!title || !body) {
            admin.sendMessage(t(admin, "announce.empty"));
            return;
        }
        const all = getAll();
        all.push({ title, body, author: admin.name, ts: Date.now() });
        setAll(all);
        admin.sendMessage(t(admin, "announce.posted", { title }));
        if (form.formValues[2]) {
            for (const p of world.getAllPlayers()) {
                p.sendMessage(t(p, "announce.broadcast", { title, body, author: admin.name }));
                p.playSound("random.orb");
            }
        }
    } else {
        const a = list[sel - 1];
        const ok = await confirmForm(admin, t(admin, "announce.delete.confirm", { title: a.title }));
        if (!ok) return;
        const all = getAll().filter((x) => x.ts !== a.ts);
        setAll(all);
        admin.sendMessage(t(admin, "announce.deleted"));
    }
}
