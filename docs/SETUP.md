# Setup

## Requirements

| Component | Version | Required |
|-----------|---------|----------|
| Paper | 1.21.4+ | yes |
| Java | 21 | yes |
| Geyser-Spigot | latest | for Bedrock players |
| Floodgate | latest | for Bedrock auth + native forms |
| BetterModel | latest | for 3D models on the Java client (free, open-source) |

## Build

```
./gradlew build
# output: build/libs/PokeCraft-0.1.0.jar
```

Note: SQLite JDBC is shaded into the jar. First build downloads dependencies
from Maven Central / PaperMC / Lumine / OpenCollab repos.

## Install order

1. Drop Geyser-Spigot + Floodgate into plugins/, start once, configure Geyser
   (UDP port 19132 open for Bedrock).
2. (Optional) Drop BetterModel into plugins/ if you want 3D models on the Java
   client. Start once so it generates its resource pack.
3. Drop PokeCraft-0.1.0.jar, restart.

> **Bedrock/mobile note:** custom 3D models (BetterModel or any model engine)
> render only on the Java client. Bedrock/mobile players see each pokemon as a
> fitting **vanilla mob** instead (configured under `mobs:` in config.yml), so
> the server still looks good on mobile even without any models installed.

## Adding your own 3D models

PokeCraft does **not** ship any pokemon art/models (to avoid ripping assets).
You supply the models; PokeCraft binds them to species and can preview them
in-game. No code editing and no restart.

1. Build (or obtain, originally) a `.bbmodel` in BlockBench. Do **not** rip
   assets from Pixelmon, Cobblemon or the official games.
2. Import it into BetterModel: put it in `plugins/BetterModel/models/` and
   run `/bm reload`. It now exists as a **blueprint** with an id.
   (Or just drop the Bedrock `.geo.json` + `.png` into
   `plugins/PokeCraft/models-import/` and PokeCraft auto-converts + imports it
   on startup.)
3. **Auto-bind by name:** if the blueprint id equals the species id
   (e.g. a blueprint called `pikachu`), PokeCraft uses it automatically — no
   further setup.
4. **Different name?** bind it manually (persists in config, no restart):
   ```
   /poke model set pikachu my_custom_pikachu   # species -> blueprint
   /poke model clear pikachu                    # revert to auto
   ```
5. **Check & preview in-game** (all panel-based):
   - Menu → OP Setup → *Pokemon 3D models* opens the model panel: shows
     `X / 1016` species that have a blueprint, browse all species (green = has
     model), and click any pokemon to **spawn a live preview** of its model.
   - Or `/poke model coverage`, `/poke model preview <blueprint>`.
6. Java players see the 3D model; Bedrock/mobile players see the mapped vanilla
   mob (custom models can't render through Geyser). Use
   `models.hide-base-java-only: true` to hide the base mob only from Java
   players so both platforms look right.

Species with no matching blueprint just fall back to a vanilla base entity
(`capture.base-entity`), so the server works fine with zero models installed
and gets prettier as you add them. Toggle the whole system with
`models.enabled` in config.yml.

## Adding species

Create plugins/PokeCraft/species/<id>.json following the bundled examples
(13 species: the bulbasaur/charmander/squirtle/pidgey lines + pikachu),
then /poke reload.

## Quick test

```
/poke give            # 16 Poke Balls (admin)
/poke spawn pikachu 5 # spawn a wild pikachu
punch it              # opens battle menu
throw a pokeball      # capture attempt
/poke party           # view/reorder party, deposit to PC
/poke pc              # PC box (click to withdraw)
/poke nickname 1 Bob  # rename slot 1 ("off" clears)
/poke release pc 3    # release (run twice to confirm)
/poke heal            # full HP/PP/status restore (admin)
```
