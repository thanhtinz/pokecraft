package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Level-up and item (evolution stone) evolutions. */
public class EvolutionService {
    private final PokeCraftPlugin plugin;

    public EvolutionService(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called after level-ups; item evolutions never trigger from levels. */
    public void tryLevelEvolve(Player player, PokemonInstance p, PokemonSpecies species) {
        for (PokemonSpecies.Evolution evo : species.allEvolutions()) {
            if (evo.item != null || evo.to == null || p.level < evo.level) continue;
            PokemonSpecies target = plugin.species().getSpecies(evo.to);
            if (target == null) continue; // evolution species file not installed yet
            apply(player, p, species, target);
            return;
        }
    }

    /** @return true if the item evolved this pokemon (item should be consumed). */
    public boolean tryItemEvolve(Player player, PokemonInstance p, String itemId) {
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null) return false;
        for (PokemonSpecies.Evolution evo : species.allEvolutions()) {
            if (!itemId.equals(evo.item) || evo.to == null) continue;
            PokemonSpecies target = plugin.species().getSpecies(evo.to);
            if (target == null) continue;
            apply(player, p, species, target);
            return true;
        }
        return false;
    }

    private void apply(Player player, PokemonInstance p, PokemonSpecies from, PokemonSpecies to) {
        p.speciesId = to.id;
        p.moves = PokemonInstance.latestMoves(to, p.level);
        p.currentHp = Math.min(p.currentHp, p.maxHp(to));
        player.sendMessage(Component.text("What? " + from.name + " is evolving... It became "
                + to.name + "!", NamedTextColor.LIGHT_PURPLE));
    }
}
