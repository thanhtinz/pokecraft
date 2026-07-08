#!/usr/bin/env python3
"""
cobblemon2mcpack.py
Convert Cobblemon (Fabric/NeoForge) jar assets -> Bedrock resource pack (.mcpack)
for use with Geyser/Hydraulic so Bedrock clients can render Pokemon models.

Cobblemon ships Bedrock-format geometry (.geo.json) and animations
(.animation.json) inside the jar, so this tool mostly restructures assets
and generates the Bedrock-side glue files:
  - manifest.json
  - entity/<species>.entity.json  (client entity definitions)
  - render_controllers/cobblemon.render_controllers.json
  - models/entity/**  (copied geometry)
  - animations/**     (copied animations)
  - textures/**       (copied textures)

Usage:
  python3 cobblemon2mcpack.py path/to/cobblemon-fabric-1.6.x.jar \
      -o Cobblemon.mcpack [--namespace cobblemon] [--verbose]

Then drop the .mcpack into Geyser's `packs` folder (Geyser-Fabric:
config/Geyser-Fabric/packs). Hydraulic/GeyserUtils must map the Java
entity to the matching Bedrock identifier `cobblemon:<species>`.
"""

import argparse
import io
import json
import re
import sys
import uuid
import zipfile
from collections import defaultdict
from pathlib import PurePosixPath

MODELS_PREFIX = "assets/cobblemon/bedrock/pokemon/models/"
ANIMS_PREFIX = "assets/cobblemon/bedrock/pokemon/animations/"
TEXTURES_PREFIX = "assets/cobblemon/textures/pokemon/"

SPECIES_DIR_RE = re.compile(r"^(\d{4})_([a-z0-9_]+)$")


def log(verbose, *args):
    if verbose:
        print(*args)


def load_json_bytes(data: bytes):
    # Some Cobblemon jsons contain BOM or trailing commas rarely; be lenient.
    text = data.decode("utf-8-sig", errors="replace")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        # strip // comments and trailing commas (best effort)
        text = re.sub(r"//[^\n]*", "", text)
        text = re.sub(r",\s*([}\]])", r"\1", text)
        return json.loads(text)


def geometry_identifier(geo_json) -> str | None:
    try:
        geos = geo_json.get("minecraft:geometry") or []
        if geos:
            return geos[0]["description"]["identifier"]
    except (KeyError, TypeError, IndexError):
        pass
    # legacy format: keys like "geometry.bulbasaur"
    for k in geo_json.keys():
        if k.startswith("geometry."):
            return k
    return None


def pick_animation(anim_keys, *needles):
    """Pick first animation key whose suffix matches one of needles."""
    for needle in needles:
        for k in anim_keys:
            if k.endswith("." + needle) or k == needle:
                return k
    return None


