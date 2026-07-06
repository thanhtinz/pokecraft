package dev.thanhtin.pokecraft.pokemon;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Pure breeding rules: compatibility by evolution family, IV inheritance. */
public final class Breeding {

    /** Number of IVs a child inherits from its parents (rest are random). */
    public static final int INHERITED_IVS = 3;

    private Breeding() {}

    /**
     * Walks the evolution chain backwards to the family's base form.
     * @param childToParent map of species id -> the species it evolves FROM
     */
    public static String baseForm(String speciesId, Map<String, String> childToParent) {
        String current = speciesId;
        Set<String> seen = new HashSet<>();
        while (childToParent.containsKey(current) && seen.add(current)) {
            current = childToParent.get(current);
        }
        return current;
    }

    /** Two pokemon can breed when they share the same evolution family. */
    public static boolean compatible(String speciesA, String speciesB, Map<String, String> childToParent) {
        return baseForm(speciesA, childToParent).equals(baseForm(speciesB, childToParent));
    }

    /**
     * Child IVs: {@link #INHERITED_IVS} random stats are copied from a random
     * parent, the remaining stats roll fresh 0-31.
     */
    public static int[] childIvs(int[] parentA, int[] parentB, RandomGenerator rnd) {
        int[] child = new int[6];
        for (int i = 0; i < 6; i++) child[i] = rnd.nextInt(32);

        Set<Integer> picked = new HashSet<>();
        while (picked.size() < INHERITED_IVS) picked.add(rnd.nextInt(6));
        for (int stat : picked) {
            child[stat] = (rnd.nextBoolean() ? parentA : parentB)[stat];
        }
        return child;
    }
}
