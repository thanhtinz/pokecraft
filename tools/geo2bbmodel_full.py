#!/usr/bin/env python3
"""
geo2bbmodel_full.py — variant-aware Cobblemon -> BetterModel converter.

For every species directory, emits one .bbmodel per texture variant:
  pikachu.bbmodel, pikachu_shiny.bbmodel, pikachu_alola.bbmodel,
  pikachu_female.bbmodel, relicanth_male.bbmodel, ...

Rules:
  - geo base chosen as the longest geo-file stem that prefixes the texture stem
  - textures with no matching geo fall back to the primary (shortest) geo
  - geo bases with no texture of their own reuse the primary texture
  - animation file matched by geo base, falling back to the primary anim file

Usage:
  python3 geo2bbmodel_full.py <pack_root> -o out_dir
"""

import argparse
import re
from pathlib import Path

# reuse core conversion from v1
import importlib.util as _ilu
import json
import sys

_spec = _ilu.spec_from_file_location(
    "geo2bbmodel", Path(__file__).parent / "geo2bbmodel.py")
_v1 = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(_v1)

load_json = _v1.load_json
build_bbmodel = _v1.build_bbmodel


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root")
    ap.add_argument("-o", "--out", default="bbmodels_full")
    ap.add_argument("--no-anim", action="store_true",
                    help="skip animations (static models) - avoids BetterModel "
                         "molang evaluation, which spikes CPU on big packs")
    args = ap.parse_args()

    root = Path(args.root)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    models_dir = root / "models" / "entity"
    ok, failed = 0, []

    for d in sorted(p for p in models_dir.iterdir() if p.is_dir()):
        key = d.name
        geo_files = sorted(d.glob("*.geo.json"))
        if not geo_files:
            failed.append((key, "no geo"))
            continue

        # geo bases, primary = shortest stem
        bases = {g.name[:-len(".geo.json")]: g for g in geo_files}
        primary = min(bases, key=len)

        anim_dir = root / "animations" / key
        anims = {}
        if anim_dir.is_dir():
            for a in anim_dir.glob("*.animation.json"):
                anims[a.name[:-len(".animation.json")]] = a
        primary_anim = anims.get(primary) or (
            next(iter(anims.values())) if anims else None)

        tex_dir = root / "textures" / "entity" / key
        textures = sorted(tex_dir.glob("*.png")) if tex_dir.is_dir() else []
        tex_stems = {t.stem: t for t in textures}

        jobs = {}  # out_name -> (geo_base, texture_path)
        for stem, tpath in tex_stems.items():
            match = None
            for b in sorted(bases, key=len, reverse=True):
                if stem == b or stem.startswith(b + "_"):
                    match = b
                    break
            jobs[stem] = (match or primary, tpath)

        # geo bases lacking their own texture reuse the primary texture
        prim_tex = tex_stems.get(primary) or (textures[0] if textures else None)
        for b in bases:
            if b not in jobs:
                jobs[b] = (b, prim_tex)

        # convert
        geo_cache, anim_cache = {}, {}
        for out_name, (geo_base, tpath) in sorted(jobs.items()):
            try:
                if geo_base not in geo_cache:
                    geo_cache[geo_base] = load_json(bases[geo_base])
                apath = None if args.no_anim else (anims.get(geo_base) or primary_anim)
                akey = str(apath)
                if akey not in anim_cache:
                    anim_cache[akey] = load_json(apath) if apath else None
                bb = build_bbmodel(out_name, geo_cache[geo_base],
                                   anim_cache[akey], tpath)
                (out / f"{out_name}.bbmodel").write_text(
                    json.dumps(bb, separators=(",", ":")), encoding="utf-8")
                ok += 1
            except Exception as e:  # noqa: BLE001
                failed.append((f"{key}/{out_name}", str(e)[:100]))

    print(f"Converted {ok} variant models")
    if failed:
        print(f"Failed {len(failed)}:")
        for k, why in failed[:15]:
            print(f"  {k}: {why}")
        if len(failed) > 15:
            print(f"  ... +{len(failed) - 15}")


if __name__ == "__main__":
    main()
