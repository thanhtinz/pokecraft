#!/usr/bin/env python3
"""
Convert Bedrock entity geometry (.geo.json) into BlockBench .bbmodel files so
they can be dropped into ModelEngine (plugins/ModelEngine/blueprints/) and used
by PokeCraft + GeyserModelEngine on PC and mobile.

This mirrors what BlockBench's "Import Bedrock Model" does: bones become groups,
cubes become elements, UVs / inflate / rotation are carried over, and the
texture PNG (if given) is embedded. Only the static mesh is converted - bedrock
animations (.animation.json) are not included (the model shows in its rest
pose). That is still far better than a vanilla base entity, and animations can
be added later in BlockBench.

Usage
-----
Single model:
  python geo_to_bbmodel.py pikachu.geo.json --texture pikachu.png --out blueprints/
Batch a whole folder (matches each *.geo.json with a same-named *.png in
--textures if provided):
  python geo_to_bbmodel.py bedrock/pokemon/ --textures textures/pokemon/ --out out/

Name the output after the PokeCraft species id (e.g. pikachu.bbmodel) so it
auto-binds. Use --name to override, or rely on the .geo.json file name.

If a converted model looks rotated or mirrored wrong, flip ROT_SIGN below and
re-run - that is the one convention that can differ between exporters.
"""
import argparse
import base64
import json
import os
import struct
import sys
import uuid

# Bedrock -> BlockBench rotation sign per axis. If models come out rotated
# wrong, try (1, 1, 1) or (-1, -1, -1).
ROT_SIGN = (-1, -1, 1)


def uid():
    return str(uuid.uuid4())


def conv_rot(r):
    if not r:
        return [0, 0, 0]
    return [r[0] * ROT_SIGN[0], r[1] * ROT_SIGN[1], r[2] * ROT_SIGN[2]]


def png_size(path):
    """Return (width, height) from a PNG header, or (None, None)."""
    try:
        with open(path, "rb") as f:
            head = f.read(24)
        if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
            return None, None
        w, h = struct.unpack(">II", head[16:24])
        return w, h
    except Exception:
        return None, None


def face_uv(uv, uv_size):
    return [uv[0], uv[1], uv[0] + uv_size[0], uv[1] + uv_size[1]]


def cube_element(cube, bone_pivot):
    origin = [float(x) for x in cube.get("origin", [0, 0, 0])]
    size = cube.get("size", [0, 0, 0])
    to = [origin[0] + size[0], origin[1] + size[1], origin[2] + size[2]]
    pivot = cube.get("pivot", bone_pivot) or [0, 0, 0]
    el = {
        "name": "cube",
        "box_uv": isinstance(cube.get("uv"), list),
        "rescale": False,
        "locked": False,
        "from": origin,
        "to": to,
        "autouv": 0,
        "color": 0,
        "inflate": cube.get("inflate", 0),
        "origin": [float(pivot[0]), float(pivot[1]), float(pivot[2])],
        "uuid": uid(),
    }
    rot = cube.get("rotation")
    if rot:
        el["rotation"] = conv_rot(rot)
    uvd = cube.get("uv")
    if isinstance(uvd, list):  # box UV
        el["uv_offset"] = [uvd[0], uvd[1]]
        el["mirror_uv"] = bool(cube.get("mirror", False))
    else:  # per-face UV
        faces = {}
        for f in ("north", "east", "south", "west", "up", "down"):
            fd = (uvd or {}).get(f)
            if not fd or "uv" not in fd:
                faces[f] = {"uv": [0, 0, 0, 0], "texture": None}
            else:
                faces[f] = {"uv": face_uv(fd["uv"], fd.get("uv_size", [0, 0])), "texture": 0}
        el["faces"] = faces
    return el


