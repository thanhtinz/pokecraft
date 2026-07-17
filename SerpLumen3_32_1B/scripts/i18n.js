// i18n.js - bilingual EN/VI system for SerpLumen.
//
// Usage in any file:
//   import { t, getLang, toggleLang } from "./i18n.js";
//   player.sendMessage(t(player, "hub.heal.done", { n: 6 }));
//
// - Each player's choice is stored in a dynamic property ("sl:lang").
// - t(player, key, vars) returns the string for that player's language,
//   substituting {name}-style placeholders from `vars`.
// - Default language is Vietnamese ("vi"); English ("en") is the alternate.
// - If a key is missing in the chosen language it falls back to the other
//   language, then to the key itself, so nothing ever renders blank.
//
// Adding strings: put every user-visible string here as `key: { en, vi }` and
// replace the hard-coded literal in code with t(player, "key"). This file is
// the single source of truth for wording.

const LANG_PROP = "sl:lang";
const DEFAULT_LANG = "vi";
const LANGS = ["vi", "en"];

export function getLang(player) {
    try {
        const v = player.getDynamicProperty(LANG_PROP);
        if (v === "en" || v === "vi") return v;
    } catch {}
    return DEFAULT_LANG;
}

export function setLang(player, lang) {
    if (!LANGS.includes(lang)) return;
    try { player.setDynamicProperty(LANG_PROP, lang); } catch {}
}

export function toggleLang(player) {
    const next = getLang(player) === "vi" ? "en" : "vi";
    setLang(player, next);
    return next;
}

export function langName(lang) {
    return lang === "en" ? "English" : "Tiếng Việt";
}

// interpolate {placeholders} in a template from a vars object
function interp(str, vars) {
    if (!vars) return str;
    return str.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? String(vars[k]) : m));
}

export function t(player, key, vars) {
    const lang = getLang(player);
    const entry = STRINGS[key];
    if (!entry) return interp(key, vars); // unknown key: show key, never blank
    const val = entry[lang] ?? entry.vi ?? entry.en ?? key;
    return interp(val, vars);
}

// Translate for a specific language without a player (menus building a preview)
export function tl(lang, key, vars) {
    const entry = STRINGS[key];
    if (!entry) return interp(key, vars);
    const val = entry[lang] ?? entry.vi ?? entry.en ?? key;
    return interp(val, vars);
}

