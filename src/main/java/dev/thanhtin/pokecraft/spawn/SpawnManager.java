package dev.thanhtin.pokecraft.spawn;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;
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
        World world = loc.getWorld();
        List<PokemonSpecies> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;
        for (PokemonSpecies s : plugin.species().all()) {
            if (s.spawn == null || s.spawn.biomes == null) continue;
            if (s.spawn.biomes.contains(biome)) {
                int w = adjustedWeight(s, world);
                pool.add(s);
                weights.add(w);
                totalWeight += w;
            }
        }
        if (pool.isEmpty() || totalWeight <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return pool.get(i);
        }
        return pool.get(0);
    }

    /** Spawn weight after day/night and weather influence (by type). */
    private int adjustedWeight(PokemonSpecies s, World world) {
        int w = Math.max(1, s.spawn.weight);
        if (!plugin.getConfig().getBoolean("spawning.time-weather-influence", true)) return w;
        boolean night = isNight(world);
        boolean storm = world.hasStorm();
        double mult = 1.0;
        if (s.types != null) {
            for (PokemonType t : s.types) {
                if (night) {
                    if (t == PokemonType.GHOST || t == PokemonType.DARK
                            || t == PokemonType.POISON || t == PokemonType.ICE) mult *= 1.6;
                } else {
                    if (t == PokemonType.NORMAL || t == PokemonType.BUG
                            || t == PokemonType.FLYING || t == PokemonType.GRASS
                            || t == PokemonType.FAIRY) mult *= 1.3;
                }
                if (storm && (t == PokemonType.WATER || t == PokemonType.ELECTRIC)) mult *= 1.6;
            }
        }
        return Math.max(1, (int) Math.round(w * mult));
    }

    private boolean isNight(World world) {
        long t = world.getTime() % 24000L;
        return t >= 13000 && t <= 23000;
    }
}
