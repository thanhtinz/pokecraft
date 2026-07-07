# Project Status

Last updated: 2026-07-07 (all-in-one: full Gen 1 dex, NPCs, menu panel)

## Legend
- [x] Done - implemented in this build
- [~] Partial - works but simplified, needs expansion
- [ ] Not done - planned, see ROADMAP.md

---

## Core Data System

- [x] Species registry loaded from JSON (`plugins/PokeCraft/species/*.json`)
- [x] Move registry loaded from JSON (`moves/moves.json`)
- [x] Bundled species/moves auto-extracted when missing (per file)
- [x] Full 18-type effectiveness chart (super effective / not very / immune)
- [x] Base stats (HP/Atk/Def/SpA/SpD/Spe) per species
- [x] Gen 3+ stat formula with IVs (0-31) and natures (25 natures, +10%/-10%)
- [x] Exp curve medium-fast (level^3), max level 100
- [x] Learnset per level, auto-keeps 4 newest moves
- [x] Shiny roll (config `battle.shiny-rate`, default 1/4096)
- [x] Move secondary effects: stat stage changes + status infliction w/ chance
- [x] Move priority (Quick Attack)
- [x] Species dataset: FULL GEN 1 - 151 pokemon (script tools/generate_gen1.py,
      base stats chuẩn, spawn theo biome/hệ, evolution chains đầy đủ)
- [x] Tiến hóa bằng đá: Thunder/Fire/Water/Leaf/Moon Stone (mua ở shop);
      hỗ trợ nhiều nhánh (Eevee -> Vaporeon/Jolteon/Flareon)
- [x] Trade evolution đổi thành level 36 (Alakazam/Machamp/Golem/Gengar)
- [x] Move dataset: 89 moves phủ đủ 18 hệ
- [ ] EVs (effort values)
- [ ] Abilities
- [ ] Held items
- [ ] Genders + breeding compatibility data

## Spawning

- [x] Biome-weighted wild spawn quanh player (interval, radius, cap trong config)
- [x] Level range per species per spawn entry
- [x] Ground-check + height-check khi chọn vị trí spawn
- [x] Active despawn sweep: `despawn-seconds` (tuổi tối đa) + `despawn-distance`
      (xa mọi player) - bỏ qua pokemon đang trong battle
- [ ] Điều kiện spawn theo thời gian (ngày/đêm) và thời tiết
- [ ] Spawn dưới nước / trên không (hiện chỉ spawn trên mặt đất)
- [ ] Legendary spawn event + broadcast

## Rendering / Bedrock

- [x] ModelEngine hook qua reflection (softdepend, không crash khi thiếu)
- [x] Fallback vanilla base entity (HUSK, đổi được trong config) + nametag
- [x] Wild entity không bốc cháy ban ngày, không tấn công/chase player,
      không nhận damage môi trường, luôn là zombie trưởng thành
- [x] Floodgate detection qua reflection
- [x] Native Bedrock SimpleForm (Cumulus) cho battle menu + switch menu (PP, status)
- [x] Chest GUI fallback cho mọi client (Geyser tự dịch)
- [ ] Model .bbmodel cho pokemon (phải tự làm trong BlockBench - KHÔNG rip asset)
- [ ] Bedrock custom item texture cho pokeball (Geyser custom item mappings)
- [ ] Hitbox scale theo species
- [ ] Animation state (idle/walk/faint) binding với ModelEngine

## Capture

- [x] 4 loại ball (Poke/Great/Ultra/Master) - snowball + CustomModelData + PDC
- [x] Công thức bắt scale theo catchRate, ball bonus, HP còn lại
- [x] Status condition bonus (SLP/FRZ x2, BRN/PAR/PSN x1.5)
- [x] Master Ball luôn thành công
- [x] Damage + status trong battle sync vào entity PDC (đánh yếu/gây status dễ bắt hơn)
- [x] Bắt thành công khi đang battle sẽ kết thúc battle sạch sẽ
- [x] Party đầy thì tự chuyển vào PC box
- [ ] Animation ném ball / ball rung 3 lần
- [ ] Craft recipe cho pokeball

