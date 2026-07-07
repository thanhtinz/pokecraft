# Custom Pokedex item texture

The Pokedex is a `RED_DYE` item tagged with `custom_model_data: 1`. With a
resource pack it shows a real Pokedex texture instead of the red dye; without
one it just looks like red dye (still fully works).

> **Assets:** the bundled `pokedex.png` is a **simple original placeholder**,
> not official Pokemon art. Do not commit ripped Pokemon/Pixelmon/Cobblemon
> assets here. Replace `pokedex.png` with your own 16x16 (or larger) texture.

## Java client (`java/`)

1. Copy the `java/` folder into a resource pack (or zip its contents so
   `pack.mcmeta` is at the zip root).
2. Set `pack_format` in `pack.mcmeta` to match your MC version.
3. Serve it as the server resource pack (server.properties `resource-pack`) or
   drop it in the client's `resourcepacks/` folder.
4. The Pokedex now renders `assets/minecraft/textures/item/pokedex.png`.
   Swap that PNG for your art.

Format used is the 1.21.4+ item-model system
(`assets/minecraft/items/red_dye.json` with `range_dispatch` on
`custom_model_data`). On older versions use an `overrides` block in
`models/item/red_dye.json` instead.

## Bedrock / mobile (Geyser) (`geyser/` + `bedrock/`)

Custom item textures only reach Bedrock players through Geyser custom items.
Everything is pre-built here - you only swap the texture:

1. Put `geyser/pokecraft_mappings.json` in
   `plugins/Geyser-Spigot/custom_mappings/`.
2. Zip the **contents** of the `bedrock/` folder (so `manifest.json` is at the
   zip root) into e.g. `pokecraft.mcpack` and drop it in
   `plugins/Geyser-Spigot/packs/`. It already contains:
   - `manifest.json` (resource pack module)
   - `textures/item_texture.json` (maps the `pokedex` icon to the PNG)
   - `textures/items/pokedex.png` (the texture - **replace with your art**)
   - `pack_icon.png`
3. In Geyser's `config.yml` set `add-non-bedrock-items: true`, then restart.

The mapping's `icon: "pokedex"` matches the `texture_data.pokedex` key in
`item_texture.json`, which points at `textures/items/pokedex.png`. Keep those
three names in sync if you rename anything.

See the Geyser docs: https://geysermc.org/wiki/geyser/custom-items/

Without the Geyser pack, Bedrock players see the vanilla red dye - the item
still opens the Pokedex, it just isn't reskinned.
