#!/usr/bin/env python3
"""Imports species data from Cobblemon to expand the PokeCraft dex.

Cobblemon (https://gitlab.com/cable-mc/cobblemon, code under MPL 2.0) ships
accurate species JSON: base stats, types, catch rate, exp yield and evolution
chains. This script downloads those files for the requested generation(s) and
converts them into PokeCraft's species format.

What we take from Cobblemon:  base stats, types, catch rate, exp yield,
                              evolution chains, national dex number, legendary flag.
What we generate locally:     learnsets (from PokeCraft's own 89-move pool, via
                              generate_gen1's per-type pools) and biome spawn
                              tables - so every imported species is guaranteed a
                              working moveset within PokeCraft's existing moves.

Underlying Pokemon names/stats are Nintendo/Game Freak IP; this is fine for a
private server but do not redistribute commercially.

Usage:
    python3 tools/import_cobblemon.py 2          # import generation 2
    python3 tools/import_cobblemon.py 2 3 4      # several generations
"""
import json
import os
import sys
import urllib.request

import generate_gen1 as g1  # reuse learnset/spawn/biome helpers

RAW = ("https://gitlab.com/cable-mc/cobblemon/-/raw/main/"
       "common/src/main/resources/data/cobblemon/species/generation{gen}")
API = ("https://gitlab.com/api/v4/projects/cable-mc%2Fcobblemon/repository/tree"
       "?path=common/src/main/resources/data/cobblemon/species/generation{gen}"
       "&per_page=500&ref=main")

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
SPECIES_DIR = os.path.join(ROOT, "species")

# Cobblemon evolution stones -> the five stones PokeCraft implements.
STONE_MAP = {
    "thunder_stone": "thunder_stone",
    "fire_stone": "fire_stone",
    "water_stone": "water_stone",
    "leaf_stone": "leaf_stone",
    "moon_stone": "moon_stone",
    "sun_stone": "leaf_stone",   # PokeCraft has no Sun Stone; Leaf is the closest
}

# Do not overwrite the hand-curated / already-generated Gen 1 files.
def existing_ids():
    return {f[:-5] for f in os.listdir(SPECIES_DIR)
            if f.endswith(".json") and not f.startswith("_")}


def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "pokecraft-import"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def list_species(gen):
    tree = fetch_json(API.format(gen=gen))
    return sorted(x["name"][:-5] for x in tree
                  if x["name"].endswith(".json"))


def parse_types(data):
    types = [data["primaryType"].upper()]
    if data.get("secondaryType"):
        types.append(data["secondaryType"].upper())
    return types


def parse_evolutions(data):
    out = []
    for evo in data.get("evolutions") or []:
        result = evo.get("result")
        if not result:
            continue
        # a "result" can be "pikachu" or "pikachu key=value"; take the species id
        result = result.split()[0]
        level = None
        for req in evo.get("requirements") or []:
            if req.get("variant") == "level":
                level = req.get("minLevel")
                break
        variant = evo.get("variant")
        ctx = evo.get("requiredContext")
        if level is not None:
            out.append({"level": int(level), "to": result, "item": None})
        elif variant == "item_interact" and ctx:
            stone = ctx.split(":")[-1]
            mapped = STONE_MAP.get(stone)
            if mapped:
                out.append({"level": 0, "to": result, "item": mapped})
            else:
                out.append({"level": 30, "to": result, "item": None})
        else:
            # friendship / trade / time / move-taught -> reachable level fallback
            out.append({"level": 37 if variant == "trade" else 30,
                        "to": result, "item": None})
    return out


def is_legendary(data):
    labels = {str(x).lower() for x in data.get("labels") or []}
    return bool(labels & {"legendary", "mythical", "ultra_beast"})


def convert(sid, data, evo_targets):
    stats = data["baseStats"]
    s = (stats["hp"], stats["attack"], stats["defence"],
         stats["special_attack"], stats["special_defence"], stats["speed"])
    types = parse_types(data)
    dex = data.get("nationalPokedexNumber", 9999)
    legendary = is_legendary(data)
    evolutions = parse_evolutions(data)
    return {
        "id": sid,
        "dex": dex,
        "name": data["name"],
        "types": types,
        "baseStats": {"hp": s[0], "atk": s[1], "def": s[2],
                      "spa": s[3], "spd": s[4], "spe": s[5]},
        "catchRate": data.get("catchRate", 45),
        "expYield": data.get("baseExperienceYield", 64),
        "modelId": sid,
        "learnset": g1.learnset_for(dex, types, s),
        "evolution": None,
        "evolutions": evolutions or None,
        "spawn": g1.spawn_for(types, data.get("catchRate", 45),
                              sid in evo_targets, legendary),
    }


def main():
    gens = [a for a in sys.argv[1:] if a.isdigit()]
    if not gens:
        print("usage: python3 tools/import_cobblemon.py <gen> [gen...]")
        sys.exit(1)

    curated = existing_ids()
    # first pass: fetch everything so we know all evolution targets.
    # The Cobblemon filename is used as the species id so evolution "result"
    # references (which are filenames) line up.
    raw = {}
    for gen in gens:
        names = list_species(gen)
        print(f"generation {gen}: {len(names)} species")
        for name in names:
            try:
                raw[name] = fetch_json(RAW.format(gen=gen) + "/" + name + ".json")
            except Exception as e:
                print(f"  ! skip {name}: {e}")

    evo_targets = set()
    for data in raw.values():
        for evo in parse_evolutions(data):
            evo_targets.add(evo["to"])

    written = skipped = 0
    for sid, data in raw.items():
        if sid in curated or "baseStats" not in data:
            skipped += 1
            continue  # keep hand-curated files; skip form stubs without stats
        out = convert(sid, data, evo_targets)
        with open(os.path.join(SPECIES_DIR, sid + ".json"), "w") as f:
            json.dump(out, f, indent=2)
            f.write("\n")
        written += 1

    # rebuild the bundled index from every species file on disk, dex-ordered
    all_files = [f[:-5] for f in os.listdir(SPECIES_DIR)
                 if f.endswith(".json") and not f.startswith("_")]
    dex_of = {}
    for sid in all_files:
        with open(os.path.join(SPECIES_DIR, sid + ".json")) as f:
            dex_of[sid] = json.load(f).get("dex", 9999)
    index = sorted(all_files, key=lambda s: dex_of[s])
    with open(os.path.join(SPECIES_DIR, "_index.json"), "w") as f:
        json.dump(index, f, indent=2)
        f.write("\n")

    # prune evolutions whose target isn't installed (e.g. Gen 2 -> Gen 4 lines)
    present = set(all_files)
    pruned = 0
    for sid in all_files:
        if sid in curated:
            continue  # never touch hand-curated files
        path = os.path.join(SPECIES_DIR, sid + ".json")
        with open(path) as f:
            data = json.load(f)
        evos = data.get("evolutions")
        if not evos:
            continue
        kept = [e for e in evos if e["to"] in present]
        if len(kept) != len(evos):
            pruned += len(evos) - len(kept)
            data["evolutions"] = kept or None
            with open(path, "w") as f:
                json.dump(data, f, indent=2)
                f.write("\n")

    print(f"OK: wrote {written} new species, pruned {pruned} cross-gen "
          f"evolutions, index now has {len(index)} total")


if __name__ == "__main__":
    main()
