package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;

import java.util.concurrent.ThreadLocalRandom;

public final class DamageCalculator {

    private DamageCalculator() {}

    public record Result(int damage, double effectiveness, boolean critical, boolean missed) {}

    public static Result calculate(PokemonInstance attacker, PokemonSpecies attackerSpecies,
                                   PokemonInstance defender, PokemonSpecies defenderSpecies,
                                   MoveData move) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (move.accuracy < 100 && rnd.nextInt(100) >= move.accuracy) {
            return new Result(0, 1.0, false, true);
        }
        if (move.category == MoveData.Category.STATUS || move.power <= 0) {
            return new Result(0, 1.0, false, false);
        }

        boolean physical = move.category == MoveData.Category.PHYSICAL;
        int atk = attacker.stat(attackerSpecies, physical ? 1 : 3);
        int def = defender.stat(defenderSpecies, physical ? 2 : 4);

        double effectiveness = 1.0;
        for (PokemonType t : defenderSpecies.types) {
            effectiveness *= move.type.effectivenessAgainst(t);
        }
        double stab = attackerSpecies.types.contains(move.type) ? 1.5 : 1.0;
        boolean critical = rnd.nextInt(24) == 0;
        double crit = critical ? 1.5 : 1.0;
        double random = rnd.nextDouble(0.85, 1.0);

        double base = ((2.0 * attacker.level / 5.0 + 2.0) * move.power * atk / Math.max(1, def)) / 50.0 + 2.0;
        int damage = (int) Math.max(effectiveness == 0 ? 0 : 1,
                Math.floor(base * stab * effectiveness * crit * random));
        return new Result(damage, effectiveness, critical, false);
    }
}
