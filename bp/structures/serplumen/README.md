# Bundled structures

Drop `.mcstructure` files here (or in a subfolder). `build.sh` scans this
folder and auto-registers each file so it appears in the admin
"Structures / Công trình -> Đặt công trình có sẵn" menu.

Identifier rules (Bedrock):
- `structures/serplumen/house.mcstructure`  ->  id `serplumen:house`
- `structures/foo.mcstructure`              ->  id `mystructure:foo`

The display name in the menu is the file name with underscores turned into
spaces (e.g. `poke_center.mcstructure` -> "poke center").
