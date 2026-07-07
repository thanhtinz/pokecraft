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
- [x] Species dataset: FULL NATIONAL DEX - 1016 pokemon (Gen 1-9). Gen 1 tự
      sinh (tools/generate_gen1.py); Gen 2-9 import từ Cobblemon MPL-2.0
      (tools/import_cobblemon.py) - lấy stats/types/catchRate/expYield/evolution
      chính xác; spawn sinh cục bộ theo hệ/biome. Xem docs/CREDITS.md
- [x] Learnset CHUẨN từng loài: level-up moveset thật của mỗi loài lấy từ
      Cobblemon (tools/enrich_learnsets.py), khớp vào move pool ~919 move —
      không còn dùng chung type-pool. 1016/1016 loài có learnset hợp lệ.
- [x] ~506 loài spawn hoang theo biome/hệ; legendary/final-evo không spawn
- [x] Tiến hóa bằng đá: Thunder/Fire/Water/Leaf/Moon Stone (mua ở shop);
      hỗ trợ nhiều nhánh (Eevee -> Vaporeon/Jolteon/Flareon)
- [x] Trade evolution THẬT: khi trao đổi giữa 2 người chơi, các loài trade-evo
      cổ điển tự tiến hóa (Kadabra->Alakazam, Machoke->Machamp, Graveler->Golem,
      Haunter->Gengar, Boldore, Gurdurr, Phantump, Pumpkaboo); Everstone chặn
- [x] Move dataset: ~919 moves (build từ PokeAPI CSV qua tools/build_moves.py),
      phủ đủ 18 hệ, kèm hiệu ứng phụ (status/stat-change)
- [x] EVs (effort values): +EV khi hạ pokemon theo evYield từng loài, cap
      252/stat & 510 tổng, cộng vào công thức chỉ số; hiện trong Summary
- [x] Abilities: mỗi pokemon có 1 đặc tính (từ data Cobblemon, 1/20 ra hidden),
      hiện ở Summary; ~20 đặc tính tác động sát thương đã hoạt động trong battle:
      Levitate/Flash Fire/Water-Volt Absorb/Sap Sipper (miễn nhiễm hệ),
      Thick Fat/Heatproof/Multiscale/Filter-Solid Rock (giảm sát thương),
      Huge Power/Pure Power/Hustle/Guts (tăng công), Sturdy (trụ 1 HP khi full),
      Rough Skin/Iron Barbs (phản 1/8), Speed Boost (+1 speed mỗi lượt),
      Overgrow/Blaze/Torrent/Swarm
      (pinch +50%). ON-ENTRY: Intimidate (giảm Atk đối thủ khi ra trận/switch).
      ON-HIT tiếp xúc: Static/Flame Body/Poison Point (30% gây par/burn/psn khi
      bị đánh vật lý, tôn trọng miễn nhiễm hệ). Áp dụng CẢ wild/trainer VÀ PvP.
- [x] Held items: Leftovers (hồi 1/16/lượt), Muscle Band (+10% physical),
      Wise Glasses (+10% special), Quick Claw (20% đánh trước), Lucky Egg
      (+50% EXP), Everstone (chặn tiến hóa), Focus Band (10% trụ 1 HP) -
      mua ở shop, right-click để trang bị, tháo trong Summary GUI,
      hoạt động ở cả wild/trainer/PvP battle
- [x] Giới tính (♂/♀/vô tính theo maleRatio từng loài), hiện ở Summary

## Spawning

- [x] Biome-weighted wild spawn quanh player (interval, radius, cap trong config)
- [x] Level range per species per spawn entry
- [x] Ground-check + height-check khi chọn vị trí spawn
- [x] Active despawn sweep: `despawn-seconds` (tuổi tối đa) + `despawn-distance`
      (xa mọi player) - bỏ qua pokemon đang trong battle
