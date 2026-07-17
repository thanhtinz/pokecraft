package dev.thanhtin.survivalcore.teleport;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** /rtp - drop the player at a random safe surface location within a radius. */
public class RtpManager {

    private final SurvivalCore plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public RtpManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void rtp(Player player) {
        long cd = plugin.getConfig().getLong("rtp.cooldown-seconds", 60) * 1000L;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (!player.hasPermission("survivalcore.teleport.bypass")
                && last != null && now - last < cd) {
            Msg.warn(player, "RTP recharging: " + ((cd - (now - last)) / 1000 + 1) + "s.");
            return;
        }
        World world = player.getWorld();
        int min = plugin.getConfig().getInt("rtp.min-radius", 500);
        int max = plugin.getConfig().getInt("rtp.max-radius", 5000);
        boolean avoidWater = plugin.getConfig().getBoolean("rtp.avoid-water", true);

        Msg.info(player, "Searching for a safe spot...");
        for (int attempt = 0; attempt < 40; attempt++) {
            Location origin = world.getSpawnLocation();
            int dx = signedRange(min, max);
            int dz = signedRange(min, max);
            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (!ground.getType().isSolid()) continue;
            if (above.getType() != Material.AIR && above.getType() != Material.CAVE_AIR) continue;
            if (ground.isLiquid()) continue;
            if (avoidWater && (ground.getType() == Material.WATER || ground.getType() == Material.LAVA)) continue;
            Location dest = new Location(world, x + 0.5, y + 1, z + 0.5,
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            cooldowns.put(player.getUniqueId(), now);
            plugin.teleports().teleport(player, dest, "the wild");
            return;
        }
        Msg.error(player, "Couldn't find a safe spot, try again.");
    }

    private int signedRange(int min, int max) {
        int v = ThreadLocalRandom.current().nextInt(min, Math.max(min + 1, max));
        return ThreadLocalRandom.current().nextBoolean() ? v : -v;
    }
}
