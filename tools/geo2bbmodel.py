#!/usr/bin/env python3
"""
geo2bbmodel.py — Convert Cobblemon Bedrock assets -> Blockbench .bbmodel
(Generic Model format) for ModelEngine (PokeCraft / Paper).

Input layout (kirbycope/cobblemon-bedrock or extracted Cobblemon jar):
  models/entity/<NNNN_species>/<species>.geo.json
  animations/<NNNN_species>/<species>.animation.json
  textures/entity/<NNNN_species>/<species>.png

Usage:
  python3 geo2bbmodel.py <pack_root> -o out_dir [--only 0001_bulbasaur ...]
"""

import argparse
import base64
import json
import re
import sys
import uuid
from pathlib import Path

NUM_RE = re.compile(r"-?\d+(\.\d+)?([eE]-?\d+)?$")


def u():
    return str(uuid.uuid4())


def neg(v):
    """Negate a keyframe value that may be a number or a molang string."""
    if isinstance(v, (int, float)):
        return -v
    s = str(v).strip()
    if NUM_RE.match(s):
        f = float(s)
        return -f
    return f"-({s})"


def as_str(v):
    if isinstance(v, (int, float)):
        return round(float(v), 5)
    return str(v)


def load_json(p: Path):
    text = p.read_text(encoding="utf-8-sig", errors="replace")
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r",\s*([}\]])", r"\1", text)
    return json.loads(text)


# ---------------- geometry ----------------

def convert_geometry(geo_json):
    """Return (resolution, elements, outliner, bone_uuid_by_name)."""
    g = geo_json["minecraft:geometry"][0]
    desc = g["description"]
    res = {"width": desc.get("texture_width", 64),
           "height": desc.get("texture_height", 64)}

    elements = []
    groups = {}   # name -> group dict
    bone_uuid = {}

    for bone in g.get("bones", []):
        name = bone["name"]
        p = bone.get("pivot", [0, 0, 0])
        r = bone.get("rotation", [0, 0, 0])
        gid = u()
        bone_uuid[name] = gid
        group = {
            "name": name,
            "origin": [-p[0], p[1], p[2]],
            "rotation": [-r[0], -r[1], r[2]],
            "bedrock_binding": "",
            "color": 0,
            "uuid": gid,
            "export": True,
            "mirror_uv": bool(bone.get("mirror", False)),
            "isOpen": False,
            "locked": False,
            "visibility": True,
            "autouv": 0,
            "children": [],
            "_parent": bone.get("parent"),
        }
        groups[name] = group

        for ci, cube in enumerate(bone.get("cubes", [])):
            o = cube.get("origin", [0, 0, 0])
            s = cube.get("size", [0, 0, 0])
            cp = cube.get("pivot", [-(o[0] + s[0] / 2) * -1, 0, 0]
                          if False else None)
            cr = cube.get("rotation", [0, 0, 0])
            frm = [-(o[0] + s[0]), o[1], o[2]]
            to = [frm[0] + s[0], frm[1] + s[1], frm[2] + s[2]]
            uv = cube.get("uv")
            box_uv = isinstance(uv, list)
            el = {
                # unique per-cube name: BetterModel maps elements by name with
                # toMap and throws on duplicates, so a bone's cubes can't share
                # the bone's name (that caused "Duplicate key: head and head").
                "name": f"{name}_c{ci}",
                "box_uv": box_uv,
                "rescale": False,
                "locked": False,
                "from": frm,
                "to": to,
                "autouv": 0,
                "color": 0,
                "inflate": cube.get("inflate", 0),
                "rotation": [-cr[0], -cr[1], cr[2]],
                "origin": ([-cube["pivot"][0], cube["pivot"][1],
                            cube["pivot"][2]] if "pivot" in cube
                           else group["origin"]),
                "type": "cube",
                "uuid": u(),
            }
            if box_uv:
                el["uv_offset"] = uv[:2] if len(uv) >= 2 else [0, 0]
                el["mirror_uv"] = bool(cube.get("mirror",
                                                bone.get("mirror", False)))
            else:
                # per-face UV: BetterModel needs all 6 faces or it NPEs on a
                # null face (Cannot invoke ModelUV.hasTexture() ... north null)
                uvobj = uv if isinstance(uv, dict) else {}
                faces = {}
                for f in ("north", "east", "south", "west", "up", "down"):
                    fd = uvobj.get(f)
                    if isinstance(fd, dict) and "uv" in fd:
                        u0, v0 = fd["uv"][0], fd["uv"][1]
                        sz = fd.get("uv_size", [0, 0])
                        uw = sz[0] if len(sz) > 0 else 0
                        uh = sz[1] if len(sz) > 1 else 0
                        faces[f] = {"uv": [u0, v0, u0 + uw, v0 + uh], "texture": 0}
                    else:
                        faces[f] = {"uv": [0, 0, 0, 0]}
                el["faces"] = faces
            elements.append(el)
            group["children"].append(el["uuid"])

    # build tree
    outliner = []
    for name, group in groups.items():
        parent = group.pop("_parent", None)
        if parent and parent in groups:
            groups[parent]["children"].insert(0, group)
        else:
            outliner.append(group)

    return res, elements, outliner, bone_uuid


# ---------------- animations ----------------

CHANNEL_NEG = {
    "rotation": lambda x, y, z: (neg(x), neg(y), z),
    "position": lambda x, y, z: (neg(x), y, z),
    "scale": lambda x, y, z: (x, y, z),
}