- [x] Ảnh hưởng ngày/đêm & thời tiết tới spawn (theo hệ): đêm ưu tiên
      Ghost/Dark/Poison/Ice, ngày ưu tiên Normal/Bug/Flying/Grass/Fairy, mưa
      tăng Water/Electric (config spawning.time-weather-influence)
- [ ] Spawn dưới nước / trên không (hiện chỉ spawn trên mặt đất)
- [x] Legendary spawn event: định kỳ (config) có xác suất spawn 1 legendary
      (30 loài Gen 1-9) gần 1 player + broadcast toàn server; OP panel có nút
      spawn ngay; legendary vẫn không spawn thường

## Rendering / Bedrock

- [x] ModelEngine hook qua reflection (softdepend, không crash khi thiếu)
- [x] Fallback vanilla base entity (HUSK, đổi được trong config) + nametag
- [x] Wild entity không bốc cháy ban ngày, không tấn công/chase player,
      không nhận damage môi trường, luôn là zombie trưởng thành
- [x] Floodgate detection qua reflection
- [x] Native Bedrock SimpleForm (Cumulus) cho battle menu + switch menu (PP, status)
- [x] Chest GUI fallback cho mọi client (Geyser tự dịch)
- [x] Hệ thống quản lý model 3D: ModelManager map species -> blueprint
      ModelEngine (auto theo tên hoặc override), panel OP xem coverage
      X/1016 + duyệt loài + click PREVIEW model ngay trong game; lệnh
      /poke model set|clear|preview|coverage; config models.enabled/scale.
      (Model .bbmodel vẫn do chủ server tự làm trong BlockBench - KHÔNG rip asset)
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
- [x] Gym Leader + Badge: 8 gym Kanto (Brock->Giovanni), đội theo hệ & level
      tăng dần; hạ leader nhận badge; panel Badges (menu Activities) xem 8 badge;
      OP panel -> "Place Gym Leader" chọn gym để đặt tại chỗ đứng
- [x] Dùng item giữa trận: nút Bag trong battle menu -> chọn potion (Potion/
      Super/Hyper/Oran Berry) hồi HP cho pokemon đang đánh; tốn lượt (pokemon
      hoang được đánh 1 phát), không cho phí khi đã đầy HP
- [ ] Battle theo turn timeout (chống AFK)
- [x] Stat stages cho accuracy/evasion: Sand-Attack/Smokescreen/Flash (giảm
      accuracy đối thủ), Double Team/Minimize (tăng evasion) -> ảnh hưởng tỉ lệ
      trúng đòn (bảng 3-based). 11 move giảm accuracy + 5 move evasion

## Party / Storage

- [x] Party 6 slot + PC box per player
- [x] Cache in-memory, load async khi join, save async khi quit + khi tắt server
- [x] SQLite mặc định (shaded JDBC), MySQL qua config
- [x] Pokemon lưu dạng JSON row, index theo owner (PP/status persist)
- [x] Party GUI: xem chi tiết (HP, type, nature, exp, moves+PP, status),
      click-chọn rồi click slot khác để đổi chỗ, gửi vào PC, mở PC box
- [x] Summary GUI per pokemon (kiểu Cobblemon): info/nature/EXP, 4 move
      với PP-power-accuracy-effect, 6 stat + IV, yêu cầu tiến hóa,
      ô held item (click để tháo)
- [x] PC box GUI phân trang (45/trang), click để rút về party
- [x] /poke nickname, /poke release (xác nhận 2 lần trong 15s)
- [x] Trade giữa 2 player: /poke trade <player>, GUI 2 bên - mỗi người
      offer 1 pokemon, đổi khi CẢ HAI xác nhận; đổi chủ sở hữu + cập nhật
      Pokedex người nhận; đổi offer reset xác nhận; thoát/quit hủy trade

## Economy / Shop

