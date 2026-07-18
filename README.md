# SerpLumen — Nhật ký công việc & Việc cần làm

**Phiên bản hiện tại:** v3.33.0
**Nền tảng:** SERP Pokedrock (addon phủ lên) · Minecraft Bedrock · chạy trên Realm
**Ràng buộc kỹ thuật cốt lõi:** chỉ dùng `@minecraft/server` 2.6.0 (API stable), **không Beta Script API**, hạn chế tối đa `runCommand` (ưu tiên Script API: events, ActionForm, tags, dynamic properties).

---

## 1. Đã hoàn thành (theo thứ tự)

### Máy & xe (vehicles / machines)
- **Reset xe (admin):** panel admin → *Reset Vehicles*. Xoá đăng ký xe của 1 người chơi, xoá refund treo, dọn entity xe sót mang tag chủ cũ. Dùng khi xe của người chơi bị mất/kẹt số lượng.
- **Khôi phục máy tự động:** panel admin → *Restore Machines*. Neo (anchor) vị trí + loại máy SERP (PC, heal/revive, trade, TM, lab, rotomarket, communicator, slot). Máy despawn (SERP không có `persistent`) sẽ tự spawn lại đúng chỗ khi có người chơi ở gần.
- **Auto-protect:** tự neo mọi máy SERP người chơi đi ngang qua, có chống trùng (adopt máy sẵn có thay vì tạo mới). Bật/tắt trong *Restore Machines*.
- **Giữ máy đứng yên:** máy bị đẩy xa >3 ô (theo phương ngang) sẽ được kéo về chỗ neo; **không** đụng trục Y để tránh đánh nhau với physics (đã sửa lỗi máy nhảy liên tục ở v3.31.6).
- **Sửa id máy lab:** trong danh sách purge, `serp:personal_lab` → `serp:mini_lab` (id thật của SERP).

### NPC
- **Sửa lỗi nhân bản NPC** (2 Nurse Joy chồng nhau): ngừng khôi phục các NPC cố định (joy/jenny/professors/store/game_corner — vốn không tự despawn); chỉ khôi phục `trainer`/`poke_npc`. Thêm dọn dẹp trùng lặp **chỉ khi hai NPC chồng khít <0.6 ô** (tránh xoá nhầm NPC hợp lệ đứng gần nhau).

### Giao diện Hub
- **Header thông tin nhân vật:** tên + huy hiệu/danh hiệu + số dư + cấp nghề + chuỗi ngày (streak). Toàn bộ là **dữ liệu thật** của SunHub, không bịa số.
- **Thử nghiệm JSON UI (đã gỡ bỏ):** từng thử dựng skin xám tùy biến theo mockup. Gỡ bỏ ở v3.32.1 vì JSON UI tùy biến `server_form` quá kén phiên bản, gây lỗi (`Unknown property [scrollbar]`, rồi rơi về UI mặc định). **Quyết định:** giữ khung skin SERP `serp.main.*` vốn ổn định.

### Song ngữ Anh/Việt (i18n) — MỚI ở v3.32.1
- Module `i18n.js`: lưu ngôn ngữ riêng từng người chơi (`sl:lang`), hàm `t(player, key, vars)` trả chuỗi đúng ngôn ngữ + chèn biến `{name}`, `{coins}`... Thiếu key → fallback, không bao giờ để trống. Mặc định **Tiếng Việt**.
- **Nút "Ngôn ngữ / Language"** trong hub (icon la bàn): bấm để đổi VI ⇄ EN, menu vẽ lại ngay.
- **Hub chính đã song ngữ đầy đủ:** tiêu đề, box thông tin, 16 nút, thông báo bật/tắt bảng tên & tiết kiệm pin.

---

## 2. Việc cần làm — Song ngữ hoá phần còn lại (i18n)

**Cách làm:** với mỗi file, thay chuỗi hiển thị hard-code bằng `t(player, "key")`, thêm cặp `{ en, vi }` vào từ điển `STRINGS` trong `i18n.js`. Quy ước key: `<màn>.<mục>` (vd `bank.deposit.ok`).

**Trạng thái:** ✅ đã xong · ⬜ chưa làm. Số = ước lượng chuỗi hiển thị (độ lớn công việc).