def keyframes_from_channel(channel, data):
    """Bedrock channel data -> list of BB keyframes."""
    kfs = []
    if isinstance(data, list):  # constant [x,y,z]
        data = {"0.0": data}
    if isinstance(data, (int, float, str)):
        data = {"0.0": [data, data, data]}
    for t, val in data.items():
        interp = "linear"
        if isinstance(val, dict):
            if val.get("lerp_mode") == "catmullrom":
                interp = "catmullrom"
            val = val.get("post", val.get("pre", [0, 0, 0]))
        if isinstance(val, (int, float, str)):
            val = [val, val, val]
        x, y, z = CHANNEL_NEG[channel](*val[:3])
        kfs.append({
            "channel": channel,
            "data_points": [{"x": as_str(x), "y": as_str(y),
                             "z": as_str(z)}],
            "uuid": u(),
            "time": float(t),
            "color": -1,
            "interpolation": interp,
        })
    kfs.sort(key=lambda k: k["time"])
    return kfs


def convert_animations(anim_json, bone_uuid):
    out = []
    for full_name, a in (anim_json.get("animations") or {}).items():
        short = full_name.split(".")[-1]
        loop = a.get("loop", False)
        loop_mode = ("loop" if loop is True
                     else "hold" if loop == "hold_on_last_frame"
                     else "once")
        animators = {}
        length = float(a.get("animation_length", 0) or 0)
        for bone_name, channels in (a.get("bones") or {}).items():
            gid = bone_uuid.get(bone_name)
            if not gid:
                continue
            kfs = []
            for ch in ("rotation", "position", "scale"):
                if ch in channels:
                    kfs.extend(keyframes_from_channel(ch, channels[ch]))
            if kfs:
                length = max(length, max(k["time"] for k in kfs))
                animators[gid] = {"name": bone_name, "type": "bone",
                                  "keyframes": kfs}
        out.append({
            "uuid": u(),
            "name": short,
            "loop": loop_mode,
            "override": False,
            "length": length,
            "snapping": 24,
            "selected": False,
            "anim_time_update": "",
            "blend_weight": "",
            "start_delay": "",
            "loop_delay": "",
            "animators": animators,
        })
    return out


# ---------------- assembly ----------------

def build_bbmodel(name, geo_json, anim_json, texture_path: Path):
    res, elements, outliner, bone_uuid = convert_geometry(geo_json)
    animations = convert_animations(anim_json, bone_uuid) if anim_json else []

    textures = []
    if texture_path and texture_path.exists():
        b64 = base64.b64encode(texture_path.read_bytes()).decode()
        textures.append({
            "path": str(texture_path),
            "name": texture_path.name,
            "folder": "", "namespace": "", "id": "0",
            "particle": False, "render_mode": "default",
            "visible": True, "mode": "bitmap", "saved": True,
            "uuid": u(),
            "source": f"data:image/png;base64,{b64}",
        })

    return {
        "meta": {"format_version": "4.5", "model_format": "free",
                 "box_uv": True},
        "name": name,
        "model_identifier": name,
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "resolution": res,
        "elements": elements,
        "outliner": outliner,
        "textures": textures,
        "animations": animations,
    }


def find_texture(root: Path, key: str, name: str):
    cands = [
        root / "textures" / "entity" / key / f"{name}.png",
        root / "textures" / "entity" / key / f"{name}_male.png",
    ]
    for c in cands:
        if c.exists():
            return c
    d = root / "textures" / "entity" / key
    if d.is_dir():
        pngs = sorted(p for p in d.glob("*.png") if "shiny" not in p.name)
        if pngs:
            return pngs[0]
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root", help="pack root (contains models/animations/textures)")
    ap.add_argument("-o", "--out", default="bbmodels")
    ap.add_argument("--only", nargs="*", help="species dirs to convert")
    args = ap.parse_args()

    root = Path(args.root)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    models_dir = root / "models" / "entity"

    ok, failed = 0, []
    dirs = sorted(d for d in models_dir.iterdir() if d.is_dir())
    if args.only:
        dirs = [d for d in dirs if d.name in set(args.only)]

    for d in dirs:
        key = d.name
        m = re.match(r"^\d{4}_(.+)$", key)
        name = m.group(1) if m else key
        geo_path = d / f"{name}.geo.json"
        if not geo_path.exists():
            geos = list(d.glob("*.geo.json"))
            if not geos:
                failed.append((key, "no geo"))
                continue
            geo_path = geos[0]
            name = geo_path.name.replace(".geo.json", "")
        try:
            geo = load_json(geo_path)
            anim_path = root / "animations" / key / f"{name}.animation.json"
            if not anim_path.exists():
                anims = list((root / "animations" / key).glob(
                    "*.animation.json")) if (root / "animations" / key
                                             ).is_dir() else []
                anim_path = anims[0] if anims else None
            anim = load_json(anim_path) if anim_path else None
            tex = find_texture(root, key, name)
            bb = build_bbmodel(name, geo, anim, tex)
            (out / f"{name}.bbmodel").write_text(
                json.dumps(bb, separators=(",", ":")), encoding="utf-8")
            ok += 1
        except Exception as e:  # noqa: BLE001
            failed.append((key, str(e)[:120]))

    print(f"Converted {ok}/{len(dirs)}")
    for k, why in failed[:15]:
        print(f"  FAIL {k}: {why}")
    if len(failed) > 15:
        print(f"  ... +{len(failed) - 15} more")


if __name__ == "__main__":
    main()