- [x] PokeDollar per player (bảng players, số dư khởi điểm config)
- [x] Thưởng tiền khi thắng wild battle (level * config) và PvP
- [x] /poke balance, /poke pay <player> <amount>
- [x] Pokemart GUI (/poke shop): ball bundle, 3 loại potion, Thunder Stone
- [x] Potion dùng ngoài trận (right-click -> chọn pokemon; hồi được cả pokemon đã faint)
- [ ] Vault integration
- [x] Bán đồ: Pokemart -> nút "Sell items" liệt kê đồ bán được (ball/potion/
      stone/held item), click bán 1 cái lấy tiền
- [x] Bán pokemon: nút Sell trong Summary (xác nhận 2 lần), giá = base + level*
      hệ số (x3 nếu shiny); không bán con cuối cùng

## Leaderboard

- [x] /poke top [caught|money|wins|pvp] - top 10 từ DB

## Social

- [x] Kết hôn: /poke marry <player|accept|deny>, /poke divorce
- [x] Bonus EXP battle (config marriage.exp-bonus) khi vợ/chồng cùng online
- [ ] Quà cưới / teleport tới vợ chồng

## Daycare / Breeding

- [x] /poke daycare deposit|withdraw|status (tối đa 2 slot, config)
- [x] EXP thụ động theo phút khi gửi (config exp-per-minute)
- [x] Lai tạo: 2 pokemon cùng dòng tiến hóa -> ra 1 EGG (dạng gốc base form),
      con non thừa hưởng 3 IV từ bố mẹ, shiny rate x2
- [x] Egg item (turtle egg) + đi bộ để nở: dữ liệu con non nằm trong item nên
      egg có thể cất/thả/trade; đi đủ egg.steps block thì nở ra pokemon
- [x] Ditto: lai được với mọi loài (ra base form của con kia); lai thường
      cần đực+cái. Everstone: chặn tiến hóa (đã có)

## Riding

- [x] /poke ride <slot> - cưỡi pokemon của mình, đi theo hướng nhìn
- [x] Pokemon hệ FLYING bay được (config ride.allow-fly)
- [x] Sneak để xuống, tự dismount khi vào battle/quit
- [x] Walking pokemon: bật/tắt ở menu (nút Walking Pokemon) - con đầu đội đi
      theo người chơi (entity không AI, glide mỗi tick, dùng model ModelEngine
      nếu có); trạng thái lưu meta nên relog vẫn theo; chạy cả PC + mobile
- [ ] Điều khiển WASD thật (cần saddle-entity hack), tốc độ theo chỉ số Speed

## Pokedex

- [x] Theo dõi seen/caught per player (bảng pokedex): gặp trong battle = seen,
      bắt/lai tạo/tiến hóa/sở hữu = caught
- [x] GUI phân trang theo số dex: caught hiện đủ stats/evolution/spawn biome,
      seen hiện tên, chưa gặp hiện "???"
- [x] /poke dex + nút trên menu panel, đếm tiến độ x/1016 (phân trang 45/trang)
- [ ] Phần thưởng hoàn thành dex (milestone rewards)

## In-game Menu (PC + Mobile) - NO COMMANDS NEEDED

- [x] MỌI thao tác người chơi làm được qua panel, không cần gõ lệnh:
      - Summary GUI: nút Set nickname (nhập chữ qua anvil - Geyser dịch sang
        Bedrock), nút Release (xác nhận 2 lần)
      - Menu: click Balance -> chọn người -> chọn mức tiền để Pay (không gõ số);
        click ô Marriage khi đã cưới -> xác nhận Divorce
      - Duel/Trade/Marry/Pay đều qua GUI chọn người chơi
- [x] Lệnh /poke vẫn còn làm fallback nhưng không bắt buộc

- [x] Item menu (Nether Star) phát khi join, right-click/tap để mở panel
      (không cần gõ lệnh; chest GUI nên Bedrock/Geyser dùng được luôn)
- [x] Item riêng cho từng tính năng (không dồn hết vào 1 panel): Pokedex là
      cuốn sổ (BOOK), Team là túi (BUNDLE) - phát khi join, right-click/tap mở
      thẳng panel tương ứng; cấu hình items.give-pokedex / items.give-party
