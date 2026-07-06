package dev.thanhtin.pokecraft.pokemon;

import java.util.concurrent.ThreadLocalRandom;

/** Simplified natures: each boosts one stat 10% and lowers another 10%. */
public enum Nature {
    HARDY(0, 0), LONELY(1, 2), BRAVE(1, 5), ADAMANT(1, 3), NAUGHTY(1, 4),
    BOLD(2, 1), DOCILE(0, 0), RELAXED(2, 5), IMPISH(2, 3), LAX(2, 4),
    TIMID(5, 1), HASTY(5, 2), SERIOUS(0, 0), JOLLY(5, 3), NAIVE(5, 4),
    MODEST(3, 1), MILD(3, 2), QUIET(3, 5), BASHFUL(0, 0), RASH(3, 4),
    CALM(4, 1), GENTLE(4, 2), SASSY(4, 5), CAREFUL(4, 3), QUIRKY(0, 0);

    /** stat index: 0=hp 1=atk 2=def 3=spa 4=spd 5=spe (hp never affected) */
    public final int up, down;

    Nature(int up, int down) { this.up = up; this.down = down; }

    public double multiplier(int statIndex) {
        if (up == down) return 1.0;
        if (statIndex == up) return 1.1;
        if (statIndex == down) return 0.9;
        return 1.0;
    }

    public static Nature random() {
        Nature[] v = values();
        return v[ThreadLocalRandom.current().nextInt(v.length)];
    }
}
