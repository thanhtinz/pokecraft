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

    // --- bank ---
    "bank.interest":       { en: "§6[SunHub] Bank interest: §f+{n}", vi: "§6[SunHub] Lãi ngân hàng: §f+{n}" },
    "bank.title":          { en: "Sunny Bank", vi: "Ngân hàng Sunny" },
    "bank.body":           { en: "Wallet: §6{wallet}§r | Bank: §b{bank}\n§7Interest 1%/day (max {cap}/day)", vi: "Ví: §6{wallet}§r | Ngân hàng: §b{bank}\n§7Lãi 1%/ngày (tối đa {cap}/ngày)" },
    "bank.deposit":        { en: "Deposit to Bank", vi: "Gửi vào ngân hàng" },
    "bank.withdraw":       { en: "Withdraw to Wallet", vi: "Rút về ví" },
    "bank.transfer":       { en: "Transfer money to player", vi: "Chuyển tiền cho người chơi" },
    "bank.deposit.title":  { en: "Deposit", vi: "Gửi tiền" },
    "bank.withdraw.title": { en: "Withdraw", vi: "Rút tiền" },
    "bank.amount.label":   { en: "Amount (max {max})", vi: "Số tiền (tối đa {max})" },
    "bank.amount.ph":      { en: "e.g. 500", vi: "vd 500" },
    "bank.deposit.ok":     { en: "§a[SunHub] Sent §6{n}§a. Bank: §b{bank}", vi: "§a[SunHub] Đã gửi §6{n}§a. Ngân hàng: §b{bank}" },
    "bank.withdraw.ok":    { en: "§a[SunHub] Withdrew §6{n}§a. Wallet: §6{wallet}", vi: "§a[SunHub] Đã rút §6{n}§a. Ví: §6{wallet}" },
    "bank.none":           { en: "§e[SunHub] Nobody else online.", vi: "§e[SunHub] Không có ai khác đang online." },
    "bank.transfer.who":   { en: "Transfer to whom?", vi: "Chuyển cho ai?" },
    "bank.transfer.title": { en: "Transfer to {name}", vi: "Chuyển cho {name}" },
    "bank.transfer.ok":    { en: "§a[SunHub] Transferred §6{n}§a to §f{name}", vi: "§a[SunHub] Đã chuyển §6{n}§a cho §f{name}" },
    "bank.transfer.recv":  { en: "§6[SunHub] §f{name}§6 transferred to you §f{n}", vi: "§6[SunHub] §f{name}§6 đã chuyển cho bạn §f{n}" },

    // --- jobs ---
    "job.name.miner":     { en: "Miner", vi: "Thợ mỏ" },
    "job.name.farmer":    { en: "Farmer", vi: "Nông dân" },
    "job.name.lumber":    { en: "Lumberjack", vi: "Tiều phu" },
    "job.contract.complete": { en: "§6[JOB] Contract complete! Claim it in Hub -> Jobs.", vi: "§6[JOB] Hoàn thành hợp đồng! Nhận ở Hub -> Nghề nghiệp." },
    "job.levelup":        { en: "§6[JOB] §l{job} level {lvl}!§r{perk}", vi: "§6[JOB] §l{job} lên cấp {lvl}!§r{perk}" },
    "job.master":         { en: "§6[JOB] §l{name}§r§6 is now a §lMASTER {job}§r§6! (+50% pay)", vi: "§6[JOB] §l{name}§r§6 giờ là §lMASTER {job}§r§6! (+50% lương)" },
    "job.actionbar":      { en: "§6+{pay} §7| {job} +§b{xp}xp", vi: "§6+{pay} §7| {job} +§b{xp}xp" },
    "job.perk.5":         { en: " §a+15% pay unlocked!", vi: " §a+15% lương đã mở!" },
    "job.perk.10":        { en: " §a10% double-drop unlocked!", vi: " §a10% rơi đôi đã mở!" },
    "job.perk.15":        { en: " §awork buff procs unlocked!", vi: " §abuff khi làm việc đã mở!" },
    "job.perk.20":        { en: " §a3rd daily contract unlocked!", vi: " §ahợp đồng thứ 3 đã mở!" },
    "job.perk.25":        { en: " §6MASTER: +50% pay!", vi: " §6MASTER: +50% lương!" },
    "job.perkdesc.5":     { en: "+15% pay", vi: "+15% lương" },
    "job.perkdesc.10":    { en: "10% double-drop", vi: "10% rơi đôi" },
    "job.perkdesc.15":    { en: "Work buff procs (Haste/Speed)", vi: "Buff khi làm việc (Haste/Speed)" },
    "job.perkdesc.20":    { en: "3rd daily contract", vi: "Hợp đồng ngày thứ 3" },
    "job.perkdesc.25":    { en: "MASTER: +50% pay + title rules", vi: "MASTER: +50% lương + luật danh hiệu" },
    "job.claimed":        { en: "§8[Claimed]", vi: "§8[Đã nhận]" },
    "job.claim":          { en: "§a[CLAIM!]", vi: "§a[NHẬN!]" },
    "job.progress":       { en: "§e[{done}/{need}]", vi: "§e[{done}/{need}]" },
    "job.contract.btn":   { en: "Contract: {block} x{need}\n{status} §r| §6{coins} §b+{xp}xp", vi: "Hợp đồng: {block} x{need}\n{status} §r| §6{coins} §b+{xp}xp" },
    "job.perks.btn":      { en: "Perks & pay info", vi: "Đặc quyền & lương" },
    "job.change.btn":     { en: "Change job\n§8Levels are kept per job", vi: "Đổi nghề\n§8Cấp được giữ riêng từng nghề" },
    "job.title":          { en: "{job}  Lv.{lvl}{master}", vi: "{job}  Lv.{lvl}{master}" },
    "job.master.suffix":  { en: " §6MASTER", vi: " §6MASTER" },
    "job.max":            { en: " (max)", vi: " (tối đa)" },
    "job.body":           { en: "XP: §b{into}/{need}§r{max}  Pay: §6x{mult}§r\nDaily contracts (reset every day):", vi: "XP: §b{into}/{need}§r{max}  Lương: §6x{mult}§r\nHợp đồng ngày (reset mỗi ngày):" },
    "job.already":        { en: "§e[JOB] Already claimed.", vi: "§e[JOB] Đã nhận rồi." },
    "job.notdone":        { en: "§e[JOB] Not done yet: {done}/{need}", vi: "§e[JOB] Chưa xong: {done}/{need}" },
    "job.paid":           { en: "§a[JOB] Contract paid: §6+{coins} §b+{xp}xp", vi: "§a[JOB] Đã trả hợp đồng: §6+{coins} §b+{xp}xp" },
    "job.perks.title":    { en: "{job} perks", vi: "Đặc quyền {job}" },
    "job.perks.body":     { en: "§7Base pay/block by rarity, contracts pay the big money. Blocks you placed yourself pay §cnothing§7; crops must be fully grown.", vi: "§7Lương cơ bản theo độ hiếm, hợp đồng mới trả nhiều. Khối bạn tự đặt trả §ckhông gì cả§7; cây trồng phải chín." },
    "job.pick.title":     { en: "Pick your job", vi: "Chọn nghề" },
    "job.pick.body":      { en: "One active job. XP and levels are saved per job - switch any time.", vi: "Một nghề đang hoạt động. XP và cấp lưu riêng từng nghề - đổi bất cứ lúc nào." },
    "job.pick.label":     { en: "{job}  §bLv.{lvl}{cur}", vi: "{job}  §bLv.{lvl}{cur}" },
    "job.pick.current":   { en: " §a(current)", vi: " §a(hiện tại)" },
    "job.switched":       { en: "§a[JOB] You are now a §l{job}§r§a (Lv.{lvl})", vi: "§a[JOB] Bạn giờ là §l{job}§r§a (Lv.{lvl})" },

    // --- generic ---
    "common.back":         { en: "Back", vi: "Quay lại" },
    "common.none.online":  { en: "§e[SunHub] Nobody else online.", vi: "§e[SunHub] Không có ai khác đang online." },

    // --- navigator ---
    "nav.tp.fail":         { en: "§c[SunHub] Teleport failed.", vi: "§c[SunHub] Dịch chuyển thất bại." },
    "nav.home.yourland":   { en: "Your land", vi: "Đất của bạn" },
    "nav.home.otherland":  { en: "{name}'s land", vi: "Đất của {name}" },
    "nav.home.land.label": { en: "§a⚑ {who}\n§7{x}, {z} §8(claim)", vi: "§a⚑ {who}\n§7{x}, {z} §8(vùng đất)" },
    "nav.home.sethere":    { en: "§a+ Set home here", vi: "§a+ Đặt nhà tại đây" },
    "nav.home.title":      { en: "Homes", vi: "Nhà" },
    "nav.home.body.lands": { en: "Your claimed land is listed first - tap to go home.", vi: "Đất bạn đã cắm được liệt kê đầu - bấm để về nhà." },
    "nav.home.body.none":  { en: "Select a home to travel to, or set a new one:", vi: "Chọn nhà để đi tới, hoặc đặt nhà mới:" },
    "nav.home.setform.title": { en: "Set home", vi: "Đặt nhà" },
    "nav.home.name.label": { en: "Home name", vi: "Tên nhà" },
    "nav.home.name.ph":    { en: "e.g. main home", vi: "vd nhà chính" },
    "nav.home.set.ok":     { en: "§a[SunHub] Home set: §f{name}", vi: "§a[SunHub] Đã đặt nhà §f{name}" },
    "nav.home.action.body": { en: "What do you want to do?", vi: "Bạn muốn làm gì?" },
    "nav.home.tp":         { en: "Teleport here", vi: "Dịch chuyển tới" },
    "nav.home.delete":     { en: "§cDelete this home", vi: "§cXoá nhà này" },
    "nav.home.delete.confirm": { en: "Delete home §c{name}§r?", vi: "Xoá nhà §c{name}§r?" },
    "nav.home.deleted":    { en: "§a[SunHub] Deleted home.", vi: "§a[SunHub] Đã xoá nhà." },
    "nav.tpa.send.btn":    { en: "Send a TPA request to another player", vi: "Gửi yêu cầu TPA tới người chơi khác" },
    "nav.tpa.accepted.btn":{ en: "§aAccepted: {name} to you", vi: "§aĐã đồng ý: {name} tới bạn" },
    "nav.tpa.title":       { en: "TPA", vi: "TPA" },
    "nav.tpa.body":        { en: "Teleport to another player (needs consent).", vi: "Dịch chuyển tới người chơi khác (cần đồng ý)." },
    "nav.tpa.who":         { en: "Send TPA to whom?", vi: "Gửi TPA cho ai?" },
    "nav.tpa.sent":        { en: "§a[SunHub] Sent TPA request to §f{name}§a (expires in 60s).", vi: "§a[SunHub] Đã gửi yêu cầu TPA tới §f{name}§a (hết hạn sau 60s)." },
    "nav.tpa.incoming":    { en: "§b[SunHub] §f{name}§b wants to teleport to you. Open menu > Navigator > TPA to accept.", vi: "§b[SunHub] §f{name}§b muốn dịch chuyển tới bạn. Mở menu > Di chuyển > TPA để đồng ý." },
    "nav.tpa.sender.off":  { en: "§e[SunHub] Sender is offline.", vi: "§e[SunHub] Người gửi đã offline." },
    "nav.tpa.accepted.msg":{ en: "§a[SunHub] {name} accepted the TPA.", vi: "§a[SunHub] {name} đã đồng ý TPA." },
    "nav.tpa.onway":       { en: "§a[SunHub] Accepted, {name} is on the way.", vi: "§a[SunHub] Đã đồng ý, {name} đang tới." },
    "nav.title":           { en: "Quick Travel", vi: "Di chuyển nhanh" },
    "nav.where":           { en: "Where to?", vi: "Đi đâu?" },
    "nav.village":         { en: "To Village\n§8Server gathering point", vi: "Tới Làng\n§8Điểm tụ họp của server" },
    "nav.homes":           { en: "My Homes\n§8Set up to 3 homes; tap to go home", vi: "Nhà của tôi\n§8Đặt tối đa 3 nhà; bấm để về" },
    "nav.tpa.btn":         { en: "Teleport to a player\n§8Send a request, they accept and you go", vi: "Dịch chuyển tới người chơi\n§8Gửi yêu cầu, họ đồng ý là tới" },
    "nav.spawn":           { en: "Return to spawn", vi: "Về spawn" },
    "nav.back":            { en: "Back to previous position\n§8including your death spot", vi: "Về vị trí trước\n§8gồm cả chỗ bạn chết" },
    "nav.rtp":             { en: "Explore randomly\n§8Fly to new lands to hunt Pokemon", vi: "Khám phá ngẫu nhiên\n§8Bay tới vùng mới săn Pokemon" },
    "nav.pillars":         { en: "Teleport Pillars\n§8Place lodestones, warp between them", vi: "Trụ dịch chuyển\n§8Đặt đá dẫn đường, dịch chuyển giữa chúng" },
    "nav.vehicles":        { en: "My Vehicles\n§8Call your boat/cart/horse to you", vi: "Phương tiện\n§8Gọi thuyền/xe/ngựa tới bạn" },
    "nav.back.none":       { en: "§e[SunHub] No previous position yet.", vi: "§e[SunHub] Chưa có vị trí trước đó." },
    "nav.rtp.ok":          { en: "§a[SunHub] Randomly teleported! Use Back to return.", vi: "§a[SunHub] Đã dịch chuyển ngẫu nhiên! Dùng Về để quay lại." },
    "nav.rtp.fail":        { en: "§c[SunHub] Random TP failed, try again.", vi: "§c[SunHub] Dịch chuyển ngẫu nhiên thất bại, thử lại." },
    "nav.admin.title":     { en: "Teleport Admin", vi: "TP Quản trị" },
    "nav.admin.body":      { en: "Select an action:", vi: "Chọn hành động:" },
    "nav.admin.toplayer":  { en: "Teleport to player", vi: "Tới người chơi" },
    "nav.admin.pull":      { en: "Pull a player to you", vi: "Kéo người chơi tới bạn" },
    "nav.admin.homes":     { en: "View a player's homes", vi: "Xem nhà của người chơi" },
    "nav.admin.setvillage":{ en: "§6Set the Village point here\n§8Players who die / newly join spawn here", vi: "§6Đặt điểm Làng tại đây\n§8Người chơi chết / mới vào sẽ spawn ở đây" },
    "nav.admin.pick":      { en: "Select a player", vi: "Chọn người chơi" },
    "nav.admin.pulled":    { en: "§e[SunHub] You were teleported by an admin.", vi: "§e[SunHub] Bạn vừa bị admin dịch chuyển." },
    "nav.admin.nohomes":   { en: "§e[SunHub] {name} has no homes.", vi: "§e[SunHub] {name} chưa có nhà." },
    "nav.admin.homesof":   { en: "Homes of {name}", vi: "Nhà của {name}" },
    "nav.admin.homesbody": { en: "Tap to teleport to:", vi: "Bấm để dịch chuyển tới:" },
};
