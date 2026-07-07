# Credits & Attribution

## Cobblemon species data

Most of PokeCraft's species dataset (Generations 2-9, national dex 152-1025;
Generation 1 is generated separately) was derived from **Cobblemon** (https://gitlab.com/cable-mc/cobblemon), which is
licensed under the **Mozilla Public License 2.0**.

Specifically, `tools/import_cobblemon.py` reads Cobblemon's species JSON files
and converts the following fields into PokeCraft's format:

- base stats, primary/secondary types
- catch rate, base experience yield
- evolution chains (levels and stone requirements)
- national dex numbers and legendary flags

Biome spawn tables are **generated locally** by PokeCraft
(`tools/generate_gen1.py` helpers). Each species' authentic **level-up
learnset** is read from Cobblemon's species JSON (`moves` field) by
`tools/enrich_learnsets.py` and matched onto PokeCraft's move pool.

Under MPL 2.0 you may use and modify these data files; keep this attribution
and the MPL notice if you redistribute them.

## PokeAPI move data

PokeCraft's move pool (`src/main/resources/moves/moves.json`, ~919 moves) is
built by `tools/build_moves.py` from the **PokeAPI** CSV data dump
(https://github.com/PokeAPI/pokeapi, `data/v2/csv/`), released under the
**BSD 3-Clause License**. Move names, types, power, accuracy, PP and secondary
effects come from those CSVs.

## Pokemon intellectual property

Pokemon names, species and their stats are trademarks and copyright of
Nintendo / Creatures Inc. / GAME FREAK inc. PokeCraft is a non-commercial
fan project. Do not use it for commercial purposes.

## Assets

PokeCraft ships **no** Pokemon art, models, textures or sounds. 3D models (if
any) must be supplied by the server owner via ModelEngine; do not rip assets
from official games or from other projects such as Cobblemon or Pixelmon.
