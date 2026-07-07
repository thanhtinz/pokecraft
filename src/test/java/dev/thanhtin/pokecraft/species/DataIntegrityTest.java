package dev.thanhtin.pokecraft.species;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.thanhtin.pokecraft.battle.MoveData;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the bundled dex data: every learnset move a species references must
 * exist in the move pool, and every species must have at least one damaging
 * move it can actually use. Also proves moves.json deserializes cleanly into
 * {@link MoveData} (so every type/category/status/target enum value is valid).
 */
class DataIntegrityTest {

    private static final Gson GSON = new Gson();

    private static InputStream res(String path) {
        InputStream in = DataIntegrityTest.class.getResourceAsStream(path);
        assertNotNull(in, "missing bundled resource: " + path);
        return in;
    }

    private static Map<String, MoveData> loadMoves() {
        try (InputStream in = res("/moves/moves.json")) {
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, MoveData>>() {}.getType());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> loadIndex() {
        try (InputStream in = res("/species/_index.json")) {
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<List<String>>() {}.getType());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonObject loadSpecies(String id) {
        try (InputStream in = res("/species/" + id + ".json")) {
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void movePoolIsSubstantial() {
        Map<String, MoveData> moves = loadMoves();
        assertTrue(moves.size() > 500,
                "expected a large authentic move pool, got " + moves.size());
    }

    @Test
    void everyLearnsetMoveExistsAndSpeciesCanAttack() {
        Map<String, MoveData> moves = loadMoves();
        List<String> index = loadIndex();
        assertTrue(index.size() > 900, "expected the full dex, got " + index.size());

        List<String> dangling = new ArrayList<>();
        List<String> noDamage = new ArrayList<>();
        for (String id : index) {
            JsonObject species = loadSpecies(id);
            JsonObject learnset = species.getAsJsonObject("learnset");
            boolean canAttack = false;
            if (learnset != null) {
                for (String level : learnset.keySet()) {
                    for (var el : learnset.getAsJsonArray(level)) {
                        String move = el.getAsString();
                        MoveData data = moves.get(move);
                        if (data == null) {
                            dangling.add(id + "@" + level + ":" + move);
                        } else if (data.power > 0) {
                            canAttack = true;
                        }
                    }
                }
            }
            if (!canAttack) noDamage.add(id);
        }
        assertTrue(dangling.isEmpty(), "learnset moves not in the pool: " + dangling);
        assertTrue(noDamage.isEmpty(), "species with no damaging move: " + noDamage);
    }

    @Test
    void moveEffectsAreWellFormed() {
        for (Map.Entry<String, MoveData> e : loadMoves().entrySet()) {
            MoveData m = e.getValue();
            assertNotNull(m.type, "no type on " + e.getKey());
            assertNotNull(m.category, "no category on " + e.getKey());
            assertFalse(m.name == null || m.name.isBlank(), "no name on " + e.getKey());
            if (m.effect != null && m.effect.stat != 0) {
                // 1-5 = atk/def/spa/spd/spe, 6 = accuracy, 7 = evasion
                assertTrue(m.effect.stat >= 1 && m.effect.stat <= 7,
                        "bad stat index on " + e.getKey());
            }
        }
    }
}