def build_pack(jar_path, out_path, namespace, verbose):
    jar = zipfile.ZipFile(jar_path, "r")
    names = jar.namelist()

    # species_key ("0001_bulbasaur") -> data
    species = defaultdict(lambda: {
        "geo": [],        # (jar_path, species_subpath)
        "anim": [],
        "tex": [],
    })

    for n in names:
        if n.endswith("/"):
            continue
        if n.startswith(MODELS_PREFIX) and n.endswith(".geo.json"):
            rel = n[len(MODELS_PREFIX):]
            key = rel.split("/", 1)[0]
            species[key]["geo"].append(n)
        elif n.startswith(ANIMS_PREFIX) and n.endswith(".animation.json"):
            rel = n[len(ANIMS_PREFIX):]
            key = rel.split("/", 1)[0]
            species[key]["anim"].append(n)
        elif n.startswith(TEXTURES_PREFIX) and n.endswith(".png"):
            rel = n[len(TEXTURES_PREFIX):]
            key = rel.split("/", 1)[0]
            species[key]["tex"].append(n)

    if not species:
        print("ERROR: no Cobblemon pokemon assets found in jar. "
              "Is this the right file?", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(species)} species directories in jar")

    out = zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED)

    # ---- manifest ----
    manifest = {
        "format_version": 2,
        "header": {
            "name": "Cobblemon Bedrock Assets",
            "description": "Auto-converted from Cobblemon jar for Geyser",
            "uuid": str(uuid.uuid4()),
            "version": [1, 0, 0],
            "min_engine_version": [1, 20, 60],
        },
        "modules": [{
            "type": "resources",
            "uuid": str(uuid.uuid4()),
            "version": [1, 0, 0],
        }],
    }
    out.writestr("manifest.json", json.dumps(manifest, indent=2))

    # ---- shared render controller ----
    render_controllers = {
        "format_version": "1.10.0",
        "render_controllers": {
            "controller.render.cobblemon_default": {
                "geometry": "Geometry.default",
                "materials": [{"*": "Material.default"}],
                "textures": ["Texture.default"],
            }
        },
    }
    out.writestr("render_controllers/cobblemon.render_controllers.json",
                 json.dumps(render_controllers, indent=2))

    ok, skipped = 0, []

    for key in sorted(species.keys()):
        data = species[key]
        m = SPECIES_DIR_RE.match(key)
        name = m.group(2) if m else key.lower()

        if not data["geo"]:
            skipped.append((key, "no geometry"))
            continue

        # prefer main geo file named <name>.geo.json
        geo_path = None
        for g in data["geo"]:
            if PurePosixPath(g).name == f"{name}.geo.json":
                geo_path = g
                break
        geo_path = geo_path or data["geo"][0]

        try:
            geo_json = load_json_bytes(jar.read(geo_path))
        except Exception as e:  # noqa: BLE001
            skipped.append((key, f"geo parse error: {e}"))
            continue

        geo_id = geometry_identifier(geo_json)
        if not geo_id:
            skipped.append((key, "no geometry identifier"))
            continue

        # texture: prefer <name>.png in species dir root
        tex_path = None
        for t in data["tex"]:
            if PurePosixPath(t).name == f"{name}.png":
                tex_path = t
                break
        if not tex_path and data["tex"]:
            # avoid shiny as default
            non_shiny = [t for t in data["tex"] if "shiny" not in t]
            tex_path = (non_shiny or data["tex"])[0]
        if not tex_path:
            skipped.append((key, "no texture"))
            continue

        # animations (optional)
        anim_refs = {}
        animate = []
        for a in data["anim"]:
            try:
                aj = load_json_bytes(jar.read(a))
            except Exception:  # noqa: BLE001
                continue
            keys = list((aj.get("animations") or {}).keys())
            idle = pick_animation(keys, "ground_idle", "water_idle",
                                  "air_idle", "idle")
            walk = pick_animation(keys, "ground_walk", "water_swim",
                                  "air_fly", "walk")
            if idle and "idle" not in anim_refs:
                anim_refs["idle"] = idle
            if walk and "walk" not in anim_refs:
                anim_refs["walk"] = walk

        if "idle" in anim_refs:
            animate.append("idle")
        if "walk" in anim_refs:
            animate.append({"walk": "query.modified_move_speed > 0.1"})

        # ---- copy assets into pack ----
        for g in data["geo"]:
            rel = g[len(MODELS_PREFIX):]
            out.writestr(f"models/entity/pokemon/{rel}", jar.read(g))
        for a in data["anim"]:
            rel = a[len(ANIMS_PREFIX):]
            out.writestr(f"animations/pokemon/{rel}", jar.read(a))
        for t in data["tex"]:
            rel = t[len(TEXTURES_PREFIX):]
            out.writestr(f"textures/pokemon/{rel}", jar.read(t))

        tex_rel = "textures/pokemon/" + tex_path[len(TEXTURES_PREFIX):]
        tex_ref = tex_rel[:-4]  # strip .png

        entity = {
            "format_version": "1.10.0",
            "minecraft:client_entity": {
                "description": {
                    "identifier": f"{namespace}:{name}",
                    "materials": {"default": "entity_alphatest"},
                    "textures": {"default": tex_ref},
                    "geometry": {"default": geo_id},
                    "render_controllers":
                        ["controller.render.cobblemon_default"],
                }
            },
        }
        desc = entity["minecraft:client_entity"]["description"]
        if anim_refs:
            desc["animations"] = anim_refs
            desc["scripts"] = {"animate": animate}

        out.writestr(f"entity/{key}.entity.json",
                     json.dumps(entity, indent=2))
        ok += 1
        log(verbose, f"  + {namespace}:{name}  geo={geo_id}")

    out.close()
    jar.close()

    print(f"Done: {ok} entities written -> {out_path}")
    if skipped:
        print(f"Skipped {len(skipped)}:")
        for k, why in skipped[:20]:
            print(f"  - {k}: {why}")
        if len(skipped) > 20:
            print(f"  ... and {len(skipped) - 20} more")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("jar", help="Path to cobblemon-fabric/neoforge .jar")
    p.add_argument("-o", "--output", default="Cobblemon.mcpack")
    p.add_argument("--namespace", default="cobblemon")
    p.add_argument("-v", "--verbose", action="store_true")
    args = p.parse_args()
    build_pack(args.jar, args.output, args.namespace, args.verbose)


if __name__ == "__main__":
    main()
