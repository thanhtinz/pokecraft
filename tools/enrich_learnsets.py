#!/usr/bin/env python3
"""Overlay authentic per-species level-up learnsets onto the PokeCraft dex.

Cobblemon species JSON carries each pokemon's real level-up moveset in a
"moves" array of "level:compactname" strings (e.g. "12:razorleaf"). This
script downloads those files, matches each compact move name to PokeCraft's
move pool (moves.json, built by build_moves.py) and rewrites only the
"learnset" field of every species file already on disk - stats, types,
evolutions and spawns are left untouched.

Only level-up moves are used (egg / tm / tutor entries are ignored) so the
in-game "last four moves learnable at this level" logic stays authentic. A
damaging fallback (tackle) is injected at level 1 for any species that would
otherwise have no attacking move in the pool.

Usage:
    python3 tools/enrich_learnsets.py 1 2 3 4 5 6 7 8 9
"""
import json
import os
import sys
import urllib.request

RAW = ("https://gitlab.com/cable-mc/cobblemon/-/raw/main/"
       "common/src/main/resources/data/cobblemon/species/generation{gen}")
API = ("https://gitlab.com/api/v4/projects/cable-mc%2Fcobblemon/repository/tree"
       "?path=common/src/main/resources/data/cobblemon/species/generation{gen}"
       "&per_page=500&ref=main")

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
SPECIES_DIR = os.path.join(ROOT, "species")
MOVES_FILE = os.path.join(ROOT, "moves", "moves.json")

FALLBACK = "tackle"


def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "pokecraft-import"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def list_species(gen):
    tree = fetch_json(API.format(gen=gen))
    return sorted(x["name"][:-5] for x in tree if x["name"].endswith(".json"))


def build_lookup(moves):
    """compact key (no separators) -> underscored move id."""
    lookup = {}
    for mid, data in moves.items():
        compact = mid.replace("_", "")
        lookup[compact] = mid
        # also index by the display name compacted, as a fallback
        lookup.setdefault(data["name"].lower().replace(" ", "").replace("-", ""), mid)
    return lookup


def parse_learnset(cob_moves, lookup, moves):
    """Cobblemon moves array -> {level(str): [move ids]} using level-up moves."""
    by_level = {}
    order = []
    for entry in cob_moves or []:
        if ":" not in entry:
            continue
        prefix, name = entry.split(":", 1)
        if not prefix.isdigit():
            continue  # skip egg / tm / tutor / form moves
        level = int(prefix)
        mid = lookup.get(name.replace("-", "").replace("_", ""))
        if not mid:
            continue
        key = str(max(1, level))
        by_level.setdefault(key, [])
        if mid not in by_level[key]:
            by_level[key].append(mid)
            order.append(mid)

    # guarantee at least one damaging move somewhere in the set
    has_damage = any(moves.get(m, {}).get("power", 0) > 0 for m in order)
    if not has_damage:
        by_level.setdefault("1", [])
        if FALLBACK not in by_level["1"]:
            by_level["1"].insert(0, FALLBACK)

    # sort by numeric level for stable, readable output
    return {k: by_level[k] for k in sorted(by_level, key=lambda x: int(x))}


def main():
    gens = [a for a in sys.argv[1:] if a.isdigit()] or \
        ["1", "2", "3", "4", "5", "6", "7", "8", "9"]

    moves = json.load(open(MOVES_FILE))
    lookup = build_lookup(moves)

    on_disk = {f[:-5] for f in os.listdir(SPECIES_DIR)
               if f.endswith(".json") and not f.startswith("_")}

    updated = missing = empty = 0
    for gen in gens:
        try:
            names = list_species(gen)
        except Exception as e:
            print(f"  ! gen {gen} listing failed: {e}")
            continue
        print(f"generation {gen}: {len(names)} species")
        for name in names:
            if name not in on_disk:
                continue  # form/stub not in our dex
            try:
                cob = fetch_json(RAW.format(gen=gen) + "/" + name + ".json")
            except Exception as e:
                print(f"  ! skip {name}: {e}")
                missing += 1
                continue
            learnset = parse_learnset(cob.get("moves"), lookup, moves)
            if not learnset:
                empty += 1
                learnset = {"1": [FALLBACK]}
            path = os.path.join(SPECIES_DIR, name + ".json")
            data = json.load(open(path))
            data["learnset"] = learnset
            with open(path, "w") as f:
                json.dump(data, f, indent=2)
                f.write("\n")
            updated += 1

    print(f"OK: updated {updated} learnsets "
          f"({missing} download misses, {empty} empty->fallback)")


if __name__ == "__main__":
    main()