## Battle

- [x] Battle hoang dã 1v1 (đấm pokemon hoang để mở, đấm lại để mở lại menu)
- [x] Turn order: move priority trước, rồi speed (tính stat stages + paralysis)
- [x] Damage formula gen-style: STAB, type chart, crit 1/24, roll 0.85-1.0
- [x] Stat stages -6..+6 (Growl/Tail Whip/Growth/Withdraw/Agility...)
- [x] Status conditions: BURN (1/16, halve physical), POISON (1/8),
      PARALYSIS (speed 1/2, 25% skip), SLEEP (1-3 turns), FREEZE (20% thaw)
- [x] PP tracking per move, hết PP toàn bộ thì Struggle (recoil 1/4 max HP)
- [x] Accuracy check / miss
- [x] Wild AI chọn move ngẫu nhiên trong số move còn PP
- [x] Exp khi thắng (yield * level / 7 * multiplier), level up giữa trận
- [x] Evolution theo level sau khi thắng (đủ species cho 4 evolution line)
- [x] Run/flee
- [x] Switch pokemon giữa trận (mất lượt); faint thì bắt buộc chọn pokemon mới
      qua GUI (Java + Bedrock form)
- [x] PvP duel 1v1 (/poke duel): 2 người chọn move đồng thời, resolve theo
      priority/speed, đủ status/stages/PP/switch/forfeit, thưởng tiền người thắng
- [x] Trainer NPC battle theo đội 2-4 pokemon (không cần Citizens), thưởng tiền,
      cooldown tái đấu, exp từng con khi hạ
- [ ] Dùng item giữa trận (ball ném được; potion chỉ dùng ngoài trận)
- [ ] Battle theo turn timeout (chống AFK)
- [ ] Stat stages cho accuracy/evasion

## Party / Storage

- [x] Party 6 slot + PC box per player
- [x] Cache in-memory, load async khi join, save async khi quit + khi tắt server
- [x] SQLite mặc định (shaded JDBC), MySQL qua config
- [x] Pokemon lưu dạng JSON row, index theo owner (PP/status persist)
- [x] Party GUI: xem chi tiết (HP, type, nature, exp, moves+PP, status),
      click-chọn rồi click slot khác để đổi chỗ, gửi vào PC, mở PC box
- [x] PC box GUI phân trang (45/trang), click để rút về party
- [x] /poke nickname, /poke release (xác nhận 2 lần trong 15s)
- [ ] Trade giữa 2 player

## Economy / Shop

- [x] PokeDollar per player (bảng players, số dư khởi điểm config)
- [x] Thưởng tiền khi thắng wild battle (level * config) và PvP
- [x] /poke balance, /poke pay <player> <amount>
- [x] Pokemart GUI (/poke shop): ball bundle, 3 loại potion, Thunder Stone
- [x] Potion dùng ngoài trận (right-click -> chọn pokemon; hồi được cả pokemon đã faint)
- [ ] Vault integration
- [ ] Bán đồ / bán pokemon lấy tiền

## Leaderboard

- [x] /poke top [caught|money|wins|pvp] - top 10 từ DB

## Social

- [x] Kết hôn: /poke marry <player|accept|deny>, /poke divorce
- [x] Bonus EXP battle (config marriage.exp-bonus) khi vợ/chồng cùng online
- [ ] Quà cưới / teleport tới vợ chồng

## Daycare / Breeding

- [x] /poke daycare deposit|withdraw|status (tối đa 2 slot, config)
- [x] EXP thụ động theo phút khi gửi (config exp-per-minute)
- [x] Lai tạo: 2 pokemon cùng dòng tiến hóa -> có xác suất ra con non Lv.1
      dạng gốc (base form), thừa hưởng 3 IV từ bố mẹ, shiny rate x2
