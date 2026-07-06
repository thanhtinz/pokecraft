package dev.thanhtin.pokecraft.pokemon;

/** Medium-fast curve: exp(level) = level^3 */
public final class ExperienceCurve {
    public static final int MAX_LEVEL = 100;

    private ExperienceCurve() {}

    public static long expForLevel(int level) {
        long l = Math.max(1, Math.min(level, MAX_LEVEL));
        return l * l * l;
    }

    public static int levelForExp(long exp) {
        int level = 1;
        while (level < MAX_LEVEL && expForLevel(level + 1) <= exp) level++;
        return level;
    }
}
