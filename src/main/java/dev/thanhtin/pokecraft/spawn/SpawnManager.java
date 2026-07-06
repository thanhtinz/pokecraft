package dev.thanhtin.pokecraft.spawn;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnManager {
    private final PokeCraftPlugin plugin;
    private BukkitRunnable task;

    public SpawnManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("spawning.enabled", true)) return;
        long interval = plugin.getConfig().getLong("spawning.interval-ticks", 200);
        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        };
        task.runTaskTimer(plugin, interval, interval);
        plugin.getLogger().info("[OK] Spawn task started (every " + interval + " ticks)");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        int cap = plugin.getConfig().getInt("spawning.max-wild-per-player", 4);
        int rMin = plugin.getConfig().getInt("spawning.spawn-radius-min", 12);
        int rMax = plugin.getConfig().getInt("spawning.spawn-radius-max", 32);
        int despawn = plugin.getConfig().getInt("spawning.despawn-distance", 64);

        despawnSweep();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            int nearby = 0;
            for (Entity e : player.getNearbyEntities(despawn, despawn, despawn)) {
                if (plugin.entities().isWild(e)) nearby++;
            }
            if (nearby >= cap) continue;

            Location loc = pickLocation(player, rMin, rMax);
            if (loc == null) continue;

            PokemonSpecies species = pickSpecies(loc);
            if (species == null) continue;

            int level = ThreadLocalRandom.current().nextInt(
                    species.spawn.minLevel, species.spawn.maxLevel + 1);
            int shinyRate = plugin.getConfig().getInt("battle.shiny-rate", 4096);
            PokemonInstance instance = PokemonInstance.generate(species, level, shinyRate);
            plugin.entities().spawnWild(species, instance, loc);
        }
    }

    /** Remove wild pokemon that are too old or too far from every player. */
    private void despawnSweep() {
        long maxAgeMillis = plugin.getConfig().getInt("spawning.despawn-seconds", 300) * 1000L;
        int distance = plugin.getConfig().getInt("spawning.despawn-distance", 64);
        long distanceSq = (long) distance * distance;
        long now = System.currentTimeMillis();

        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!plugin.entities().isWild(entity)) continue;
                if (plugin.battles().isWildInBattle(entity.getUniqueId())) continue;

                long spawnTime = plugin.entities().spawnTime(entity);
                boolean tooOld = maxAgeMillis > 0 && spawnTime > 0 && now - spawnTime > maxAgeMillis;

                boolean nearPlayer = false;
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(entity.getLocation()) <= distanceSq) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (tooOld || !nearPlayer) entity.remove();
            }
        }
    }

    private Location pickLocation(Player player, int rMin, int rMax) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World world = player.getWorld();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = rnd.nextDouble(rMin, rMax);
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

    private PokemonSpecies pickSpecies(Location loc) {
        String biome = loc.getBlock().getBiome().getKey().getKey().toUpperCase(Locale.ROOT);
        List<PokemonSpecies> pool = new ArrayList<>();
        int totalWeight = 0;
        for (PokemonSpecies s : plugin.species().all()) {
            if (s.spawn == null || s.spawn.biomes == null) continue;
            if (s.spawn.biomes.contains(biome)) {
                pool.add(s);
                totalWeight += s.spawn.weight;
            }
        }
        if (pool.isEmpty()) return null;
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (PokemonSpecies s : pool) {
            roll -= s.spawn.weight;
            if (roll < 0) return s;
        }
        return pool.get(0);
    }
}
