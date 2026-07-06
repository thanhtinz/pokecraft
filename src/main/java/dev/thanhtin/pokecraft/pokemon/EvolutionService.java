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
        if (species.evolution == null || species.evolution.item != null) return;
        if (p.level < species.evolution.level) return;
        PokemonSpecies target = plugin.species().getSpecies(species.evolution.to);
        if (target == null) return; // evolution species file not installed yet
        apply(player, p, species, target);
    }

    /** @return true if the item evolved this pokemon (item should be consumed). */
    public boolean tryItemEvolve(Player player, PokemonInstance p, String itemId) {
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null || species.evolution == null) return false;
        if (!itemId.equals(species.evolution.item)) return false;
        PokemonSpecies target = plugin.species().getSpecies(species.evolution.to);
        if (target == null) return false;
        apply(player, p, species, target);
        return true;
    }

    private void apply(Player player, PokemonInstance p, PokemonSpecies from, PokemonSpecies to) {
        p.speciesId = to.id;
        p.moves = PokemonInstance.latestMoves(to, p.level);
        p.currentHp = Math.min(p.currentHp, p.maxHp(to));
        player.sendMessage(Component.text("What? " + from.name + " is evolving... It became "
                + to.name + "!", NamedTextColor.LIGHT_PURPLE));
    }
}
