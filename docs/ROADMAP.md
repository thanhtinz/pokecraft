# Roadmap

## Phase 1 (done)
- Species/moves data system, 18-type chart, stats/IV/nature/exp
- Biome-weighted wild spawning with ModelEngine models
- Pokeball capture with HP-scaled formula
- Turn-based wild battles with GUI (Java) and native forms (Bedrock)
- Level-up, learnset, evolution, SQLite/MySQL persistence
- Full Geyser/Floodgate compatibility path

## Phase 2 (mostly done)
- [x] PC box GUI with pagination, party reordering, deposit/withdraw, release
- [x] Status conditions (burn, paralysis, sleep, poison, freeze), stat stages
- [x] Move PP tracking + Struggle, move priority
- [x] Mid-battle switching + forced switch on faint
- [x] Full evolution lines for the starter species + capture status bonus
- [x] Active despawn timer, passive wild AI, CI + unit tests
- [x] PvP trainer battles (challenge system)
- [x] Pokemart shop GUI + potions + evolution stones
- [x] Economy (PokeDollars), leaderboards, marriage, daycare + breeding, riding
- [ ] Pokecenter blocks (heal station)
- [ ] Held items, natures affecting spawn odds

## Phase 3
- [x] Trainer NPCs (built-in, no Citizens) + healer/vendor NPCs
- [ ] Gym leaders + badges (dùng trainer NPC làm nền)
- Egg items + hatching, day/night + weather spawn conditions
- Legendary spawn events with server broadcasts
- Trading GUI between players (Bedrock-safe)

## Phase 4
- Next.js admin panel + REST API (species editor, player inspector)
- [x] Full 151 gen-1 dataset generation script (tools/generate_gen1.py)
- Bedrock resource pack polish (custom pokeball item textures via
  Geyser custom item mappings)
