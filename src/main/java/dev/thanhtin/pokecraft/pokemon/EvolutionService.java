package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Level-up, item (evolution stone) and trade evolutions. */
public class EvolutionService {
    private final PokeCraftPlugin plugin;

    /** Classic no-item trade evolutions: species id -> what it becomes when traded. */
    private static final java.util.Map<String, String> TRADE_EVOLUTIONS = java.util.Map.of(
            "kadabra", "alakazam",
            "machoke", "machamp",
            "graveler", "golem",
            "haunter", "gengar",
            "boldore", "gigalith",
            "gurdurr", "conkeldurr",
            "phantump", "trevenant",
            "pumpkaboo", "gourgeist");

    public EvolutionService(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called after a pokemon changes hands in a trade. @return true if it evolved. */
    public boolean tryTradeEvolve(Player receiver, PokemonInstance p) {
        if (p.holds("everstone")) return false;
        String targetId = TRADE_EVOLUTIONS.get(p.speciesId);
        if (targetId == null) return false;
        PokemonSpecies from = plugin.species().getSpecies(p.speciesId);
        PokemonSpecies to = plugin.species().getSpecies(targetId);
        if (from == null || to == null) return false;
        apply(receiver, p, from, to);
        return true;
    }

    /** Called after level-ups; item evolutions never trigger from levels. */
    public void tryLevelEvolve(Player player, PokemonInstance p, PokemonSpecies species) {
        if (p.holds("everstone")) return;
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
        if (p.owner != null) plugin.storage().markCaught(p.owner, to.id);
        p.moves = PokemonInstance.latestMoves(to, p.level);
        p.currentHp = Math.min(p.currentHp, p.maxHp(to));
        player.sendMessage(Component.text("What? " + from.name + " is evolving... It became "
                + to.name + "!", NamedTextColor.LIGHT_PURPLE));
    }
}
