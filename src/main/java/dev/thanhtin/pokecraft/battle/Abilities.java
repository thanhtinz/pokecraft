package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.species.PokemonType;

/**
 * Damage-affecting abilities. Pure functions on the ability id so they are easy
 * to unit-test and to fold into {@link DamageCalculator}. Ability ids may be
 * hyphenated ("water-absorb"), underscored or plain; they are normalised here.
 */
public final class Abilities {

    private Abilities() {}

    static String norm(String ability) {
        return ability == null ? "" : ability.toLowerCase().replace("-", "").replace("_", "").replace(" ", "");
    }

    /** Defender takes no damage from this move type (Levitate, Flash Fire, absorbs...). */
    public static boolean immune(String defenderAbility, PokemonType moveType) {
        return switch (norm(defenderAbility)) {
            case "levitate" -> moveType == PokemonType.GROUND;
            case "flashfire" -> moveType == PokemonType.FIRE;
            case "waterabsorb", "stormdrain", "dryskin" -> moveType == PokemonType.WATER;
            case "voltabsorb", "lightningrod", "motordrive" -> moveType == PokemonType.ELECTRIC;
            case "sapsipper" -> moveType == PokemonType.GRASS;
            default -> false;
        };
    }

    /** Offensive multiplier applied to the attacker's attack stat. */
    public static double attackMultiplier(String attackerAbility, PokemonType moveType,
                                          boolean physical, boolean statused, double hpFraction) {
        String a = norm(attackerAbility);
        double m = 1.0;
        if (physical && (a.equals("hugepower") || a.equals("purepower"))) m *= 2.0;
        if (physical && a.equals("hustle")) m *= 1.5;
        if (a.equals("guts") && statused) m *= 1.5;
        if (hpFraction <= 1.0 / 3.0) {
            if ((a.equals("overgrow") && moveType == PokemonType.GRASS)
                    || (a.equals("blaze") && moveType == PokemonType.FIRE)
                    || (a.equals("torrent") && moveType == PokemonType.WATER)
                    || (a.equals("swarm") && moveType == PokemonType.BUG)) {
                m *= 1.5;
            }
        }
        return m;
    }

    /** Guts (and similar) ignore the burn attack drop. */
    public static boolean ignoresBurn(String attackerAbility) {
        return norm(attackerAbility).equals("guts");
    }

    /** Multiplier applied to the damage the defender takes. */
    public static double defenseMultiplier(String defenderAbility, PokemonType moveType,
                                           double effectiveness, boolean atFullHp) {
        String a = norm(defenderAbility);
        double m = 1.0;
        if (a.equals("thickfat") && (moveType == PokemonType.FIRE || moveType == PokemonType.ICE)) m *= 0.5;
        if (a.equals("heatproof") && moveType == PokemonType.FIRE) m *= 0.5;
        if (a.equals("multiscale") && atFullHp) m *= 0.5;
        if ((a.equals("filter") || a.equals("solidrock") || a.equals("prismarmor")) && effectiveness > 1.0) {
            m *= 0.75;
        }
        return m;
    }
}
