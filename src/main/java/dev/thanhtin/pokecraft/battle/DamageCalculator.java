package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.pokemon.StatusCondition;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;

import java.util.concurrent.ThreadLocalRandom;

public final class DamageCalculator {

    private DamageCalculator() {}

    public record Result(int damage, double effectiveness, boolean critical, boolean missed) {}

    /** Multiplier for a stat stage in -6..+6 (gen-style (2+s)/2 or 2/(2-s)). */
    public static double stageMultiplier(int stage) {
        int s = Math.max(-6, Math.min(6, stage));
        return s >= 0 ? (2.0 + s) / 2.0 : 2.0 / (2.0 - s);
    }

    /**
     * @param attackerStages/defenderStages per-battle stat stages indexed like
     *        {@link PokemonInstance#stat} (1=atk 2=def 3=spa 4=spd 5=spe); may be null.
     */
    public static Result calculate(PokemonInstance attacker, PokemonSpecies attackerSpecies, int[] attackerStages,
                                   PokemonInstance defender, PokemonSpecies defenderSpecies, int[] defenderStages,
                                   MoveData move) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (move.accuracy < 100 && rnd.nextInt(100) >= move.accuracy) {
            return new Result(0, 1.0, false, true);
        }
        if (move.category == MoveData.Category.STATUS || move.power <= 0) {
            return new Result(0, 1.0, false, false);
        }

        String attackerAbility = attacker.ability(attackerSpecies);
        String defenderAbility = defender.ability(defenderSpecies);
        // ability-based immunity (Levitate, Flash Fire, Water/Volt Absorb, ...)
        if (Abilities.immune(defenderAbility, move.type)) {
            return new Result(0, 0.0, false, false);
        }

        boolean physical = move.category == MoveData.Category.PHYSICAL;
        int atkIndex = physical ? 1 : 3;
        int defIndex = physical ? 2 : 4;
        double atk = attacker.stat(attackerSpecies, atkIndex)
                * stageMultiplier(attackerStages == null ? 0 : attackerStages[atkIndex]);
        double def = defender.stat(defenderSpecies, defIndex)
                * stageMultiplier(defenderStages == null ? 0 : defenderStages[defIndex]);
        double hpFraction = attacker.currentHp / (double) Math.max(1, attacker.maxHp(attackerSpecies));
        atk *= Abilities.attackMultiplier(attackerAbility, move.type, physical,
                attacker.status != null, hpFraction);
        if (physical && attacker.status == StatusCondition.BURN
                && !Abilities.ignoresBurn(attackerAbility)) {
            atk *= 0.5;
        }

        double effectiveness = 1.0;
        for (PokemonType t : defenderSpecies.types) {
            effectiveness *= move.type.effectivenessAgainst(t);
        }
        double stab = attackerSpecies.types.contains(move.type) ? 1.5 : 1.0;
        double held = (physical && attacker.holds("muscle_band"))
                || (!physical && attacker.holds("wise_glasses")) ? 1.1 : 1.0;
        boolean critical = rnd.nextInt(24) == 0;
        double crit = critical ? 1.5 : 1.0;
        double random = rnd.nextDouble(0.85, 1.0);

        boolean atFullHp = defender.currentHp >= defender.maxHp(defenderSpecies);
        double abilityDef = Abilities.defenseMultiplier(defenderAbility, move.type, effectiveness, atFullHp);

        double base = ((2.0 * attacker.level / 5.0 + 2.0) * move.power * atk / Math.max(1.0, def)) / 50.0 + 2.0;
        int damage = (int) Math.max(effectiveness == 0 ? 0 : 1,
                Math.floor(base * stab * held * effectiveness * crit * random * abilityDef));
        return new Result(damage, effectiveness, critical, false);
    }
}
