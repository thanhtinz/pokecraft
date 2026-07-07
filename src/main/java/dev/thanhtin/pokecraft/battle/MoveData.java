package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.pokemon.StatusCondition;
import dev.thanhtin.pokecraft.species.PokemonType;

public class MoveData {
    public transient String id;
    public String name;
    public PokemonType type;
    public Category category;
    public int power;
    public int accuracy;
    public int pp;
    /** Higher priority moves act first regardless of speed (e.g. Quick Attack = 1). */
    public int priority;
    public Effect effect;

    public enum Category { PHYSICAL, SPECIAL, STATUS }

    public enum Target { SELF, TARGET }

    /** Optional secondary effect: a stat stage change and/or a status condition. */
    public static class Effect {
        /** Stat index 1-5 (atk/def/spa/spd/spe), 6 = accuracy, 7 = evasion; 0 = none. */
        public int stat;
        /** Stages added to the stat (-6..+6). */
        public int stages;
        public Target target = Target.TARGET;
        public StatusCondition status;
        /** Percent chance (1-100) that {@link #status} is inflicted. */
        public int statusChance;
    }
}