// ---------------------------------------------------------------------------
// DICTIONARY. key: { en, vi }. Grouped by screen. Colour codes (§x) stay inside
// the strings so each language keeps its own formatting.
// ---------------------------------------------------------------------------
export const STRINGS = {
    // --- language switch itself ---
    "lang.button":      { en: "Language: §aEnglish\n§8Tap to switch to Vietnamese", vi: "Ngôn ngữ: §aTiếng Việt\n§8Bấm để chuyển sang English" },
    "lang.switched":    { en: "§a[SunHub] Language set to English.", vi: "§a[SunHub] Đã chuyển sang Tiếng Việt." },

    // --- hub header ---
    "hub.title":        { en: "MENU - {server}", vi: "MENU - {server}" },
    "hub.header.bal":   { en: "§f{name}{rank}§r  §aBalance: §6{coins}", vi: "§f{name}{rank}§r  §aSố dư: §6{coins}" },
    "hub.header.job":   { en: "§7Job: §fLv {lv}", vi: "§7Nghề: §fLv {lv}" },
    "hub.header.streak":{ en: "§7Streak: §f{n} days", vi: "§7Chuỗi: §f{n} ngày" },

    // --- hub buttons ---
    "hub.announces":    { en: "Announcements", vi: "Thông báo" },
    "hub.announces.new":{ en: " §e(new!)", vi: " §e(mới!)" },
    "hub.bank":         { en: "Bank & Transfer\n§8Save with interest / send to others", vi: "Ngân hàng & Chuyển tiền\n§8Gửi lấy lãi / gửi cho người khác" },
    "hub.daily.done":   { en: "Daily Check-in (done)", vi: "Điểm danh (đã nhận)" },
    "hub.daily.ready":  { en: "Daily Check-in §a(!)", vi: "Điểm danh §a(!)" },
    "hub.quests":       { en: "Daily Quests", vi: "Nhiệm vụ ngày" },
    "hub.packs":        { en: "Periodic Packs\n§8Starter / Weekly", vi: "Gói định kỳ\n§8Khởi đầu / Hàng tuần" },
    "hub.gifts.live":   { en: "Gifts §d(!)", vi: "Quà §d(!)" },
    "hub.gifts.code":   { en: "Redeem Gift Code", vi: "Nhập Giftcode" },
    "hub.travel":       { en: "Quick travel\n§8Village / Home / Den to you / Spawn", vi: "Di chuyển nhanh\n§8Làng / Nhà / Gọi Den / Spawn" },
    "hub.heal":         { en: "Heal whole team", vi: "Hồi máu cả đội" },
    "hub.leaderboards.on":  { en: "Season Leaderboards\n§8Weekly race - week {w}", vi: "BXH Mùa giải\n§8Đua hàng tuần - tuần {w}" },
    "hub.leaderboards.off": { en: "§8Season Leaderboards (off)", vi: "§8BXH Mùa giải (tắt)" },
    "hub.jobs":         { en: "Jobs\n§8Work, level up, daily contracts", vi: "Nghề nghiệp\n§8Làm việc, lên cấp, hợp đồng ngày" },
    "hub.buddy":        { en: "Buddy\n§8Partner skill, magnet, bonus drops", vi: "Bạn đồng hành\n§8Kỹ năng, hút đồ, rơi thêm vật phẩm" },
    "hub.titles":       { en: "Titles\n§8Wear a badge on your name", vi: "Danh hiệu\n§8Gắn huy hiệu lên tên" },
    "hub.claims":       { en: "Land Claims\n§8Protect your base", vi: "Bảo vệ đất\n§8Bảo vệ căn cứ của bạn" },
    "hub.plates.on":    { en: "Nameplates: §aON\n§8Names above nearby Pokemon & NPCs", vi: "Bảng tên: §aBẬT\n§8Tên trên đầu Pokemon & NPC gần đó" },
    "hub.plates.off":   { en: "Nameplates: §cOFF\n§8Names above nearby Pokemon & NPCs", vi: "Bảng tên: §cTẮT\n§8Tên trên đầu Pokemon & NPC gần đó" },
    "hub.battery.on":   { en: "Battery Saver: §aON\n§8Fewer effects - cooler phone", vi: "Tiết kiệm pin: §aBẬT\n§8Ít hiệu ứng - máy mát hơn" },
    "hub.battery.off":  { en: "Battery Saver: §8off\n§8Turn on if your phone heats up", vi: "Tiết kiệm pin: §8tắt\n§8Bật nếu máy nóng" },
    "hub.guide":        { en: "Guide\n§8How everything works", vi: "Hướng dẫn\n§8Cách mọi thứ hoạt động" },
    "hub.admin":        { en: "§cServer admin", vi: "§cQuản trị máy chủ" },

    // --- hub misc / actions ---
    "hub.gifts.title":  { en: "Gifts", vi: "Quà" },
    "hub.gifts.body":   { en: "§dA Mystery Gift is live!", vi: "§dCó Quà Bí Ẩn đang mở!" },
    "hub.gifts.claim":  { en: "§dClaim Mystery Gift §f(!)", vi: "§dNhận Quà Bí Ẩn §f(!)" },
    "hub.gifts.enter":  { en: "Enter Gift Code", vi: "Nhập Giftcode" },
    "hub.heal.wait":    { en: "§e[SunHub] Healing over time, wait §f{s}s§e.", vi: "§e[SunHub] Đang hồi máu, chờ §f{s}s§e." },
    "hub.heal.none":    { en: "§e[SunHub] None of your Pokemon nearby need healing.", vi: "§e[SunHub] Không có Pokemon nào gần đây cần hồi máu." },
    "hub.heal.done":    { en: "§a[SunHub] Healed §f{n}§a Pokemon!", vi: "§a[SunHub] Đã hồi máu §f{n}§a Pokemon!" },

    // --- generic confirm ---
    "common.confirm":   { en: "Confirm", vi: "Xác nhận" },
    "common.cancel":    { en: "Cancel", vi: "Huỷ" },

    // --- toggle result messages ---
    "msg.plates.on":    { en: "§a[SunHub] Nameplates ON - names show above nearby Pokemon & NPCs.", vi: "§a[SunHub] Đã BẬT bảng tên - hiện tên trên đầu Pokemon & NPC gần đó." },
    "msg.plates.off":   { en: "§e[SunHub] Nameplates OFF.", vi: "§e[SunHub] Đã TẮT bảng tên." },
    "msg.battery.on":   { en: "§a[SunHub] Battery Saver ON - nameplates, item magnet and border particles are off for you. Everything else works normally.", vi: "§a[SunHub] Đã BẬT tiết kiệm pin - tắt bảng tên, hút vật phẩm và hạt viền cho bạn. Mọi thứ khác vẫn chạy bình thường." },
    "msg.battery.off":  { en: "§e[SunHub] Battery Saver off - all visuals back on.", vi: "§e[SunHub] Đã TẮT tiết kiệm pin - bật lại toàn bộ hiệu ứng." },
};
