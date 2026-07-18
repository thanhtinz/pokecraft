# SerpLumen

**Addon "SunHub" phủ lên SERP Pokedrock** — thêm một hub tiện ích đầy đủ cho server Pokémon Bedrock: kinh tế, nghề nghiệp, bảo vệ đất, danh hiệu, di chuyển nhanh và nhiều hơn nữa. **Song ngữ Anh/Việt.**

> Nền tảng: SERP Pokedrock · Minecraft Bedrock · chạy trên Realm
> Kỹ thuật: chỉ dùng `@minecraft/server` 2.6.0 (Script API stable) — không Beta API, hạn chế tối đa `runCommand`.

---

## SerpLumen là gì?

SerpLumen là một **behavior + resource pack** cài chồng lên addon SERP Pokedrock. Nó không đụng vào lối chơi Pokémon của SERP — thay vào đó bổ sung một lớp "server core" mà các server sinh tồn/đua top hay cần: ví tiền, nghề, đất đai, danh hiệu, quà, chợ… tất cả gói trong một **menu Hub** duy nhất mở bằng vật phẩm la bàn.

Toàn bộ giao diện dùng **ActionForm** khung skin SERP (`serp.main.*`) nên hiển thị đẹp và ổn định trên cả điện thoại lẫn PC.

## Tính năng chính

- **Hub trung tâm** — mở bằng la bàn, header hiển thị tên + danh hiệu + số dư + cấp nghề + chuỗi ngày (dữ liệu thật).
- **Song ngữ Anh/Việt** — mỗi người chơi tự chọn ngôn ngữ (nút La bàn trong hub), lưu riêng theo người. Mặc định Tiếng Việt.
- **Ngân hàng & chuyển tiền** — gửi lấy lãi 1%/ngày, rút, chuyển cho người khác.
- **Nghề nghiệp** (Thợ mỏ / Nông dân / Tiều phu) — XP, lên cấp, mốc đặc quyền, hợp đồng hằng ngày; chống cày lặp (khối tự đặt không tính, cây phải chín).
- **Di chuyển nhanh** — Nhà (tối đa 3 + đất cắm), TPA, về spawn, quay lại chỗ cũ/chỗ chết, dịch chuyển ngẫu nhiên, trụ waypoint, gọi phương tiện.
- **Bảo vệ đất** — cắm đất ô vuông, thêm thành viên, khoá cửa, vùng cấm quái, hiện viền; người ngoài không phá/mở rương được, vụ nổ không đụng tới.
- **Danh hiệu** — admin tạo danh hiệu (màu/gradient/biểu tượng/khung) + luật tự trao (số Pokedex, tiền, số bắt, shiny, streak, #1 mùa); người chơi đeo trên tên.
- **Quà & Giftcode**, **Điểm danh & Nhiệm vụ ngày**, **Gói định kỳ (Kits)**, **Bạn đồng hành (Buddy)**, **Chợ/Trao đổi (GTS)**, **BXH Mùa giải**.
- **Tiện ích khác** — bảng tên trên đầu Pokemon/NPC, chế độ tiết kiệm pin, khôi phục máy SERP tự động, quản lý xe (admin), hệ NPC.

## Cài đặt

1. Tải file `.mcaddon` mới nhất ở mục **[Releases](../../releases)**.
2. Mở bằng Minecraft để nhập (import) — sẽ có 1 behavior pack + 1 resource pack.
3. Trong world/realm: bật **cả BP và RP**, và **đặt SerpLumen RP nằm TRÊN SERP RP** trong danh sách resource pack (pack trên ghi đè pack dưới).
4. Yêu cầu bật **Beta APIs? Không** — chỉ cần Script API bản stable (đã có sẵn trong phiên bản game hỗ trợ).

## Tải về

Mỗi bản phát hành nằm ở tab **Releases**, tag `v<phiên bản>`, kèm file `SerpLumen_v<ver>.mcaddon` build tự động.

---

## Dành cho nhà phát triển

**Bố cục repo**
- `bp/` — behavior pack (scripts, entities, items…). Toàn bộ logic ở `bp/scripts/`.
- `rp/` — resource pack (ui, textures, models, font…).
- `bp/scripts/i18n.js` — từ điển song ngữ trung tâm (`key: { en, vi }`) + hàm `t(player, key, vars)`.
- `build.sh` — đóng gói `.mcaddon`. `.github/workflows/release.yml` — tự động phát hành.

**Build**
```bash
bash build.sh      # -> dist/SerpLumen_v<ver>.mcaddon  (node --check mọi script trước khi đóng gói)
```
Version đọc từ `bp/manifest.json`. Khi đóng gói, mỗi pack được copy vào thư mục versioned `SerpLumen<ver>B` / `SerpLumen<ver>R` bên trong zip con, rồi gộp thành `.mcaddon`.

**Phát hành tự động**
Push lên `main` có thay đổi `bp/`, `rp/` hoặc `build.sh` → GitHub Actions build lại và tạo/cập nhật GitHub Release `v<ver>` kèm file `.mcaddon`. Ra bản mới: đổi `version` trong **cả hai** manifest (`bp/` và `rp/` — header + module + dependency chéo phải khớp) rồi push.

**Thêm/sửa chữ song ngữ**
Thay chuỗi hiển thị hard-code bằng `t(player, "key")`, rồi thêm `key: { en, vi }` vào `i18n.js`. Quy ước key: `<màn>.<mục>` (vd `bank.deposit.ok`). Broadcast toàn server thì gửi từng người theo ngôn ngữ của họ. Đã song ngữ: hub, bank, jobs, nav, titles, claims; các phần còn lại đang được chuyển dần.
