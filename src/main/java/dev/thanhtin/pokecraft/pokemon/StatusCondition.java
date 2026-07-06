package dev.thanhtin.pokecraft.pokemon;

/** Persistent (non-volatile) status conditions. Cleared by healing. */
public enum StatusCondition {
    BURN("burned", "BRN"),
    PARALYSIS("paralyzed", "PAR"),
    POISON("poisoned", "PSN"),
    SLEEP("asleep", "SLP"),
    FREEZE("frozen", "FRZ");

    public final String verb;
    public final String tag;

    StatusCondition(String verb, String tag) {
        this.verb = verb;
        this.tag = tag;
    }

    /** Fraction of max HP lost at the end of each turn (0 = none). */
    public double residualDamage() {
        return switch (this) {
            case BURN -> 1.0 / 16.0;
            case POISON -> 1.0 / 8.0;
            default -> 0;
        };
    }

    /** Capture rate bonus when the wild pokemon has this status. */
    public double catchBonus() {
        return switch (this) {
            case SLEEP, FREEZE -> 2.0;
            default -> 1.5;
        };
    }
}
