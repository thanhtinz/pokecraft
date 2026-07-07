#!/usr/bin/env python3
"""Build an authentic moves.json from the PokeAPI CSV data dump.

Source CSVs live at
https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv/ .
Download moves.csv, types.csv, move_damage_classes.csv, move_meta.csv,
move_meta_ailments.csv, move_meta_stat_changes.csv into --csv-dir first
(the importer expects them there), then run this to (re)generate
src/main/resources/moves/moves.json.

Move ids use underscores (razor_leaf) to match the existing species
learnset references; Cobblemon's compact names (razorleaf) are matched by
stripping separators, see import_cobblemon.py.
"""
import argparse
import csv
import json
import os

# PokeAPI type_id -> PokeCraft PokemonType enum name (only the 18 real types)
TYPE_BY_ID = {
    1: "NORMAL", 2: "FIGHTING", 3: "FLYING", 4: "POISON", 5: "GROUND",
    6: "ROCK", 7: "BUG", 8: "GHOST", 9: "STEEL", 10: "FIRE", 11: "WATER",
    12: "GRASS", 13: "ELECTRIC", 14: "PSYCHIC", 15: "ICE", 16: "DRAGON",
    17: "DARK", 18: "FAIRY",
}

# damage_class_id -> category
CATEGORY_BY_ID = {1: "STATUS", 2: "PHYSICAL", 3: "SPECIAL"}

# PokeAPI ailment identifier -> PokeCraft StatusCondition enum name
STATUS_BY_AILMENT = {
    "paralysis": "PARALYSIS",
    "sleep": "SLEEP",
    "freeze": "FREEZE",
    "burn": "BURN",
    "poison": "POISON",
}

# PokeAPI stat_id -> PokeCraft Effect.stat index
# 1=atk 2=def 3=spa 4=spd 5=spe 6=accuracy 7=evasion
STAT_INDEX = {2: 1, 3: 2, 4: 3, 5: 4, 6: 5, 7: 6, 8: 7}


def read_csv(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def to_int(s, default=0):
    s = (s or "").strip()
    if not s:
        return default
    try:
        return int(s)
    except ValueError:
        return default


def title(identifier):
    return " ".join(w.capitalize() for w in identifier.split("-"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv-dir", required=True)
    ap.add_argument("--out", default="src/main/resources/moves/moves.json")
    args = ap.parse_args()

    moves = read_csv(os.path.join(args.csv_dir, "moves.csv"))
    meta = {to_int(r["move_id"]): r
            for r in read_csv(os.path.join(args.csv_dir, "move_meta.csv"))}
    ailments = {to_int(r["id"]): r["identifier"]
                for r in read_csv(os.path.join(args.csv_dir, "move_meta_ailments.csv"))}
    stat_changes = {}
    for r in read_csv(os.path.join(args.csv_dir, "move_meta_stat_changes.csv")):
        stat_changes.setdefault(to_int(r["move_id"]), []).append(
            (to_int(r["stat_id"]), to_int(r["change"])))

    out = {}
    skipped = 0
    for row in moves:
        mid = to_int(row["id"])
        type_id = to_int(row["type_id"])
        if type_id not in TYPE_BY_ID:
            skipped += 1
            continue  # stellar / shadow / typeless moves we can't represent
        ident = row["identifier"]
        key = ident.replace("-", "_")
        category = CATEGORY_BY_ID.get(to_int(row["damage_class_id"]), "STATUS")
        accuracy = to_int(row["accuracy"], 100)  # blank = never-miss -> 100
        pp = to_int(row["pp"], 5) or 5
        entry = {
            "name": title(ident),
            "type": TYPE_BY_ID[type_id],
            "category": category,
            "power": to_int(row["power"], 0),
            "accuracy": accuracy,
            "pp": pp,
        }
        priority = to_int(row["priority"], 0)
        if priority != 0:
            entry["priority"] = priority

        # Secondary effect: a status ailment and/or a single stat change.
        effect = {}
        m = meta.get(mid)
        if m:
            ail = ailments.get(to_int(m["meta_ailment_id"]), "none")
            if ail in STATUS_BY_AILMENT:
                chance = to_int(m["ailment_chance"], 0)
                if chance <= 0:
                    chance = 100 if category == "STATUS" else 0
                if chance > 0:
                    effect["status"] = STATUS_BY_AILMENT[ail]
                    effect["statusChance"] = chance
        changes = stat_changes.get(mid, [])
        for stat_id, change in changes:
            idx = STAT_INDEX.get(stat_id)
            if idx and change != 0:
                effect["stat"] = idx
                effect["stages"] = change
                # +N to the user, -N to the opponent
                effect["target"] = "SELF" if change > 0 else "TARGET"
                break
        if effect:
            entry["effect"] = effect
        out[key] = entry

    out = dict(sorted(out.items()))
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as f:
        json.dump(out, f, indent=1)
        f.write("\n")
    print(f"wrote {len(out)} moves to {args.out} (skipped {skipped} typeless)")


if __name__ == "__main__":
    main()