def build_model(geo, name, tex_b64, res_w, res_h):
    elements = []
    groups = {}
    order = []
    poly = False
    for bone in geo.get("bones", []):
        g = {
            "name": bone["name"],
            "origin": [float(x) for x in bone.get("pivot", [0, 0, 0])],
            "rotation": conv_rot(bone.get("rotation")),
            "uuid": uid(),
            "export": True,
            "isOpen": False,
            "visibility": True,
            "children": [],
            "_parent": bone.get("parent"),
        }
        if "poly_mesh" in bone:
            poly = True  # not supported by this simple converter
        for cube in bone.get("cubes", []):
            el = cube_element(cube, bone.get("pivot", [0, 0, 0]))
            elements.append(el)
            g["children"].append(el["uuid"])
        groups[bone["name"]] = g
        order.append(bone["name"])

    roots = []
    for nm in order:
        g = groups[nm]
        parent = g.pop("_parent")
        if parent and parent in groups:
            groups[parent]["children"].append(g)
        else:
            roots.append(g)

    model = {
        "meta": {"format_version": "4.5", "model_format": "bedrock", "box_uv": True},
        "name": name,
        "geometry_name": name,
        "resolution": {"width": int(res_w), "height": int(res_h)},
        "elements": elements,
        "outliner": roots,
        "textures": [],
    }
    if tex_b64:
        model["textures"].append({
            "path": "",
            "name": name + ".png",
            "folder": "",
            "namespace": "",
            "id": "0",
            "particle": False,
            "render_mode": "default",
            "visible": True,
            "mode": "bitmap",
            "saved": False,
            "uuid": uid(),
            "source": "data:image/png;base64," + tex_b64,
        })
    return model, poly


def convert_one(geo_path, tex_path, out_dir, name_override):
    with open(geo_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    geos = data.get("minecraft:geometry")
    if not geos:
        print(f"  ! {geo_path}: not a 1.12+ bedrock geometry, skipped")
        return 0
    tex_b64 = None
    tw = th = None
    if tex_path and os.path.isfile(tex_path):
        with open(tex_path, "rb") as f:
            tex_b64 = base64.b64encode(f.read()).decode("ascii")
        tw, th = png_size(tex_path)

    made = 0
    for i, geo in enumerate(geos):
        desc = geo.get("description", {})
        base = name_override or os.path.basename(geo_path)
        for suffix in (".geo.json", ".json"):
            if base.endswith(suffix):
                base = base[: -len(suffix)]
        name = base if len(geos) == 1 else f"{base}_{i}"
        res_w = desc.get("texture_width") or tw or 16
        res_h = desc.get("texture_height") or th or 16
        model, poly = build_model(geo, name, tex_b64, res_w, res_h)
        out_path = os.path.join(out_dir, name + ".bbmodel")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(model, f)
        note = "  (has poly_mesh - open in BlockBench to finish)" if poly else ""
        print(f"  -> {out_path}{note}")
        made += 1
    return made


def find_texture(geo_path, tex_dir):
    if not tex_dir:
        return None
    stem = os.path.basename(geo_path)
    for suffix in (".geo.json", ".json"):
        if stem.endswith(suffix):
            stem = stem[: -len(suffix)]
    for root, _dirs, files in os.walk(tex_dir):
        for fn in files:
            if fn.lower() == stem.lower() + ".png":
                return os.path.join(root, fn)
    return None


def main():
    ap = argparse.ArgumentParser(description="Bedrock .geo.json -> BlockBench .bbmodel")
    ap.add_argument("input", help="a .geo.json file or a folder of them")
    ap.add_argument("--texture", help="texture PNG for a single model")
    ap.add_argument("--textures", help="folder of PNGs to match by name (batch)")
    ap.add_argument("--out", default=".", help="output folder for .bbmodel files")
    ap.add_argument("--name", help="blueprint name (single model only)")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    total = 0
    if os.path.isdir(args.input):
        geos = []
        for root, _dirs, files in os.walk(args.input):
            for fn in files:
                if fn.endswith(".geo.json") or (fn.endswith(".json") and "geo" in fn):
                    geos.append(os.path.join(root, fn))
        print(f"Found {len(geos)} geometry files")
        for gp in sorted(geos):
            tex = find_texture(gp, args.textures)
            total += convert_one(gp, tex, args.out, None)
    else:
        total += convert_one(args.input, args.texture, args.out, args.name)
    print(f"Done. Wrote {total} .bbmodel file(s) to {args.out}")
    print("Next: upload them to plugins/ModelEngine/blueprints/ and run /meg reload")


if __name__ == "__main__":
    main()
