#!/usr/bin/env python3
"""Generates the Gen 1 (151) species dataset for PokeCraft.

Run from the repo root:  python3 tools/generate_gen1.py

- Writes src/main/resources/species/<id>.json for every species that does
  not already have a hand-curated file (the starter/pidgey/pikachu lines).
- Writes src/main/resources/species/_index.json (all bundled ids) which
  SpeciesRegistry uses to extract bundled files at runtime.
- Validates that every referenced move exists in moves.json and every
  evolution target exists.

Learnsets are simplified: generated from per-type move pools at fixed level
slots. Base stats use modern (gen 6+) values; trade evolutions become level
36; stone evolutions use the in-game stones.
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
SPECIES_DIR = os.path.join(ROOT, "species")
MOVES_FILE = os.path.join(ROOT, "moves", "moves.json")

# Hand-curated files that must not be overwritten.
CURATED = {
    "bulbasaur", "ivysaur", "venusaur", "charmander", "charmeleon", "charizard",
    "squirtle", "wartortle", "blastoise", "pidgey", "pidgeotto", "pidgeot",
    "pikachu", "raichu",
}

# dex, id, name, types, (hp atk def spa spd spe), catch, evolution spec, legendary
# evolution spec: "16>ivysaur" = level | "@fire_stone>arcanine" = stone
#                 multiple separated by "|" (eevee)
TABLE = [
    (1, "bulbasaur", "Bulbasaur", "GRASS/POISON", (45, 49, 49, 65, 65, 45), 45, "16>ivysaur", False),
    (2, "ivysaur", "Ivysaur", "GRASS/POISON", (60, 62, 63, 80, 80, 60), 45, "32>venusaur", False),
    (3, "venusaur", "Venusaur", "GRASS/POISON", (80, 82, 83, 100, 100, 80), 45, "", False),
    (4, "charmander", "Charmander", "FIRE", (39, 52, 43, 60, 50, 65), 45, "16>charmeleon", False),
    (5, "charmeleon", "Charmeleon", "FIRE", (58, 64, 58, 80, 65, 80), 45, "36>charizard", False),
    (6, "charizard", "Charizard", "FIRE/FLYING", (78, 84, 78, 109, 85, 100), 45, "", False),
    (7, "squirtle", "Squirtle", "WATER", (44, 48, 65, 50, 64, 43), 45, "16>wartortle", False),
    (8, "wartortle", "Wartortle", "WATER", (59, 63, 80, 65, 80, 58), 45, "36>blastoise", False),
    (9, "blastoise", "Blastoise", "WATER", (79, 83, 100, 85, 105, 78), 45, "", False),
    (10, "caterpie", "Caterpie", "BUG", (45, 30, 35, 20, 20, 45), 255, "7>metapod", False),
    (11, "metapod", "Metapod", "BUG", (50, 20, 55, 25, 25, 30), 120, "10>butterfree", False),
    (12, "butterfree", "Butterfree", "BUG/FLYING", (60, 45, 50, 90, 80, 70), 45, "", False),
    (13, "weedle", "Weedle", "BUG/POISON", (40, 35, 30, 20, 20, 50), 255, "7>kakuna", False),
    (14, "kakuna", "Kakuna", "BUG/POISON", (45, 25, 50, 25, 25, 35), 120, "10>beedrill", False),
    (15, "beedrill", "Beedrill", "BUG/POISON", (65, 90, 40, 45, 80, 75), 45, "", False),
    (16, "pidgey", "Pidgey", "NORMAL/FLYING", (40, 45, 40, 35, 35, 56), 255, "18>pidgeotto", False),
    (17, "pidgeotto", "Pidgeotto", "NORMAL/FLYING", (63, 60, 55, 50, 50, 71), 120, "36>pidgeot", False),
    (18, "pidgeot", "Pidgeot", "NORMAL/FLYING", (83, 80, 75, 70, 70, 101), 45, "", False),
    (19, "rattata", "Rattata", "NORMAL", (30, 56, 35, 25, 35, 72), 255, "20>raticate", False),
    (20, "raticate", "Raticate", "NORMAL", (55, 81, 60, 50, 70, 97), 127, "", False),
    (21, "spearow", "Spearow", "NORMAL/FLYING", (40, 60, 30, 31, 31, 70), 255, "20>fearow", False),
    (22, "fearow", "Fearow", "NORMAL/FLYING", (65, 90, 65, 61, 61, 100), 90, "", False),
    (23, "ekans", "Ekans", "POISON", (35, 60, 44, 40, 54, 55), 255, "22>arbok", False),
    (24, "arbok", "Arbok", "POISON", (60, 95, 69, 65, 79, 80), 90, "", False),
    (25, "pikachu", "Pikachu", "ELECTRIC", (35, 55, 40, 50, 50, 90), 190, "@thunder_stone>raichu", False),
    (26, "raichu", "Raichu", "ELECTRIC", (60, 90, 55, 90, 80, 110), 75, "", False),
    (27, "sandshrew", "Sandshrew", "GROUND", (50, 75, 85, 20, 30, 40), 255, "22>sandslash", False),
    (28, "sandslash", "Sandslash", "GROUND", (75, 100, 110, 45, 55, 65), 90, "", False),
    (29, "nidoran_f", "Nidoran F", "POISON", (55, 47, 52, 40, 40, 41), 235, "16>nidorina", False),
    (30, "nidorina", "Nidorina", "POISON", (70, 62, 67, 55, 55, 56), 120, "@moon_stone>nidoqueen", False),
    (31, "nidoqueen", "Nidoqueen", "POISON/GROUND", (90, 92, 87, 75, 85, 76), 45, "", False),
    (32, "nidoran_m", "Nidoran M", "POISON", (46, 57, 40, 40, 40, 50), 235, "16>nidorino", False),
    (33, "nidorino", "Nidorino", "POISON", (61, 72, 57, 55, 55, 65), 120, "@moon_stone>nidoking", False),
    (34, "nidoking", "Nidoking", "POISON/GROUND", (81, 102, 77, 85, 75, 85), 45, "", False),
    (35, "clefairy", "Clefairy", "FAIRY", (70, 45, 48, 60, 65, 35), 150, "@moon_stone>clefable", False),
    (36, "clefable", "Clefable", "FAIRY", (95, 70, 73, 95, 90, 60), 25, "", False),
    (37, "vulpix", "Vulpix", "FIRE", (38, 41, 40, 50, 65, 65), 190, "@fire_stone>ninetales", False),
    (38, "ninetales", "Ninetales", "FIRE", (73, 76, 75, 81, 100, 100), 75, "", False),
    (39, "jigglypuff", "Jigglypuff", "NORMAL/FAIRY", (115, 45, 20, 45, 25, 20), 170, "@moon_stone>wigglytuff", False),
    (40, "wigglytuff", "Wigglytuff", "NORMAL/FAIRY", (140, 70, 45, 85, 50, 45), 50, "", False),
    (41, "zubat", "Zubat", "POISON/FLYING", (40, 45, 35, 30, 40, 55), 255, "22>golbat", False),
    (42, "golbat", "Golbat", "POISON/FLYING", (75, 80, 70, 65, 75, 90), 90, "", False),
    (43, "oddish", "Oddish", "GRASS/POISON", (45, 50, 55, 75, 65, 30), 255, "21>gloom", False),
    (44, "gloom", "Gloom", "GRASS/POISON", (60, 65, 70, 85, 75, 40), 120, "@leaf_stone>vileplume", False),
    (45, "vileplume", "Vileplume", "GRASS/POISON", (75, 80, 85, 110, 90, 50), 45, "", False),
    (46, "paras", "Paras", "BUG/GRASS", (35, 70, 55, 45, 55, 25), 190, "24>parasect", False),
    (47, "parasect", "Parasect", "BUG/GRASS", (60, 95, 80, 60, 80, 30), 75, "", False),
    (48, "venonat", "Venonat", "BUG/POISON", (60, 55, 50, 40, 55, 45), 190, "31>venomoth", False),
    (49, "venomoth", "Venomoth", "BUG/POISON", (70, 65, 60, 90, 75, 90), 75, "", False),
    (50, "diglett", "Diglett", "GROUND", (10, 55, 25, 35, 45, 95), 255, "26>dugtrio", False),
    (51, "dugtrio", "Dugtrio", "GROUND", (35, 100, 50, 50, 70, 120), 50, "", False),
    (52, "meowth", "Meowth", "NORMAL", (40, 45, 35, 40, 40, 90), 255, "28>persian", False),
    (53, "persian", "Persian", "NORMAL", (65, 70, 60, 65, 65, 115), 90, "", False),
    (54, "psyduck", "Psyduck", "WATER", (50, 52, 48, 65, 50, 55), 190, "33>golduck", False),
    (55, "golduck", "Golduck", "WATER", (80, 82, 78, 95, 80, 85), 75, "", False),
    (56, "mankey", "Mankey", "FIGHTING", (40, 80, 35, 35, 45, 70), 190, "28>primeape", False),
    (57, "primeape", "Primeape", "FIGHTING", (65, 105, 60, 60, 70, 95), 75, "", False),
    (58, "growlithe", "Growlithe", "FIRE", (55, 70, 45, 70, 50, 60), 190, "@fire_stone>arcanine", False),
    (59, "arcanine", "Arcanine", "FIRE", (90, 110, 80, 100, 80, 95), 75, "", False),
    (60, "poliwag", "Poliwag", "WATER", (40, 50, 40, 40, 40, 90), 255, "25>poliwhirl", False),
    (61, "poliwhirl", "Poliwhirl", "WATER", (65, 65, 65, 50, 50, 90), 120, "@water_stone>poliwrath", False),
    (62, "poliwrath", "Poliwrath", "WATER/FIGHTING", (90, 95, 95, 70, 90, 70), 45, "", False),
    (63, "abra", "Abra", "PSYCHIC", (25, 20, 15, 105, 55, 90), 200, "16>kadabra", False),
    (64, "kadabra", "Kadabra", "PSYCHIC", (40, 35, 30, 120, 70, 105), 100, "36>alakazam", False),
    (65, "alakazam", "Alakazam", "PSYCHIC", (55, 50, 45, 135, 95, 120), 50, "", False),
    (66, "machop", "Machop", "FIGHTING", (70, 80, 50, 35, 35, 35), 180, "28>machoke", False),
    (67, "machoke", "Machoke", "FIGHTING", (80, 100, 70, 50, 60, 45), 90, "36>machamp", False),
    (68, "machamp", "Machamp", "FIGHTING", (90, 130, 80, 65, 85, 55), 45, "", False),
    (69, "bellsprout", "Bellsprout", "GRASS/POISON", (50, 75, 35, 70, 30, 40), 255, "21>weepinbell", False),
    (70, "weepinbell", "Weepinbell", "GRASS/POISON", (65, 90, 50, 85, 45, 55), 120, "@leaf_stone>victreebel", False),
    (71, "victreebel", "Victreebel", "GRASS/POISON", (80, 105, 65, 100, 70, 70), 45, "", False),
    (72, "tentacool", "Tentacool", "WATER/POISON", (40, 40, 35, 50, 100, 70), 190, "30>tentacruel", False),
    (73, "tentacruel", "Tentacruel", "WATER/POISON", (80, 70, 65, 80, 120, 100), 60, "", False),
    (74, "geodude", "Geodude", "ROCK/GROUND", (40, 80, 100, 30, 30, 20), 255, "25>graveler", False),
    (75, "graveler", "Graveler", "ROCK/GROUND", (55, 95, 115, 45, 45, 35), 120, "36>golem", False),
    (76, "golem", "Golem", "ROCK/GROUND", (80, 120, 130, 55, 65, 45), 45, "", False),
    (77, "ponyta", "Ponyta", "FIRE", (50, 85, 55, 65, 65, 90), 190, "40>rapidash", False),
    (78, "rapidash", "Rapidash", "FIRE", (65, 100, 70, 80, 80, 105), 60, "", False),
    (79, "slowpoke", "Slowpoke", "WATER/PSYCHIC", (90, 65, 65, 40, 40, 15), 190, "37>slowbro", False),
    (80, "slowbro", "Slowbro", "WATER/PSYCHIC", (95, 75, 110, 100, 80, 30), 75, "", False),
    (81, "magnemite", "Magnemite", "ELECTRIC/STEEL", (25, 35, 70, 95, 55, 45), 190, "30>magneton", False),
    (82, "magneton", "Magneton", "ELECTRIC/STEEL", (50, 60, 95, 120, 70, 70), 60, "", False),
    (83, "farfetchd", "Farfetch'd", "NORMAL/FLYING", (52, 90, 55, 58, 62, 60), 45, "", False),
    (84, "doduo", "Doduo", "NORMAL/FLYING", (35, 85, 45, 35, 35, 75), 190, "31>dodrio", False),
    (85, "dodrio", "Dodrio", "NORMAL/FLYING", (60, 110, 70, 60, 60, 110), 45, "", False),
    (86, "seel", "Seel", "WATER", (65, 45, 55, 45, 70, 45), 190, "34>dewgong", False),
    (87, "dewgong", "Dewgong", "WATER/ICE", (90, 70, 80, 70, 95, 70), 75, "", False),
    (88, "grimer", "Grimer", "POISON", (80, 80, 50, 40, 50, 25), 190, "38>muk", False),
    (89, "muk", "Muk", "POISON", (105, 105, 75, 65, 100, 50), 75, "", False),
    (90, "shellder", "Shellder", "WATER", (30, 65, 100, 45, 25, 40), 190, "@water_stone>cloyster", False),
    (91, "cloyster", "Cloyster", "WATER/ICE", (50, 95, 180, 85, 45, 70), 60, "", False),
    (92, "gastly", "Gastly", "GHOST/POISON", (30, 35, 30, 100, 35, 80), 190, "25>haunter", False),
    (93, "haunter", "Haunter", "GHOST/POISON", (45, 50, 45, 115, 55, 95), 90, "36>gengar", False),
    (94, "gengar", "Gengar", "GHOST/POISON", (60, 65, 60, 130, 75, 110), 45, "", False),
    (95, "onix", "Onix", "ROCK/GROUND", (35, 45, 160, 30, 45, 70), 45, "", False),
    (96, "drowzee", "Drowzee", "PSYCHIC", (60, 48, 45, 43, 90, 42), 190, "26>hypno", False),
    (97, "hypno", "Hypno", "PSYCHIC", (85, 73, 70, 73, 115, 67), 75, "", False),
    (98, "krabby", "Krabby", "WATER", (30, 105, 90, 25, 25, 50), 225, "28>kingler", False),
    (99, "kingler", "Kingler", "WATER", (55, 130, 115, 50, 50, 75), 60, "", False),
    (100, "voltorb", "Voltorb", "ELECTRIC", (40, 30, 50, 55, 55, 100), 190, "30>electrode", False),
    (101, "electrode", "Electrode", "ELECTRIC", (60, 50, 70, 80, 80, 150), 60, "", False),
    (102, "exeggcute", "Exeggcute", "GRASS/PSYCHIC", (60, 40, 80, 60, 45, 40), 90, "@leaf_stone>exeggutor", False),
    (103, "exeggutor", "Exeggutor", "GRASS/PSYCHIC", (95, 95, 85, 125, 75, 55), 45, "", False),
    (104, "cubone", "Cubone", "GROUND", (50, 50, 95, 40, 50, 35), 190, "28>marowak", False),
    (105, "marowak", "Marowak", "GROUND", (60, 80, 110, 50, 80, 45), 75, "", False),
    (106, "hitmonlee", "Hitmonlee", "FIGHTING", (50, 120, 53, 35, 110, 87), 45, "", False),
    (107, "hitmonchan", "Hitmonchan", "FIGHTING", (50, 105, 79, 35, 110, 76), 45, "", False),
    (108, "lickitung", "Lickitung", "NORMAL", (90, 55, 75, 60, 75, 30), 45, "", False),
    (109, "koffing", "Koffing", "POISON", (40, 65, 95, 60, 45, 35), 190, "35>weezing", False),
    (110, "weezing", "Weezing", "POISON", (65, 90, 120, 85, 70, 60), 60, "", False),
    (111, "rhyhorn", "Rhyhorn", "GROUND/ROCK", (80, 85, 95, 30, 30, 25), 120, "42>rhydon", False),
    (112, "rhydon", "Rhydon", "GROUND/ROCK", (105, 130, 120, 45, 45, 40), 60, "", False),
    (113, "chansey", "Chansey", "NORMAL", (250, 5, 5, 35, 105, 50), 30, "", False),
    (114, "tangela", "Tangela", "GRASS", (65, 55, 115, 100, 40, 60), 45, "", False),
    (115, "kangaskhan", "Kangaskhan", "NORMAL", (105, 95, 80, 40, 80, 90), 45, "", False),
    (116, "horsea", "Horsea", "WATER", (30, 40, 70, 70, 25, 60), 225, "32>seadra", False),
    (117, "seadra", "Seadra", "WATER", (55, 65, 95, 95, 45, 85), 75, "", False),
    (118, "goldeen", "Goldeen", "WATER", (45, 67, 60, 35, 50, 63), 225, "33>seaking", False),
    (119, "seaking", "Seaking", "WATER", (80, 92, 65, 65, 80, 68), 60, "", False),
    (120, "staryu", "Staryu", "WATER", (30, 45, 55, 70, 55, 85), 225, "@water_stone>starmie", False),
    (121, "starmie", "Starmie", "WATER/PSYCHIC", (60, 75, 85, 100, 85, 115), 60, "", False),
    (122, "mr_mime", "Mr. Mime", "PSYCHIC/FAIRY", (40, 45, 65, 100, 120, 90), 45, "", False),
    (123, "scyther", "Scyther", "BUG/FLYING", (70, 110, 80, 55, 80, 105), 45, "", False),
    (124, "jynx", "Jynx", "ICE/PSYCHIC", (65, 50, 35, 115, 95, 95), 45, "", False),
    (125, "electabuzz", "Electabuzz", "ELECTRIC", (65, 83, 57, 95, 85, 105), 45, "", False),
    (126, "magmar", "Magmar", "FIRE", (65, 95, 57, 100, 85, 93), 45, "", False),
    (127, "pinsir", "Pinsir", "BUG", (65, 125, 100, 55, 70, 85), 45, "", False),
    (128, "tauros", "Tauros", "NORMAL", (75, 100, 95, 40, 70, 110), 45, "", False),
    (129, "magikarp", "Magikarp", "WATER", (20, 10, 55, 15, 20, 80), 255, "20>gyarados", False),
    (130, "gyarados", "Gyarados", "WATER/FLYING", (95, 125, 79, 60, 100, 81), 45, "", False),
    (131, "lapras", "Lapras", "WATER/ICE", (130, 85, 80, 85, 95, 60), 45, "", False),
    (132, "ditto", "Ditto", "NORMAL", (48, 48, 48, 48, 48, 48), 35, "", False),
    (133, "eevee", "Eevee", "NORMAL", (55, 55, 50, 45, 65, 55), 45,
     "@water_stone>vaporeon|@thunder_stone>jolteon|@fire_stone>flareon", False),
    (134, "vaporeon", "Vaporeon", "WATER", (130, 65, 60, 110, 95, 65), 45, "", False),
    (135, "jolteon", "Jolteon", "ELECTRIC", (65, 65, 60, 110, 95, 130), 45, "", False),
    (136, "flareon", "Flareon", "FIRE", (65, 130, 60, 95, 110, 65), 45, "", False),
    (137, "porygon", "Porygon", "NORMAL", (65, 60, 70, 85, 75, 40), 45, "", False),
    (138, "omanyte", "Omanyte", "ROCK/WATER", (35, 40, 100, 90, 55, 35), 45, "40>omastar", False),
    (139, "omastar", "Omastar", "ROCK/WATER", (70, 60, 125, 115, 70, 55), 45, "", False),
    (140, "kabuto", "Kabuto", "ROCK/WATER", (30, 80, 90, 55, 45, 55), 45, "40>kabutops", False),
    (141, "kabutops", "Kabutops", "ROCK/WATER", (60, 115, 105, 65, 70, 80), 45, "", False),
    (142, "aerodactyl", "Aerodactyl", "ROCK/FLYING", (80, 105, 65, 60, 75, 130), 45, "", False),
    (143, "snorlax", "Snorlax", "NORMAL", (160, 110, 65, 65, 110, 30), 25, "", False),
    (144, "articuno", "Articuno", "ICE/FLYING", (90, 85, 100, 95, 125, 85), 3, "", True),
    (145, "zapdos", "Zapdos", "ELECTRIC/FLYING", (90, 90, 85, 125, 90, 100), 3, "", True),
    (146, "moltres", "Moltres", "FIRE/FLYING", (90, 100, 90, 125, 85, 90), 3, "", True),
    (147, "dratini", "Dratini", "DRAGON", (41, 64, 45, 50, 50, 50), 45, "30>dragonair", False),
    (148, "dragonair", "Dragonair", "DRAGON", (61, 84, 65, 70, 70, 70), 45, "55>dragonite", False),
    (149, "dragonite", "Dragonite", "DRAGON/FLYING", (91, 134, 95, 100, 100, 80), 45, "", False),
    (150, "mewtwo", "Mewtwo", "PSYCHIC", (106, 110, 90, 154, 90, 130), 3, "", True),
    (151, "mew", "Mew", "PSYCHIC", (100, 100, 100, 100, 100, 100), 45, "", True),
]

# Attack move pools per type, weakest -> strongest.
TYPE_POOLS = {
    "NORMAL": ["quick_attack", "hyper_fang", "mega_punch", "body_slam", "double_edge", "hyper_beam"],
    "GRASS": ["vine_whip", "razor_leaf", "seed_bomb", "petal_dance", "solar_beam"],
    "FIRE": ["ember", "fire_spin", "fire_punch", "flamethrower", "fire_blast"],
    "WATER": ["water_gun", "bubble_beam", "waterfall", "surf", "hydro_pump"],
    "ELECTRIC": ["thunder_shock", "spark", "thunderbolt", "thunder"],
    "ICE": ["ice_shard", "aurora_beam", "ice_beam", "blizzard"],
    "FIGHTING": ["karate_chop", "low_kick", "brick_break", "cross_chop"],
    "POISON": ["poison_sting", "acid", "sludge", "sludge_bomb"],
    "GROUND": ["sand_tomb", "dig", "earthquake"],
    "FLYING": ["peck", "gust", "wing_attack", "drill_peck"],
    "PSYCHIC": ["confusion", "psybeam", "psychic"],
    "BUG": ["bug_bite", "pin_missile"],
    "ROCK": ["rock_throw", "rock_slide", "stone_edge"],
    "GHOST": ["lick", "night_shade", "shadow_ball"],
    "DRAGON": ["twister", "dragon_breath", "dragon_claw", "outrage"],
    "DARK": ["bite", "crunch"],
    "STEEL": ["steel_wing", "iron_head"],
    "FAIRY": ["fairy_wind", "dazzling_gleam"],
}

STATUS_BY_TYPE = {
    "GRASS": "sleep_powder", "ELECTRIC": "thunder_wave", "PSYCHIC": "hypnosis",
    "POISON": "poison_powder", "FAIRY": "sing", "BUG": "string_shot",
    "GHOST": "hypnosis", "ICE": "leer", "WATER": "tail_whip", "FIRE": "leer",
    "NORMAL": "growl", "FIGHTING": "leer", "GROUND": "leer", "FLYING": "tail_whip",
    "ROCK": "harden", "DRAGON": "leer", "DARK": "leer", "STEEL": "harden",
}

BIOMES_BY_TYPE = {
    "NORMAL": ["PLAINS", "SUNFLOWER_PLAINS", "MEADOW", "SAVANNA"],
    "GRASS": ["FOREST", "FLOWER_FOREST", "BIRCH_FOREST", "JUNGLE"],
    "FIRE": ["DESERT", "BADLANDS", "SAVANNA"],
    "WATER": ["BEACH", "RIVER", "OCEAN", "SWAMP"],
    "ELECTRIC": ["FOREST", "DARK_FOREST", "BAMBOO_JUNGLE"],
    "ICE": ["SNOWY_PLAINS", "SNOWY_TAIGA", "ICE_SPIKES", "GROVE"],
    "FIGHTING": ["WINDSWEPT_HILLS", "WINDSWEPT_FOREST", "STONY_SHORE"],
    "POISON": ["SWAMP", "MANGROVE_SWAMP", "DARK_FOREST"],
    "GROUND": ["DESERT", "BADLANDS", "STONY_SHORE"],
    "FLYING": ["PLAINS", "FOREST", "WINDSWEPT_HILLS", "MEADOW"],
    "PSYCHIC": ["FLOWER_FOREST", "CHERRY_GROVE", "MUSHROOM_FIELDS"],
    "BUG": ["FOREST", "BIRCH_FOREST", "DARK_FOREST", "JUNGLE"],
    "ROCK": ["WINDSWEPT_HILLS", "STONY_PEAKS", "JAGGED_PEAKS", "STONY_SHORE"],
    "GHOST": ["DARK_FOREST", "PALE_GARDEN"],
    "DRAGON": ["JAGGED_PEAKS", "FROZEN_PEAKS", "STONY_PEAKS"],
    "DARK": ["DARK_FOREST", "PALE_GARDEN"],
    "STEEL": ["STONY_PEAKS", "WINDSWEPT_HILLS"],
    "FAIRY": ["FLOWER_FOREST", "CHERRY_GROVE", "MEADOW"],
}

BASIC_ATTACKS = ["tackle", "scratch", "pound"]
LEVEL_SLOTS = [8, 14, 20, 32, 40, 48]  # per-type attack slots
BOOST_SLOT = 26


def parse_evolutions(spec):
    out = []
    if not spec:
        return out
    for part in spec.split("|"):
        item = None
        level = 0
        cond, target = part.split(">")
        if cond.startswith("@"):
            item = cond[1:]
        else:
            level = int(cond)
        out.append({"level": level, "to": target, "item": item})
    return out


def boost_move(stats):
    hp, atk, dfn, spa, spd, spe = stats
    best = max((atk, "swords_dance"), (dfn, "harden"), (spa, "calm_mind"), (spe, "agility"))
    return best[1]


def learnset_for(dex, types, stats):
    learn = {}
    learn["1"] = [BASIC_ATTACKS[dex % len(BASIC_ATTACKS)]]
    status = STATUS_BY_TYPE.get(types[0], "growl")
    learn["4"] = [status]

    pools = [list(TYPE_POOLS.get(t, [])) for t in types]
    seen = set(learn["1"] + learn["4"])
    pool_index = [0] * len(pools)
    which = dex % max(1, len(pools))
    for slot in LEVEL_SLOTS:
        placed = False
        for attempt in range(len(pools)):
            i = (which + attempt) % len(pools)
            while pool_index[i] < len(pools[i]) and pools[i][pool_index[i]] in seen:
                pool_index[i] += 1
            if pool_index[i] < len(pools[i]):
                move = pools[i][pool_index[i]]
                pool_index[i] += 1
                learn[str(slot)] = [move]
                seen.add(move)
                placed = True
                break
        which += 1
        if not placed:
            break
    learn[str(BOOST_SLOT)] = [boost_move(stats)]
    return learn


def exp_yield(dex, stats, evolutions, is_target, legendary):
    bst = sum(stats)
    if legendary:
        factor = 0.5
    elif evolutions and not is_target:
        factor = 0.20   # base stage
    elif evolutions and is_target:
        factor = 0.28   # middle stage
    elif is_target:
        factor = 0.45   # final stage
    else:
        factor = 0.30   # single stage
    return max(30, round(bst * factor))


def spawn_for(types, catch, is_target, legendary):
    if legendary or is_target:
        return None
    biomes = BIOMES_BY_TYPE.get(types[0], ["PLAINS"])
    weight = max(2, min(20, catch // 12))
    if catch >= 150:
        min_l, max_l = 2, 10
    elif catch >= 75:
        min_l, max_l = 4, 14
    else:
        min_l, max_l = 8, 20
    return {"biomes": biomes, "weight": weight, "minLevel": min_l, "maxLevel": max_l}


def main():
    with open(MOVES_FILE) as f:
        moves = set(json.load(f).keys())
    all_ids = {row[1] for row in TABLE}
    targets = set()
    for row in TABLE:
        for evo in parse_evolutions(row[6]):
            targets.add(evo["to"])

    errors = []
    written = 0
    for dex, sid, name, type_spec, stats, catch, evo_spec, legendary in TABLE:
        types = type_spec.split("/")
        evolutions = parse_evolutions(evo_spec)
        for evo in evolutions:
            if evo["to"] not in all_ids:
                errors.append(f"{sid}: unknown evolution target {evo['to']}")
        if sid in CURATED:
            continue
        learnset = learnset_for(dex, types, stats)
        for level_moves in learnset.values():
            for m in level_moves:
                if m not in moves:
                    errors.append(f"{sid}: unknown move {m}")
        hp, atk, dfn, spa, spd, spe = stats
        data = {
            "id": sid,
            "dex": dex,
            "name": name,
            "types": types,
            "baseStats": {"hp": hp, "atk": atk, "def": dfn, "spa": spa, "spd": spd, "spe": spe},
            "catchRate": catch,
            "expYield": exp_yield(dex, stats, evolutions, sid in targets, legendary),
            "modelId": sid,
            "learnset": learnset,
            "evolution": None,
            "evolutions": evolutions if evolutions else None,
            "spawn": spawn_for(types, catch, sid in targets, legendary),
        }
        path = os.path.join(SPECIES_DIR, sid + ".json")
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
        written += 1

    index = sorted(all_ids, key=lambda s: next(r[0] for r in TABLE if r[1] == s))
    with open(os.path.join(SPECIES_DIR, "_index.json"), "w") as f:
        json.dump(index, f, indent=2)
        f.write("\n")

    if errors:
        print("VALIDATION ERRORS:")
        for e in errors:
            print("  -", e)
        sys.exit(1)
    print(f"OK: wrote {written} species files + _index.json ({len(index)} total ids)")


if __name__ == "__main__":
    main()
