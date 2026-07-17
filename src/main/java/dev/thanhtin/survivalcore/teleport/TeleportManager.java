package dev.thanhtin.survivalcore.teleport;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central safe-teleport with warmup + cooldown, plus /back tracking. Warmups are
 * cancelled if the player moves a block or takes damage (configurable).
 */
public class TeleportManager implements Listener {

    private final SurvivalCore plugin;
    private final Map<UUID, BukkitTask> warmups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Location> back = new ConcurrentHashMap<>();

    public TeleportManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    /** Store where the player is so /back can return them (call before a teleport). */
    public void recordBack(Player player) {
        back.put(player.getUniqueId(), player.getLocation());
    }

    public Location getBack(Player player) {
        return back.get(player.getUniqueId());
    }

    /** Teleport with warmup + cooldown. `bypassPerm` skips both when the player has it. */
    public void teleport(Player player, Location dest, String label) {
        if (dest == null || dest.getWorld() == null) {
            Msg.error(player, "That destination no longer exists.");
            return;
        }
        boolean bypass = player.hasPermission("survivalcore.teleport.bypass");
        long cd = plugin.getConfig().getLong("teleport.cooldown-seconds", 5) * 1000L;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (!bypass && last != null && now - last < cd) {
            Msg.warn(player, "Wait " + ((cd - (now - last)) / 1000 + 1) + "s before teleporting again.");
            return;
        }
        if (warmups.containsKey(player.getUniqueId())) {
            Msg.warn(player, "You are already teleporting.");
            return;
        }

        int warmup = bypass ? 0 : plugin.getConfig().getInt("teleport.warmup-seconds", 3);
        if (warmup <= 0) {
            doTeleport(player, dest, label);
            cooldowns.put(player.getUniqueId(), now);
            return;
        }

        Msg.info(player, "Teleporting to " + label + " in " + warmup + "s - don't move.");
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            warmups.remove(player.getUniqueId());
            if (player.isOnline()) {
                doTeleport(player, dest, label);
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }, warmup * 20L);
        warmups.put(player.getUniqueId(), task);
    }

    private void doTeleport(Player player, Location dest, String label) {
        recordBack(player);
        player.teleport(dest);
        Msg.ok(player, "Teleported to " + label + ".");
    }

    private void cancelWarmup(Player player, String why) {
        BukkitTask task = warmups.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            Msg.error(player, "Teleport cancelled (" + why + ").");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) return;
        if (!warmups.containsKey(e.getPlayer().getUniqueId())) return;
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            cancelWarmup(e.getPlayer(), "you moved");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player) cancelWarmup(player, "you took damage");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BukkitTask task = warmups.remove(e.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }
}