| File | Chuỗi | Trạng thái | Ghi chú ưu tiên |
|---|---:|:---:|---|
| `hubmain.js` | 127 | ✅ | Đã xong (mẫu tham chiếu) |
| `i18n.js` | — | ✅ | Từ điển trung tâm |
| `bank.js` | 19 | ✅ | Đã xong (v3.33.0) |
| `jobs.js` | 20 | ✅ | Đã xong (v3.33.0) — gồm tên nghề song ngữ |
| `nav.js` | 61 | ✅ | Đã xong (v3.33.0) |
| `titles.js` | 52 | ✅ | Đã xong (v3.33.0) — import i18n as T |
| `claims.js` | 81 | ✅ | Đã xong (v3.33.0) — cả UI admin |
| `buddy.js` | 38 | ⬜ | Trung bình |
| `gift.js` | 37 | ⬜ | Trung bình — giftcode |
| `announce.js` | 23 | ⬜ | Trung bình |
| `daily.js` | 12 | ⬜ | Trung bình |
| `kits.js` | 13 | ⬜ | Trung bình — gói định kỳ |
| `gts.js` | 51 | ⬜ | Trung bình — chợ/trade |
| `season.js` | 13 | ⬜ | Thấp — BXH mùa |
| `machines.js` | 35 | ⬜ | Thấp — chủ yếu admin |
| `vehicles.js` | 32 | ⬜ | Thấp — chủ yếu admin |
| `admin.js` / `ecoadmin.js` / `adminmode.js` / `serpadmin.js` | 41/41/18/16 | ⬜ | Thấp — chỉ admin thấy |
| `data_quests.js` | 300 | ⬜ | **Lớn nhất** — nội dung nhiệm vụ, tách buổi riêng |
| `items.js` / `vanillaitems.js` | 150/287 | ⬜ | Lớn — tên/mô tả vật phẩm |
| `events.js` | 79 | ⬜ | Rà kỹ, nhiều thông báo hệ thống |
| Các file còn lại (<15 chuỗi) | ~120 | ⬜ | Gộp làm cùng lượt cuối |

**Tổng ước lượng còn lại:** ~1,470 chuỗi trên ~45 file (đã xong nhóm ưu tiên 1: bank, jobs, nav, titles, claims — ~230 chuỗi).

**Lộ trình đề xuất theo buổi:**
1. Nhóm người-chơi-dùng-nhiều: `bank`, `jobs`, `nav`, `titles`, `claims` (~230 chuỗi).
2. Nhóm tính năng: `buddy`, `gift`, `daily`, `kits`, `announce`, `gts`, `season` (~190).
3. Nhóm admin: `admin`, `ecoadmin`, `machines`, `vehicles`... (~180).
4. Nội dung lớn: `data_quests`, `items`, `vanillaitems`, `events` (~800, chia nhiều buổi).
5. Quét file nhỏ còn lại + rà soát chuỗi sót.

---

## 3. Việc bỏ ngỏ / cân nhắc

- **Playtime & Bounty trong header:** mockup có, nhưng SunHub chưa có 2 dữ liệu này. Muốn hiện thật cần làm hệ thống đếm giờ chơi + bounty riêng (việc lớn, chưa làm — không hiển thị số giả).
- **UI đẹp như mockup HTML:** không khả thi bằng ActionForm; JSON UI tùy biến thì kén phiên bản và không test được từ xa. Nếu muốn theo đuổi: tìm addon JSON UI đã được test kỹ trên đúng phiên bản Minecraft đang chạy rồi ghép logic SunHub vào, thay vì tự dựng khung từ đầu.
- **Bố cục hub (gom nhóm nút):** đã bàn nhưng chưa làm — có thể gom 16 nút phẳng thành ~6 nhóm submenu nếu thấy cần gọn hơn.

---

## 4. Quy trình build (tham khảo)
1. Sửa code → `node --check` từng file → load-test bằng mock ESM (`/tmp/loadtest/`).
2. Bump version **đồng bộ cả BP và RP** (header + module + 2 dependency chéo phải khớp — lệch là game báo thiếu pack).
3. Đóng gói: mỗi pack giữ thư mục lồng `SerpLumen<ver>B/` · `SerpLumen<ver>R/` bên trong zip con, rồi gộp 2 zip thành `.mcaddon`.
4. Khi cài: đặt **SerpLumen RP trên SERP RP** trong danh sách resource pack (pack trên ghi đè pack dưới).

**Build cục bộ:** chạy `bash build.sh` → tạo `dist/SerpLumen_v<ver>.mcaddon` (đọc version từ tên thư mục pack, `node --check` mọi script trước khi đóng gói).

**Tự động release:** mỗi khi push lên `main` mà đổi pack/script, GitHub Actions (`.github/workflows/release.yml`) build lại `.mcaddon` và tạo **GitHub Release** tag `v<ver>` kèm file. Bump version (đổi tên thư mục + manifest) rồi push là có release mới; push lại cùng version thì chỉ cập nhật file đính kèm.

---
*Cập nhật ở phiên bản v3.33.0.*
