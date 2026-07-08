#!/usr/bin/env python3
"""
cobblemon2geyser.py
===================
Generate GeyserModelEnginePackGenerator "input/" folders directly from
Cobblemon's Bedrock-format assets (mod jar or extracted assets dir).

Why: Cobblemon geometries/animations are ALREADY Bedrock format, so instead of
round-tripping through .bbmodel (Java/BetterModel pipeline), Bedrock players
get the original assets with zero conversion loss.

Output structure (consumed by GeyserModelEnginePackGenerator extension):

    input/
      <variant_id>/
        <variant_id>.geo.json         # Bedrock geometry ("minecraft:geometry")
        <variant_id>.animation.json   # merged Bedrock animations
        <texture>.png                 # resolved texture for this variant
        config.json                   # ModelConfig (head_rotation etc.)

Variant naming matches the PokeCraft/BetterModel fallback chain:
    <species>_<gender>_shiny -> <species>_shiny -> <species>_<gender> -> <species>

A variant folder is only emitted when its resolved (model, texture) differs
from what the fallback chain already provides, keeping the pack minimal.

Usage:
    python3 cobblemon2geyser.py --jar Cobblemon-fabric-1.x.jar --out input/
    python3 cobblemon2geyser.py --assets ./assets --out input/ --zip pack_input.zip
    python3 cobblemon2geyser.py --jar ... --out input/ --sanitize-molang --species pikachu,bulbasaur
"""

import argparse
import io
import json
import re
import shutil
import sys
import zipfile
from collections import OrderedDict
from pathlib import Path, PurePosixPath

# ---------------------------------------------------------------------------
# Asset source abstraction (jar zip OR extracted directory)
# ---------------------------------------------------------------------------

class AssetSource:
    """Uniform read access to assets/cobblemon/** from a jar or a directory."""

    def __init__(self, jar: Path | None, assets_dir: Path | None):
        self.zf = zipfile.ZipFile(jar) if jar else None
        self.root = assets_dir
        self._index = None

    def close(self):
        if self.zf:
            self.zf.close()

    def list_files(self) -> list[str]:
        """All file paths, normalized to 'assets/cobblemon/...' with '/'."""
        if self._index is not None:
            return self._index
        files = []
        if self.zf:
            for n in self.zf.namelist():
                if n.startswith("assets/cobblemon/") and not n.endswith("/"):
                    files.append(n)
        else:
            base = self.root
            # accept either .../assets/cobblemon or a dir containing assets/
            candidates = [base / "assets" / "cobblemon", base]
            for c in candidates:
                if c.is_dir() and (c / "bedrock").is_dir():
                    for p in c.rglob("*"):
                        if p.is_file():
                            rel = p.relative_to(c).as_posix()
                            files.append(f"assets/cobblemon/{rel}")
                    self._dir_base = c
                    break
            else:
                sys.exit(f"[!] Could not locate assets/cobblemon under {base}")
        self._index = files
        return files

    def read_bytes(self, path: str) -> bytes:
        if self.zf:
            return self.zf.read(path)
        rel = path.removeprefix("assets/cobblemon/")
        return (self._dir_base / rel).read_bytes()

    def read_json(self, path: str):
        raw = self.read_bytes(path).decode("utf-8-sig")
        # Cobblemon jsons occasionally contain trailing commas -> tolerant parse
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            cleaned = re.sub(r",\s*([}\]])", r"\1", raw)
            return json.loads(cleaned)


# ---------------------------------------------------------------------------
# Resolver / variation handling
# ---------------------------------------------------------------------------

GENDER_ASPECTS = ("male", "female")

def strip_ns(ref: str) -> str:
    return ref.split(":", 1)[1] if ":" in ref else ref

def as_ref(v):
    """Coerce a Cobblemon model/texture reference to a string, or None.
    Some variations give a list (layered textures) or an object instead of a
    plain string ref - take the first string of a list, otherwise skip."""
    if isinstance(v, str):
        return v
    if isinstance(v, list):
        for item in v:
            if isinstance(item, str):
                return item
    return None

