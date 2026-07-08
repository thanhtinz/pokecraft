#!/usr/bin/env python3
"""
Convert Bedrock entity geometry (.geo.json) into BlockBench .bbmodel files so
they can be dropped into BetterModel (plugins/BetterModel/models/) and used by
PokeCraft on the Java client (Bedrock/mobile players see a mapped vanilla mob).

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


def _point(val):
    """Normalise a bedrock keyframe value to [x, y, z] (numbers or molang strings)."""
    if isinstance(val, dict):  # { "post": [...], "lerp_mode": ... }
        val = val.get("post", val.get("pre", [0, 0, 0]))
    if isinstance(val, list):
        v = list(val) + [0, 0, 0]
        return v[:3]
    return [val, val, val]  # scalar (e.g. uniform scale) or single molang string


def _sign(v, s):
    if s == 1:
        return v
    if isinstance(v, (int, float)):
        return -v
    return "-(" + str(v) + ")"  # negate a molang expression


def _keyframes(channel, kf_dict):
    frames = []
    for t, val in kf_dict.items():
        try:
            time = float(t)
        except (TypeError, ValueError):
            continue
        pts = _point(val)
        if channel == "rotation":
            pts = [_sign(pts[i], ROT_SIGN[i]) for i in range(3)]
        frames.append({
            "channel": channel,
            "data_points": [{"x": str(pts[0]), "y": str(pts[1]), "z": str(pts[2])}],
            "uuid": uid(),
            "time": time,
            "color": -1,
            "interpolation": "linear",
        })
    frames.sort(key=lambda f: f["time"])
    return frames


def build_animations(anim_data, model_name, name_to_uuid):
    """Convert a bedrock .animation.json into BlockBench animation objects."""
    out = []
    for anim_id, anim in (anim_data or {}).get("animations", {}).items():
        short = anim_id
        if short.startswith("animation."):
            short = short[len("animation."):]
        prefix = model_name + "."
        if short.startswith(prefix):
            short = short[len(prefix):]
        animators = {}
        for bone, chans in anim.get("bones", {}).items():
            guid = name_to_uuid.get(bone)
            if not guid:
                continue
            kfs = []
            for channel in ("rotation", "position", "scale"):
                if channel in chans:
                    ch = chans[channel]
                    if isinstance(ch, dict):
                        kfs += _keyframes(channel, ch)
                    else:  # a single constant value -> one keyframe at t=0
                        kfs += _keyframes(channel, {"0.0": ch})
            if kfs:
                animators[guid] = {"name": bone, "type": "bone", "keyframes": kfs}
        if not animators:
            continue
        out.append({
            "uuid": uid(),
            "name": short,
            "loop": "loop" if anim.get("loop") else "once",
            "override": False,
            "length": float(anim.get("animation_length", 0) or 0),
            "snapping": 24,
            "selected": False,
            "anim_time_update": "",
            "blend_weight": "",
            "start_delay": "",
            "loop_delay": "",
            "animators": animators,
        })
    return out


def build_model(geo, name, tex_b64, res_w, res_h):
    elements = []
    groups = {}
    order = []
    poly = False
    name_to_uuid = {}
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
        name_to_uuid[bone["name"]] = g["uuid"]
        if "poly_mesh" in bone:
            poly = True  # not supported by this simple converter
        for ci, cube in enumerate(bone.get("cubes", [])):
            el = cube_element(cube, bone.get("pivot", [0, 0, 0]))
            # unique element name so BetterModel's toMap doesn't hit duplicates
            el["name"] = f"{bone['name']}_c{ci}"
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
    return model, poly, name_to_uuid


def convert_one(geo_path, tex_path, anim_path, out_dir, name_override):
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
    anim_data = None
    if anim_path and os.path.isfile(anim_path):
        try:
            with open(anim_path, "r", encoding="utf-8") as f:
                anim_data = json.load(f)
        except Exception:
            anim_data = None

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
        model, poly, name_to_uuid = build_model(geo, name, tex_b64, res_w, res_h)
        anims = build_animations(anim_data, name, name_to_uuid) if anim_data else []
        if anims:
            model["animations"] = anims
        out_path = os.path.join(out_dir, name + ".bbmodel")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(model, f)
        extra = []
        if anims:
            extra.append(f"{len(anims)} anim")
        if poly:
            extra.append("poly_mesh - finish in BlockBench")
        note = ("  (" + ", ".join(extra) + ")") if extra else ""
        print(f"  -> {out_path}{note}")
        made += 1
    return made


def _stem(geo_path):
    stem = os.path.basename(geo_path)
    for suffix in (".geo.json", ".json"):
        if stem.endswith(suffix):
            stem = stem[: -len(suffix)]
    return stem


def find_texture(geo_path, tex_dir):
    if not tex_dir:
        return None
    stem = _stem(geo_path)
    for root, _dirs, files in os.walk(tex_dir):
        for fn in files:
            if fn.lower() == stem.lower() + ".png":
                return os.path.join(root, fn)
    return None


def find_animation(geo_path, anim_dir):
    if not anim_dir:
        return None
    stem = _stem(geo_path).lower()
    for root, _dirs, files in os.walk(anim_dir):
        for fn in files:
            low = fn.lower()
            if low.endswith(".animation.json") and low.split(".animation.json")[0] == stem:
                return os.path.join(root, fn)
    return None


def main():
    ap = argparse.ArgumentParser(description="Bedrock .geo.json -> BlockBench .bbmodel")
    ap.add_argument("input", help="a .geo.json file or a folder of them")
    ap.add_argument("--texture", help="texture PNG for a single model")
    ap.add_argument("--textures", help="folder of PNGs to match by name (batch)")
    ap.add_argument("--animation", help="animation .json for a single model")
    ap.add_argument("--animations", help="folder of .animation.json to match by name (batch)")
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
            anim = find_animation(gp, args.animations)
            total += convert_one(gp, tex, anim, args.out, None)
    else:
        total += convert_one(args.input, args.texture, args.animation, args.out, args.name)
    print(f"Done. Wrote {total} .bbmodel file(s) to {args.out}")
    print("Next: upload them to plugins/BetterModel/models/ and run /bm reload")


if __name__ == "__main__":
    main()
