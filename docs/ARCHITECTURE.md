# Architecture

## Why plugin, not mod

Pixelmon is a Forge/NeoForge mod: every player must install it, which makes it
impossible for Bedrock (mobile/console) players. Geyser only bridges Bedrock
clients to *plugin* servers (Paper/Spigot/Velocity). Therefore all Pokemon
features must be implemented 100% server-side.

## Rendering stack (how each platform sees pokemon)

```
BlockBench model (.bbmodel)
        |
   BetterModel            <- converts models into item-display-based entities
        |                    (Java clients only)
   Geyser + Floodgate     <- Bedrock protocol bridge + auth
        |
  vanilla mob mapping     <- Bedrock/mobile players see a fitting vanilla mob
                             (mobs.by-species / mobs.by-type in config.yml)
```

- Java players: BetterModel renders real animated 3D pokemon (its resource pack
  is auto-generated). BetterModel is free and open-source and supports current
  MC versions.
- Bedrock/mobile players: custom 3D models can't render through Geyser, so each
  pokemon shows as a fitting vanilla mob (configured under `mobs:`). This works
  on every platform because vanilla mobs are Bedrock-native.
- `models.hide-base-java-only: true` hides the base mob only from Java players,
  so Java sees the 3D model while Bedrock keeps the mapped vanilla mob.
- If BetterModel is absent the plugin still works: pokemon appear as the mapped
  vanilla mob (or `capture.base-entity`, default HUSK) with name tags.

## Module map

| Package   | Responsibility |
|-----------|----------------|
| species   | Data-driven species/type/stat definitions loaded from JSON |
| pokemon   | Runtime instances: IVs, natures, exp curve, stat formula (gen 3+) |
| party     | 6-slot party + PC box per player, cached in memory |
| storage   | SQLite (default) or MySQL, pokemon serialized as JSON rows |
| spawn     | Biome-weighted wild spawning around players, per-player cap |
| entity    | Base entity spawning, PDC tagging, BetterModel reflection hook |
| capture   | Pokeball items (snowball + CustomModelData), capture formula |
| battle    | Turn-based 1v1 wild battles: priority/speed order, stat stages, status conditions, PP/Struggle, switching, exp/level/evolution |
| ui        | Chest GUIs (Geyser auto-translates these for Bedrock) |
| bedrock   | Floodgate detection + native Cumulus SimpleForm battle menu |
| command   | /poke party, pc, shop, balance, pay, top, duel, marry, daycare, ride, ... |
| economy   | PokeDollar balances + caught/wins stats (players table) |
| shop      | Pokemart GUI: balls, potions, evolution stones |
| item      | Usable items (potions, stones) with party-picker GUI |
| social    | Marriage: propose/accept/divorce, online-spouse EXP bonus |
| daycare   | Passive EXP + breeding (base form baby, IV inheritance) |
| ride      | Mount your pokemon, look-steering, flying for FLYING types |

## Key design decisions

1. **Reflection soft-depends** for BetterModel and Floodgate: the jar talks to
   them purely via reflection, so it never hard-crashes if either is absent.
2. **Data-driven content**: species and moves are JSON in the plugin data
   folder. Adding a pokemon = drop a JSON file + (optionally) a BetterModel
   model, then /poke reload. No code changes.
3. **PDC-tagged entities**: wild pokemon carry their full PokemonInstance as
   JSON in PersistentDataContainer, so battle damage persists into the capture
   formula (weakened pokemon are easier to catch, like the games).
4. **Dual UI path**: chest GUI for Java, native Bedrock form for Floodgate
   players (config `bedrock.use-forms`). Chest GUI is the universal fallback
   because Geyser translates it anyway.

## Battle flow

```
Player punches wild pokemon
  -> BattleManager.startWildBattle
  -> BattleGui.open (or Bedrock form)
  -> player picks move/switch -> order: priority then effective speed
     (stages, paralysis) -> pre-attack check (sleep/freeze/paralysis)
     -> DamageCalculator (STAB, type chart, stat stages, burn, crit,
        0.85-1.0 roll) -> secondary effects (stages, status)
     -> end of turn: burn/poison residual -> reopen menu
  -> player faint: forced switch GUI (battle continues) or defeat
  -> wild faint: exp (yield * level / 7), level-up, learnset refresh,
     evolution check
```

## Capture formula (simplified gen-style)

```
chance = catchRate * ballBonus * statusBonus * (1.6 - currentHp/maxHp) / 255
```
statusBonus: x2 for sleep/freeze, x1.5 for other statuses. Master Ball always
succeeds. Damaging a pokemon in battle first raises the catch chance because
the entity's PDC data is updated after every hit.