def build_indexes(src: AssetSource):
    """Index geometry files, animation folders, textures, resolvers."""
    files = src.list_files()
    geo_by_name = {}        # 'bulbasaur' (from bulbasaur.geo.json) -> path
    anim_files = {}         # species folder name -> [animation json paths]
    texture_paths = set()   # full asset paths of pngs
    resolver_paths = []

    for f in files:
        pp = PurePosixPath(f)
        if "/bedrock/pokemon/models/" in f and f.endswith(".geo.json"):
            geo_by_name[pp.name[:-len(".geo.json")]] = f
        elif "/bedrock/pokemon/animations/" in f and f.endswith(".json"):
            # animations/<species>/<file>.animation.json
            parts = pp.parts
            i = parts.index("animations")
            species_dir = parts[i + 1] if i + 1 < len(parts) - 1 else pp.stem
            anim_files.setdefault(species_dir, []).append(f)
        elif "/bedrock/pokemon/resolvers/" in f and f.endswith(".json"):
            resolver_paths.append(f)
        elif f.startswith("assets/cobblemon/textures/pokemon/") and f.endswith(".png"):
            texture_paths.add(f)

    return geo_by_name, anim_files, texture_paths, resolver_paths


def resolve_variation(variations: list[dict], aspects: set[str]) -> dict:
    """Cobblemon semantics: apply every variation whose aspect list is a
    subset of the requested aspects, in file order; later fields override."""
    out = {}
    for var in variations:
        needed = set(var.get("aspects", []))
        if needed <= aspects:
            for key in ("model", "texture", "poser", "layers"):
                if key in var and var[key] is not None:
                    out[key] = var[key]
    return out


def collect_species(src: AssetSource, geo_by_name, resolver_paths):
    """Parse resolvers -> {species_name: variations[]}. Falls back to
    name-matched geometry when a species has no resolver."""
    species = {}
    for rp in resolver_paths:
        try:
            data = src.read_json(rp)
        except Exception as e:
            print(f"[warn] bad resolver {rp}: {e}")
            continue
        name = strip_ns(data.get("species", PurePosixPath(rp).stem))
        # numbered file names like 0001_bulbasaur -> keep species field if present
        name = re.sub(r"^\d+_", "", name)
        species.setdefault(name, []).extend(data.get("variations", []))
    # fallback: geometries without resolver
    for gname in geo_by_name:
        base = re.sub(r"_(male|female)$", "", gname)
        if base not in species:
            species[base] = [{
                "aspects": [],
                "model": f"cobblemon:{base}.geo",
                "texture": f"cobblemon:textures/pokemon/{base}.png",
            }]
    return species


# ---------------------------------------------------------------------------
# Molang sanitizing (Bedrock client does not know Cobblemon's custom queries)
# ---------------------------------------------------------------------------

CUSTOM_QUERY_DEFAULTS = {
    # cobblemon-side queries that don't exist on the Bedrock client
    r"q(?:uery)?\.head_yaw": "0.0",
    r"q(?:uery)?\.head_pitch": "0.0",
    r"q(?:uery)?\.water_depth": "0.0",
    r"q(?:uery)?\.entity_size": "1.0",
    r"q(?:uery)?\.pokemon_scale": "1.0",
}

def sanitize_molang(text: str) -> str:
    for pat, repl in CUSTOM_QUERY_DEFAULTS.items():
        text = re.sub(pat + r"\b", repl, text, flags=re.IGNORECASE)
    return text


def merge_animations(src: AssetSource, paths: list[str], do_sanitize: bool) -> dict | None:
    merged = OrderedDict()
    fmt = "1.8.0"
    for p in sorted(paths):
        try:
            data = src.read_json(p)
        except Exception as e:
            print(f"[warn] bad animation {p}: {e}")
            continue
        fmt = data.get("format_version", fmt)
        for k, v in data.get("animations", {}).items():
            # 'animation.bulbasaur.ground_idle' -> keep short key 'ground_idle'
            short = k.split(".")[-1] if k.startswith("animation.") else k
            merged.setdefault(short, v)
    if not merged:
        return None
    out = {"format_version": fmt, "animations": merged}
    if do_sanitize:
        out = json.loads(sanitize_molang(json.dumps(out)))
    return out


# ---------------------------------------------------------------------------
# Main generation
# ---------------------------------------------------------------------------

def parse_variant_name(name: str) -> tuple[str, str | None, bool]:
    """'mr_mime_female_shiny' -> ('mr_mime', 'female', True). Species may
    contain underscores, so strip known suffixes right-to-left."""
    shiny = False
    gender = None
    if name.endswith("_shiny"):
        shiny = True
        name = name[: -len("_shiny")]
    for g in GENDER_ASPECTS:
        if name.endswith("_" + g):
            gender = g
            name = name[: -(len(g) + 1)]
            break
    return name, gender, shiny


