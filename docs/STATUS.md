# Project Status

Last updated: 2026-07-06 (Phase 1 scaffold)

## Legend
- [x] Done - implemented in this build
- [~] Partial - works but simplified, needs expansion
- [ ] Not done - planned, see ROADMAP.md

---

## Core Data System

- [x] Species registry loaded from JSON (`plugins/PokeCraft/species/*.json`)
- [x] Move registry loaded from JSON (`moves/moves.json`)
- [x] Full 18-type effectiveness chart (super effective / not very / immune)
- [x] Base stats (HP/Atk/Def/SpA/SpD/Spe) per species
- [x] Gen 3+ stat formula with IVs (0-31) and natures (25 natures, +10%/-10%)
- [x] Exp curve medium-fast (level^3), max level 100
- [x] Learnset per level, auto-keeps 4 newest moves
- [x] Shiny roll (config `battle.shiny-rate`, default 1/4096)
- [~] Species dataset: only 5 mẫu (bulbasaur, charmander, squirtle, pidgey, pikachu)
- [~] Move dataset: only 14 moves
- [ ] EVs (effort values)
- [ ] Abilities
- [ ] Held items
- [ ] Genders + breeding compatibility data

## Spawning

- [x] Biome-weighted wild spawn quanh player (interval, radius, cap trong config)
- [x] Level range per species per spawn entry
- [x] Ground-check + height-check khi chọn vị trí spawn
- [~] Despawn: entity không persistent nên tự mất khi unload chunk, chưa có timer chủ động (config `despawn-seconds` đã khai báo nhưng chưa dùng)
- [ ] Điều kiện spawn theo thời gian (ngày/đêm) và thời tiết
- [ ] Spawn dưới nước / trên không (hiện chỉ spawn trên mặt đất)
- [ ] Legendary spawn event + broadcast

## Rendering / Bedrock

- [x] ModelEngine hook qua reflection (softdepend, không crash khi thiếu)
- [x] Fallback vanilla base entity (HUSK, đổi được trong config) + nametag
- [x] Floodgate detection qua reflection
- [x] Native Bedrock SimpleForm (Cumulus) cho battle menu
- [x] Chest GUI fallback cho mọi client (Geyser tự dịch)
- [ ] Model .bbmodel cho pokemon (phải tự làm trong BlockBench - KHÔNG rip asset)
- [ ] Bedrock custom item texture cho pokeball (Geyser custom item mappings)
- [ ] Hitbox scale theo species
- [ ] Animation state (idle/walk/faint) binding với ModelEngine

## Capture

- [x] 4 loại ball (Poke/Great/Ultra/Master) - snowball + CustomModelData + PDC
- [x] Công thức bắt scale theo catchRate, ball bonus, HP còn lại
- [x] Master Ball luôn thành công
- [x] Damage trong battle được sync vào entity PDC nên đánh yếu trước sẽ dễ bắt hơn
- [x] Party đầy thì tự chuyển vào PC box
- [ ] Animation ném ball / ball rung 3 lần
- [ ] Status condition bonus (ngủ/tê liệt tăng tỉ lệ bắt)
- [ ] Craft recipe cho pokeball

## Battle

- [x] Battle hoang dã 1v1 (đấm pokemon hoang để mở)
- [x] Speed check quyết định lượt đánh trước
- [x] Damage formula gen-style: STAB, type chart, crit 1/24, roll 0.85-1.0
- [x] Accuracy check / miss
- [x] Wild AI chọn move ngẫu nhiên
- [x] Exp khi thắng (yield * level / 7 * multiplier), level up giữa trận
- [x] Evolution theo level sau khi thắng
- [x] Run/flee
- [~] Faint: chỉ báo đổi pokemon bằng cách đấm lại, chưa có GUI switch giữa trận
- [ ] PvP trainer battle
- [ ] Trainer NPC battle (Citizens)
- [ ] Status conditions (burn/paralysis/sleep/poison/freeze)
- [ ] Stat stages (Growl hiện là STATUS nhưng chưa có effect thật)
- [ ] PP tracking (đã có field pp trong MoveData nhưng chưa trừ)
- [ ] Switch pokemon giữa trận, dùng item giữa trận
- [ ] Battle theo turn timeout (chống AFK)

## Party / Storage

- [x] Party 6 slot + PC box per player
- [x] Cache in-memory, load async khi join, save async khi quit + khi tắt server
- [x] SQLite mặc định (shaded JDBC), MySQL qua config
- [x] Pokemon lưu dạng JSON row, index theo owner
- [x] Party GUI xem thông tin (HP, type, nature, moves)
- [ ] PC box GUI (data đã lưu slot -1, chưa có giao diện)
- [ ] Đổi thứ tự party, release, đặt nickname
- [ ] Trade giữa 2 player

## Commands / Admin

- [x] /poke party (mở GUI)
- [x] /poke give <ball> - admin
- [x] /poke spawn <species> [level] - admin
- [x] /poke heal - admin
- [x] /poke reload - admin
- [x] Tab completion
- [ ] /poke pc, /poke stats <slot>, /poke nickname
- [ ] Pokecenter block (heal station cho player thường)
- [ ] Pokemart shop GUI
- [ ] Web admin panel + REST API

## Infrastructure

- [x] Gradle build (Java 21, Paper 1.21.4 API, repos: PaperMC/Lumine/OpenCollab)
- [x] plugin.yml softdepend: ModelEngine, floodgate, Geyser-Spigot
- [x] Docs riêng: ARCHITECTURE / SETUP / ROADMAP / STATUS
- [ ] Compile verify (sandbox chặn Maven Central - build lần đầu trên máy)
- [ ] Unit tests cho DamageCalculator + ExperienceCurve
- [ ] CI workflow (GitHub Actions build jar)

---

## Known issues / notes

1. `despawn-seconds` và `despawn-distance` trong config chưa được SpawnManager
   dùng làm despawn chủ động - wild pokemon dựa vào chunk unload.
2. Growl và các move STATUS chưa có hiệu ứng (cần stat stages).
3. Evolution chỉ hoạt động khi file species đích tồn tại (ivysaur, charmeleon,
   wartortle, pidgeotto chưa có JSON - bắt buộc thêm trước khi test evolution).
4. Base entity HUSK sẽ bốc cháy ban ngày nếu không có model che -
   cân nhắc đổi sang entity khác hoặc set fire ticks = 0 nếu thấy cháy.
5. Chưa verify compile (Maven bị chặn trong sandbox) - `./gradlew build`
   lần đầu trên máy, gửi log nếu lỗi.
