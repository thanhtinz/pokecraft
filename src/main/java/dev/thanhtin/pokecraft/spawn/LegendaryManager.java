package dev.thanhtin.pokecraft.spawn;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rare server-wide legendary encounters. On each tick (every
 * {@code legendary.interval-minutes}) there is a {@code legendary.chance} of a
 * random legendary spawning near a random online player, announced to everyone.
 * Legendaries never spawn from the normal biome roll, so this is the only way
 * to meet them in the wild.
 */
public class LegendaryManager {

    /** Iconic legendaries/mythicals that exist in the bundled dex. */
    private static final List<String> LEGENDARIES = List.of(
            "articuno", "zapdos", "moltres", "mewtwo", "mew",
            "raikou", "entei", "suicune", "lugia", "ho-oh", "celebi",
            "groudon", "kyogre", "rayquaza",
            "dialga", "palkia", "giratina",
            "reshiram", "zekrom", "kyurem",
            "xerneas", "yveltal", "zygarde",
            "solgaleo", "lunala", "necrozma",
            "zacian", "zamazenta", "koraidon", "miraidon");

    private final PokeCraftPlugin plugin;
    private BukkitRunnable task;

    public LegendaryManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("legendary.event-enabled", true)) return;
        long minutes = Math.max(1, plugin.getConfig().getLong("legendary.interval-minutes", 60));
        long ticks = minutes * 60L * 20L;
        task = new BukkitRunnable() {
            @Override public void run() { tryEvent(); }
        };
        task.runTaskTimer(plugin, ticks, ticks);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    /** Force a legendary encounter now (OP / command / testing). */
    public boolean spawnNow(Player near) {
        if (near == null) return false;
        return spawnFor(near);
    }

    private void tryEvent() {
        if (!plugin.getConfig().getBoolean("legendary.event-enabled", true)) return;
        double chance = plugin.getConfig().getDouble("legendary.chance", 0.5);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (players.isEmpty()) return;
        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        spawnFor(target);
    }

    private boolean spawnFor(Player target) {
        PokemonSpecies species = pickLegendary();
        if (species == null) return false;
        Location loc = findGround(target);
        if (loc == null) return false;

        int min = plugin.getConfig().getInt("legendary.min-level", 50);
        int max = Math.max(min, plugin.getConfig().getInt("legendary.max-level", 60));
        int level = ThreadLocalRandom.current().nextInt(min, max + 1);
        int shinyRate = plugin.getConfig().getInt("legendary.shiny-rate", 1024);
        PokemonInstance instance = PokemonInstance.generate(species, level, shinyRate);
        plugin.entities().spawnWild(species, instance, loc);

        plugin.getServer().broadcast(Component.text("A wild " + species.name
                + " has appeared near " + target.getName() + "!", NamedTextColor.GOLD));
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 0.8f);
        }
        return true;
    }

    private PokemonSpecies pickLegendary() {
        List<PokemonSpecies> available = new ArrayList<>();
        for (String id : LEGENDARIES) {
            PokemonSpecies s = plugin.species().getSpecies(id);
            if (s != null) available.add(s);
        }
        if (available.isEmpty()) return null;
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private Location findGround(Player player) {
        World world = player.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = rnd.nextDouble(8, 24);
            int x = player.getLocation().getBlockX() + (int) (Math.cos(angle) * dist);
            int z = player.getLocation().getBlockZ() + (int) (Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) continue;
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (Math.abs(loc.getY() - player.getLocation().getY()) > 24) continue;
            return loc;
        }
        return null;
    }
}
