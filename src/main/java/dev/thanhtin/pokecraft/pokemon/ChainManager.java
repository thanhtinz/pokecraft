package dev.thanhtin.pokecraft.pokemon;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catch chain / shiny hunting: catching the same species in a row raises your
 * shiny odds for that species. Catching a different species resets the chain.
 * The boost applies where instances are generated per-player (e.g. fishing).
 */
public class ChainManager {

    private final PokeCraftPlugin plugin;
    private final Map<UUID, String> lastSpecies = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> count = new ConcurrentHashMap<>();

    public ChainManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("chain.enabled", true);
    }

    /** Record a capture; extends the chain if it's the same species as last time. */
    public void onCatch(Player player, String speciesId) {
        if (!enabled() || speciesId == null) return;
        UUID id = player.getUniqueId();
        if (speciesId.equalsIgnoreCase(lastSpecies.get(id))) {
            count.merge(id, 1, Integer::sum);
        } else {
            lastSpecies.put(id, speciesId.toLowerCase());
            count.put(id, 1);
        }
        int c = count.get(id);
        if (c >= 5 && c % 5 == 0) {
            player.sendMessage(Component.text("Catch chain x" + c
                    + " - shiny odds are climbing!", NamedTextColor.LIGHT_PURPLE));
        }
    }

    public int chain(Player player) {
        return enabled() ? count.getOrDefault(player.getUniqueId(), 0) : 0;
    }

    /** A shiny rate (1/N) improved by the current chain; lower N = more likely. */
    public int shinyRate(Player player, int baseRate) {
        int c = chain(player);
        if (c <= 1) return baseRate;
        int rate = (int) (baseRate / (1.0 + c * 0.15));
        return Math.max(plugin.getConfig().getInt("chain.min-shiny-rate", 128), rate);
    }

    public void reset(Player player) {
        lastSpecies.remove(player.getUniqueId());
        count.remove(player.getUniqueId());
    }
}