def geo_meta(geo_text: str):
    """(texture_width, texture_height, [bone_names]) from the first geometry."""
    try:
        obj = json.loads(re.sub(r",\s*([}\]])", r"\1", geo_text))
    except Exception:
        return 64, 64, []
    geos = obj.get("minecraft:geometry", [])
    if not geos:
        return 64, 64, []
    g = geos[0]
    desc = g.get("description", {})
    tw = int(desc.get("texture_width", 64) or 64)
    th = int(desc.get("texture_height", 64) or 64)
    bones = [b.get("name") for b in g.get("bones", []) if b.get("name")]
    return tw, th, bones


def write_model_folder(vdir: Path, vid: str, geo_raw: str,
                       merged_anim: dict | None, tex_bytes: bytes) -> None:
    """Write a model folder in GeyserModelEnginePackGenerator's expected layout:
    <vid>.geo.json, optional <vid>.animation.json, <vid>.png, and a config.json
    carrying per_texture_uv_size + binding_bones (without which the generator
    produces an empty pack)."""
    vdir.mkdir(parents=True, exist_ok=True)
    tw, th, bones = geo_meta(geo_raw)
    (vdir / f"{vid}.geo.json").write_text(geo_raw, encoding="utf-8")
    if merged_anim:
        (vdir / f"{vid}.animation.json").write_text(
            json.dumps(merged_anim, ensure_ascii=False), encoding="utf-8")
    (vdir / f"{vid}.png").write_bytes(tex_bytes)
    config = {
        "head_rotation": True,
        "material": "entity_alphatest_change_color_one_sided",
        "blend_transition": True,
        "per_texture_uv_size": {vid: [tw, th]},
        "binding_bones": {vid: bones},
        "anim_textures": {},
    }
    (vdir / "config.json").write_text(
        json.dumps(config, indent=2), encoding="utf-8")


def variant_id(species: str, gender: str | None, shiny: bool) -> str:
    vid = species
    if gender:
        vid += f"_{gender}"
    if shiny:
        vid += "_shiny"
    return vid