- [x] Hub: Party / PC Box / Shop / Daycare / Ride / Duel / Trade / Leaderboard /
      Pokedex / Marriage / Balance
- [x] GUI chọn người chơi cho Duel (lọc theo khoảng cách) và Marry
- [x] GUI chọn pokemon để cưỡi; GUI daycare gửi/rút trực quan
- [x] Leaderboard panel: top 10 của 4 hạng mục trong lore
- [x] Nút Accept ngay trên menu khi có lời thách đấu / cầu hôn đang chờ
- [x] /poke (không tham số) và /poke menu cũng mở panel
- [ ] Custom UI texture pack (hiện dùng icon vanilla item)

## Activities (Daily / Quests / Fishing)

- [x] Điểm danh hằng ngày: /poke menu -> Activities, thưởng tiền tăng theo
      streak (config), mốc 7 ngày tặng 5 Ultra Ball
- [x] Nhiệm vụ ngày: bắt 3 / thắng 5 wild / thắng 1 duel / câu 3 - tự reset
      mỗi ngày, click nhận thưởng khi hoàn thành
- [x] Câu cá: câu gần nước có tỉ lệ (config) móc trúng pokemon hệ Nước hoang
      -> mở battle luôn (bắt/đánh như thường)

## Farming (berries)

- [x] Berry Seed mua ở shop, trồng trên cỏ/đất (chuột phải), mọc theo thời
      gian (config), chuột phải khi chín để thu Oran Berry + tiền
- [x] Oran Berry là item hồi 30 HP; plot lưu DB (giữ qua restart)

## Dungeon

- [x] Dungeon: /poke menu -> Dungeon, tốn phí + cooldown; đánh N wave trainer
      (level tăng dần) rồi 1 BOSS (pokemon hiếm/legendary buff level cao);
      thắng cả run được thưởng lớn + 10 Ultra Ball; thua/chạy = kết thúc run
- [x] Cấu hình waves/level/cost/cooldown/reward/boss-species trong config

## Guild / Rank

- [x] Guild: tạo (tốn tiền, đặt tên qua anvil), tham gia (click trong list),
      rời/giải tán (xác nhận), bank chung (nạp tiền); GUI xếp theo bank
- [x] Rank ladder PvP theo mùa: thắng duel +điểm, thua -điểm; 6 bậc
      Bronze->Master; GUI xem bậc + top 10; admin reset mùa (thưởng top 3,
      thông báo toàn server) qua /poke rankreset hoặc OP panel

## Minimap & Minigames

- [x] PokeMap: item bản đồ (filled map) chạy CẢ mobile qua Geyser (overlay
      minimap mod client-only không hiện trên Bedrock), tự bám theo player,
      chấm đỏ = pokemon hoang, mũi tên = người chơi
- [x] Minigame hub (menu -> Minigames) - tất cả chest GUI chạy PC + mobile,
      thắng được thưởng tiền:
  - Solo: Casino (tung xu/slot máy), Trivia (đố vui pokemon), Tic-Tac-Toe &
    Connect Four đấu AI, Minesweeper (dò mìn 5x5, reveal-only cho mobile),
    Higher/Lower (đoán lá bài, giữ streak rồi cash out)
  - PvP 2 người: Tic-Tac-Toe & Connect Four - mời người chơi khác (panel picker
    -> confirm GUI), cả 2 mở CHUNG 1 bàn nên nước đi hiện realtime, winner
    nhận thưởng (minigame.board-pvp-reward)

## OP Setup Panel

- [x] Panel cấu hình trong game cho OP (menu -> OP Setup, chỉ hiện với admin;
      hoặc /poke admin): bật/tắt tính năng (spawn/fishing/bedrock forms/menu
      item), chỉnh số (spawn interval/cap, shiny rate, daily reward, dungeon
      cost/reward) bằng click trái +/phải -, đặt NPC (healer/vendor/trainer)
      tại chỗ, reset mùa rank, heal, reload - ghi thẳng vào config, không cần
      sửa file hay gõ lệnh

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
