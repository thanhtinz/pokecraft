package dev.thanhtin.survivalcore.listener;

import dev.thanhtin.survivalcore.SurvivalCore;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Bootstrap a player's account on join and honour the spawn options. */
public class PlayerListener implements Listener {

    private final SurvivalCore plugin;

    public PlayerListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        double start = plugin.getConfig().getDouble("economy.starting-balance", 100);
        plugin.db().ensurePlayer(e.getPlayer().getUniqueId(), e.getPlayer().getName(), start);

        if (!e.getPlayer().hasPlayedBefore()
                && plugin.getConfig().getBoolean("spawn.teleport-on-first-join", true)) {
            Location spawn = plugin.db().getSpawn();
            if (spawn != null) plugin.getServer().getScheduler().runTask(plugin,
                    () -> e.getPlayer().teleport(spawn));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (plugin.getConfig().getBoolean("spawn.teleport-on-respawn", false)) {
            Location spawn = plugin.db().getSpawn();
            if (spawn != null) e.setRespawnLocation(spawn);
        }
    }
}