def generate(src: AssetSource, out_dir: Path, only: set[str] | None,
             do_sanitize: bool, match_list: list[str] | None = None) -> int:
    geo_by_name, anim_files, texture_paths, resolver_paths = build_indexes(src)
    species_map = collect_species(src, geo_by_name, resolver_paths)

    print(f"[i] species with resolvers/geo: {len(species_map)}")
    emitted = 0

    def emit(vid: str, species: str, aspects: set[str],
             variations: list[dict]) -> bool:
        resolved = resolve_variation(variations, aspects)
        model_ref = as_ref(resolved.get("model"))
        texture_ref = as_ref(resolved.get("texture"))
        if not model_ref or not texture_ref:
            return False
        geo_name = strip_ns(model_ref).removesuffix(".geo")
        geo_path = geo_by_name.get(geo_name)
        if not geo_path:
            print(f"[warn] {vid}: geometry '{geo_name}' not found, skipped")
            return False
        tex_path = "assets/cobblemon/" + strip_ns(texture_ref)
        if tex_path not in texture_paths:
            print(f"[warn] {vid}: texture '{texture_ref}' not found, skipped")
            return False
        anims = anim_files.get(species) or anim_files.get(geo_name) or []
        merged_anim = merge_animations(src, anims, do_sanitize)
        geo_raw = src.read_bytes(geo_path).decode("utf-8-sig")
        if do_sanitize:
            geo_raw = sanitize_molang(geo_raw)
        write_model_folder(out_dir / vid, vid, geo_raw, merged_anim,
                           src.read_bytes(tex_path))
        return True

    if match_list is not None:
        # emit CHÍNH XÁC bộ tên này (parity 1:1 với models/ của BetterModel)
        missing = []
        for raw in match_list:
            vid = raw.strip().removesuffix(".bbmodel").lower()
            if not vid:
                continue
            species, gender, shiny = parse_variant_name(vid)
            variations = species_map.get(species)
            if not variations:
                missing.append(vid)
                continue
            aspects = set()
            if gender:
                aspects.add(gender)
            if shiny:
                aspects.add("shiny")
            if emit(vid, species, aspects, variations):
                emitted += 1
            else:
                missing.append(vid)
        if missing:
            print(f"[warn] {len(missing)} names unresolved: {missing[:10]}{'...' if len(missing)>10 else ''}")
        return emitted

    for species, variations in sorted(species_map.items()):
        if only and species not in only:
            continue

        gendered = any(
            set(v.get("aspects", [])) & set(GENDER_ASPECTS) for v in variations
        )
        genders = [None] + ([g for g in GENDER_ASPECTS] if gendered else [])

        seen_signatures = {}  # fallback dedup: signature of already-emitted parents

        # emission order = fallback order (base first) so children can dedup
        combos = []
        for shiny in (False, True):
            for g in genders:
                combos.append((g, shiny))
        combos.sort(key=lambda c: (c[1], c[0] is not None))  # base, genders, shiny...

        for gender, shiny in combos:
            aspects = set()
            if gender:
                aspects.add(gender)
            if shiny:
                aspects.add("shiny")
            resolved = resolve_variation(variations, aspects)
            model_ref = as_ref(resolved.get("model"))
            texture_ref = as_ref(resolved.get("texture"))
            if not model_ref or not texture_ref:
                continue

            sig = (model_ref, texture_ref)
            # fallback chain parents for dedup
            parents = []
            if gender and shiny:
                parents = [variant_id(species, None, True),
                           variant_id(species, gender, False),
                           species]
            elif gender:
                parents = [species]
            elif shiny:
                parents = [species]
            if any(seen_signatures.get(p) == sig for p in parents):
                continue  # identical to fallback -> skip

            vid = variant_id(species, gender, shiny)

            # ---- geometry
            geo_name = strip_ns(model_ref).removesuffix(".geo")
            geo_path = geo_by_name.get(geo_name)
            if not geo_path:
                print(f"[warn] {vid}: geometry '{geo_name}' not found, skipped")
                continue

            # ---- texture
            tex_path = "assets/" + strip_ns(texture_ref) if ":" in texture_ref \
                else texture_ref
            tex_path = tex_path.replace("assets/textures", "assets/cobblemon/textures") \
                if not tex_path.startswith("assets/cobblemon/") else tex_path
            if tex_path not in texture_paths:
                # try namespaced form directly
                alt = "assets/cobblemon/" + strip_ns(texture_ref)
                if alt in texture_paths:
                    tex_path = alt
                else:
                    print(f"[warn] {vid}: texture '{texture_ref}' not found, skipped")
                    continue

            # ---- animations (per species folder, name-matched)
            anims = anim_files.get(species) or anim_files.get(geo_name) or []
            merged_anim = merge_animations(src, anims, do_sanitize)

            # ---- write folder
            geo_raw = src.read_bytes(geo_path).decode("utf-8-sig")
            if do_sanitize:
                geo_raw = sanitize_molang(geo_raw)
            write_model_folder(out_dir / vid, vid, geo_raw, merged_anim,
                               src.read_bytes(tex_path))

            seen_signatures[vid] = sig
            emitted += 1

    return emitted


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--jar", type=Path, help="Cobblemon mod jar")
    g.add_argument("--assets", type=Path, help="extracted assets directory")
    ap.add_argument("--out", type=Path, default=Path("input"),
                    help="output input/ directory (default: input)")
    ap.add_argument("--zip", type=Path, default=None,
                    help="also zip the output folder to this path")
    ap.add_argument("--match-list", type=Path, default=None,
                    help="file txt: moi dong 1 ten bbmodel tren server, emit dung 1:1 bo nay")
    ap.add_argument("--species", default=None,
                    help="comma-separated species filter (debug)")
    ap.add_argument("--sanitize-molang", action="store_true",
                    help="replace Cobblemon-only molang queries with constants")
    ap.add_argument("--clean", action="store_true",
                    help="wipe output dir before generating")
    args = ap.parse_args()

    if args.clean and args.out.exists():
        shutil.rmtree(args.out)
    args.out.mkdir(parents=True, exist_ok=True)

    only = set(args.species.split(",")) if args.species else None
    src = AssetSource(args.jar, args.assets)
    try:
        match = args.match_list.read_text().splitlines() if args.match_list else None
        n = generate(src, args.out, only, args.sanitize_molang, match)
    finally:
        src.close()

    print(f"[ok] emitted {n} model folders -> {args.out}")

    if args.zip:
        with zipfile.ZipFile(args.zip, "w", zipfile.ZIP_DEFLATED) as zf:
            for p in sorted(args.out.rglob("*")):
                if p.is_file():
                    zf.write(p, p.relative_to(args.out.parent).as_posix())
        print(f"[ok] zipped -> {args.zip}")


if __name__ == "__main__":
    main()
