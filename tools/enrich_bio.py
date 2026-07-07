#!/usr/bin/env python3
"""Overlay EV yield, gender ratio and abilities onto the PokeCraft dex.

Reads each Cobblemon species JSON and copies three biology fields onto the
matching PokeCraft species file (only those fields are touched):

  evYield      -> {hp,atk,def,spa,spd,spe}  (effort values a defeated mon gives)
  maleRatio    -> float 0..1, or -1 for genderless (Cobblemon's convention)
  abilities    -> normal ability ids (list)
  hiddenAbility-> the "h:" ability id, or null

Usage:
    python3 tools/enrich_bio.py 1 2 3 4 5 6 7 8 9
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

STAT_MAP = {"hp": "hp", "attack": "atk", "defence": "def",
            "special_attack": "spa", "special_defence": "spd", "speed": "spe"}


def fetch_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "pokecraft-import"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def list_species(gen):
    tree = fetch_json(API.format(gen=gen))
    return sorted(x["name"][:-5] for x in tree if x["name"].endswith(".json"))


def ev_yield(data):
    out = {"hp": 0, "atk": 0, "def": 0, "spa": 0, "spd": 0, "spe": 0}
    for k, v in (data.get("evYield") or {}).items():
        if k in STAT_MAP:
            out[STAT_MAP[k]] = int(v)
    return out


def abilities(data):
    normal, hidden = [], None
    for a in data.get("abilities") or []:
        a = str(a)
        if a.startswith("h:"):
            hidden = a[2:]
        else:
            normal.append(a)
    return normal, hidden


def main():
    gens = [a for a in sys.argv[1:] if a.isdigit()] or \
        ["1", "2", "3", "4", "5", "6", "7", "8", "9"]

    on_disk = {f[:-5] for f in os.listdir(SPECIES_DIR)
               if f.endswith(".json") and not f.startswith("_")}

    updated = missing = 0
    for gen in gens:
        try:
            names = list_species(gen)
        except Exception as e:
            print(f"  ! gen {gen} listing failed: {e}")
            continue
        print(f"generation {gen}: {len(names)} species")
        for name in names:
            if name not in on_disk:
                continue
            try:
                cob = fetch_json(RAW.format(gen=gen) + "/" + name + ".json")
            except Exception as e:
                print(f"  ! skip {name}: {e}")
                missing += 1
                continue
            normal, hidden = abilities(cob)
            path = os.path.join(SPECIES_DIR, name + ".json")
            data = json.load(open(path))
            data["evYield"] = ev_yield(cob)
            data["maleRatio"] = cob.get("maleRatio", 0.5)
            data["abilities"] = normal
            data["hiddenAbility"] = hidden
            with open(path, "w") as f:
                json.dump(data, f, indent=2)
                f.write("\n")
            updated += 1

    print(f"OK: enriched {updated} species ({missing} download misses)")


if __name__ == "__main__":
    main()
