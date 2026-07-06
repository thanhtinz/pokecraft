# Setup

## Requirements

| Component | Version | Required |
|-----------|---------|----------|
| Paper | 1.21.4+ | yes |
| Java | 21 | yes |
| Geyser-Spigot | latest | for Bedrock players |
| Floodgate | latest | for Bedrock auth + native forms |
| ModelEngine | R4.0.8+ | for 3D models |
| GeyserModelEngine | latest | shows ME models to Bedrock players |

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
2. Drop ModelEngine, start once so it generates its resource pack.
3. Install GeyserModelEngine as a **Geyser extension**
   (plugins/Geyser-Spigot/extensions/).
4. Drop PokeCraft-0.1.0.jar, restart.

## Adding pokemon models

1. Create/obtain a .bbmodel in BlockBench (Modded Entity or ME format).
   Use original models - do not rip assets from Pixelmon or official games.
2. Put it in plugins/ModelEngine/blueprints/ and run /meg reload.
3. The `modelId` field in the species JSON must match the blueprint name.
4. GeyserModelEngine picks it up automatically for Bedrock.

## Adding species

Create plugins/PokeCraft/species/<id>.json following the bundled examples
(bulbasaur, charmander, squirtle, pidgey, pikachu), then /poke reload.

## Quick test

```
/poke give            # 16 Poke Balls (admin)
/poke spawn pikachu 5 # spawn a wild pikachu
punch it              # opens battle menu
throw a pokeball      # capture attempt
/poke party           # view party
```