- [ ] Egg item + đi bộ để nở (hiện nở ngay)
- [ ] Giới tính / everstone / ditto

## Riding

- [x] /poke ride <slot> - cưỡi pokemon của mình, đi theo hướng nhìn
- [x] Pokemon hệ FLYING bay được (config ride.allow-fly)
- [x] Sneak để xuống, tự dismount khi vào battle/quit
- [ ] Điều khiển WASD thật (cần saddle-entity hack), tốc độ theo chỉ số Speed

## Pokedex

- [x] Theo dõi seen/caught per player (bảng pokedex): gặp trong battle = seen,
      bắt/lai tạo/tiến hóa/sở hữu = caught
- [x] GUI phân trang theo số dex: caught hiện đủ stats/evolution/spawn biome,
      seen hiện tên, chưa gặp hiện "???"
- [x] /poke dex + nút trên menu panel, đếm tiến độ x/151
- [ ] Phần thưởng hoàn thành dex (milestone rewards)

## In-game Menu (PC + Mobile)

- [x] Item menu (Nether Star) phát khi join, right-click/tap để mở panel
      (không cần gõ lệnh; chest GUI nên Bedrock/Geyser dùng được luôn)
- [x] Hub: Party / PC Box / Shop / Daycare / Ride / Duel / Leaderboard /
      Pokedex / Marriage / Balance
- [x] GUI chọn người chơi cho Duel (lọc theo khoảng cách) và Marry
- [x] GUI chọn pokemon để cưỡi; GUI daycare gửi/rút trực quan
- [x] Leaderboard panel: top 10 của 4 hạng mục trong lore
- [x] Nút Accept ngay trên menu khi có lời thách đấu / cầu hôn đang chờ
- [x] /poke (không tham số) và /poke menu cũng mở panel
- [ ] Custom UI texture pack (hiện dùng icon vanilla item)

## Commands / Admin

- [x] /poke party (mở GUI), /poke pc, /poke shop
- [x] /poke balance | pay | top | duel | marry | divorce | daycare | ride
- [x] /poke nickname <slot> <name|off>
- [x] /poke release <party|pc> <n> (confirm)
- [x] /poke give <ball> - admin
- [x] /poke spawn <species> [level] - admin
- [x] /poke heal - admin (full HP + PP + status)
- [x] /poke reload - admin
- [x] Tab completion
- [x] NPC hệ thống: /poke npc create <healer|vendor|trainer> (admin)
      - Healer = Pokecenter (heal miễn phí), Vendor = mở shop, Trainer = đấu đội
- [x] Legendary (Articuno/Zapdos/Moltres/Mewtwo/Mew) không spawn hoang -
      admin spawn hoặc event sau này
- [ ] Web admin panel + REST API

## Infrastructure

- [x] Gradle build (Java 21, Paper 1.21.4 API, repos: PaperMC/Lumine/OpenCollab)
- [x] Gradle wrapper committed
- [x] plugin.yml softdepend: ModelEngine, floodgate, Geyser-Spigot
- [x] Docs riêng: ARCHITECTURE / SETUP / ROADMAP / STATUS
- [x] Unit tests (JUnit 5): DamageCalculator, ExperienceCurve, PokemonInstance,
      type chart, Breeding (27 tests)
- [x] CI workflow (GitHub Actions: build jar + test + upload artifact)
- [~] Compile verify: logic thuần đã verify local; full build chạy trên CI
      (sandbox dev chặn repo PaperMC)

---

## Known issues / notes

1. Growl-type stat moves không có chance rời (hiệu ứng stat luôn áp dụng khi
   trúng); statusChance chỉ áp dụng cho status condition.
2. Status của wild pokemon tồn tại qua PDC; status của party pokemon persist
   trong DB (đúng thiết kế - heal ở /poke heal).
3. Base entity HUSK đã được chặn combust/target/damage - không cần đổi entity.
4. Bedrock form khi bị dismiss lúc bắt buộc switch: đấm lại wild pokemon để
   mở lại menu chọn.
