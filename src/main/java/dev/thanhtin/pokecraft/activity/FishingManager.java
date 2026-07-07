package dev.thanhtin.pokecraft.activity;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fishing: reeling in a catch has a chance to hook a wild Water-type pokemon
 * and start a battle, instead of a vanilla fish.
 */
public class FishingManager implements Listener {
    private final PokeCraftPlugin plugin;
    private List<PokemonSpecies> pool;

    public FishingManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    private List<PokemonSpecies> pool() {
        if (pool == null) {
            pool = new ArrayList<>();
            for (PokemonSpecies s : plugin.species().all()) {
                if (s.spawn != null && s.types != null && s.types.contains(PokemonType.WATER)) {
                    pool.add(s);
                }
            }
        }
        return pool;
    }

    /** Call after /poke reload so a changed dataset is reflected. */
    public void invalidate() { pool = null; }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!plugin.getConfig().getBoolean("fishing.enabled", true)) return;
        Player player = e.getPlayer();
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) return;
        if (plugin.parties().get(player).firstAlive() == null) return;

        double chance = plugin.getConfig().getDouble("fishing.catch-chance", 0.5);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        List<PokemonSpecies> pool = pool();
        if (pool.isEmpty()) return;
        PokemonSpecies species = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        int min = plugin.getConfig().getInt("fishing.min-level", 5);
        int max = plugin.getConfig().getInt("fishing.max-level", 25);
        int level = ThreadLocalRandom.current().nextInt(min, Math.max(min + 1, max + 1));

        e.setCancelled(true); // no vanilla fish
        if (e.getCaught() != null) e.getCaught().remove();

        Location loc = player.getLocation();
        PokemonInstance instance = PokemonInstance.generate(species, level,
                plugin.chains().shinyRate(player, plugin.getConfig().getInt("battle.shiny-rate", 4096)));
        LivingEntity entity = plugin.entities().spawnWild(species, instance, loc);
        player.sendMessage(Component.text("A wild " + species.name + " is biting!", NamedTextColor.AQUA));
        plugin.quests().progress(player, "fish", 1);
        plugin.battles().startWildBattle(player, entity);
    }
}
