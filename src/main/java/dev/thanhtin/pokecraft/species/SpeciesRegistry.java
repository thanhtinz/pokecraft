package dev.thanhtin.pokecraft.species;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.MoveData;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.*;

public class SpeciesRegistry {
    private final PokeCraftPlugin plugin;
    private final Gson gson = new Gson();
    private final Map<String, PokemonSpecies> species = new LinkedHashMap<>();
    private final Map<String, MoveData> moves = new LinkedHashMap<>();

    public SpeciesRegistry(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        species.clear();
        moves.clear();
        File speciesDir = new File(plugin.getDataFolder(), "species");
        File movesFile = new File(plugin.getDataFolder(), "moves/moves.json");

        for (String s : bundledSpecies()) {
            if (!new File(speciesDir, s + ".json").exists()) {
                plugin.saveResource("species/" + s + ".json", false);
            }
        }
        if (!movesFile.exists()) plugin.saveResource("moves/moves.json", false);

        try (Reader r = new FileReader(movesFile)) {
            Map<String, MoveData> loaded = gson.fromJson(r, new TypeToken<Map<String, MoveData>>() {}.getType());
            loaded.forEach((id, m) -> { m.id = id; moves.put(id, m); });
        } catch (Exception e) {
            plugin.getLogger().severe("[ERR] Failed to load moves.json: " + e.getMessage());
        }

        File[] files = speciesDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try (Reader r = new FileReader(f)) {
                    PokemonSpecies s = gson.fromJson(r, PokemonSpecies.class);
                    if (s != null && s.id != null) species.put(s.id, s);
                } catch (Exception e) {
                    plugin.getLogger().severe("[ERR] Failed to load species " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("[OK] Loaded " + species.size() + " species, " + moves.size() + " moves");
    }

    /** Ids of species bundled in the jar, from species/_index.json. */
    private List<String> bundledSpecies() {
        try (java.io.InputStream in = plugin.getResource("species/_index.json")) {
            if (in == null) return List.of();
            try (Reader r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                List<String> ids = gson.fromJson(r, new TypeToken<List<String>>() {}.getType());
                return ids == null ? List.of() : ids;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Failed to read species index: " + e.getMessage());
            return List.of();
        }
    }

    public PokemonSpecies getSpecies(String id) { return species.get(id); }
    public MoveData getMove(String id) { return moves.get(id); }
    public Collection<PokemonSpecies> all() { return species.values(); }

    /** Map of species id -> the species it evolves FROM (for breeding). */
    public Map<String, String> childToParent() {
        Map<String, String> map = new LinkedHashMap<>();
        for (PokemonSpecies s : species.values()) {
            for (PokemonSpecies.Evolution evo : s.allEvolutions()) {
                if (evo.to != null) map.put(evo.to, s.id);
            }
        }
        return map;
    }
}
