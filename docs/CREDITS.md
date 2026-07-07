# Credits & Attribution

## Cobblemon species data

Part of PokeCraft's species dataset (Generation 2, national dex 152-251) was
derived from **Cobblemon** (https://gitlab.com/cable-mc/cobblemon), which is
licensed under the **Mozilla Public License 2.0**.

Specifically, `tools/import_cobblemon.py` reads Cobblemon's species JSON files
and converts the following fields into PokeCraft's format:

- base stats, primary/secondary types
- catch rate, base experience yield
- evolution chains (levels and stone requirements)
- national dex numbers and legendary flags

Learnsets and biome spawn tables are **generated locally** by PokeCraft
(`tools/generate_gen1.py` helpers) and are not taken from Cobblemon.

Under MPL 2.0 you may use and modify these data files; keep this attribution
and the MPL notice if you redistribute them.

## Pokemon intellectual property

Pokemon names, species and their stats are trademarks and copyright of
Nintendo / Creatures Inc. / GAME FREAK inc. PokeCraft is a non-commercial
fan project. Do not use it for commercial purposes.

## Assets

PokeCraft ships **no** Pokemon art, models, textures or sounds. 3D models (if
any) must be supplied by the server owner via ModelEngine; do not rip assets
from official games or from other projects such as Cobblemon or Pixelmon.
