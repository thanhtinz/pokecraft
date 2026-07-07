package dev.thanhtin.pokecraft.minimap;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "PokeMap": opened from the main menu ("Get PokeMap"), not a separate
 * carried item. On the Java client a filled map in the off-hand renders as a
 * small corner minimap, so that's what Java players get (toggled from the
 * menu). Bedrock/mobile can't show an off-hand map in the corner, so those
 * players get a native radar popup instead (see BedrockSupport#tryOpenRadarForm).
 */
public class MinimapManager implements Listener {
    private final PokeCraftPlugin plugin;
    private final Map<UUID, MapView> views = new ConcurrentHashMap<>();
    private final NamespacedKey keyMap;
    private final NamespacedKey keyNav; // legacy PokeNav compass tag, kept for cleanup

    public MinimapManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyMap = new NamespacedKey(plugin, "pokemap");
        this.keyNav = new NamespacedKey(plugin, "pokenav");
    }

    public void start() {
        task = new BukkitRunnable() {
            @Override public void run() { follow(); }
        };
        task.runTaskTimer(plugin, 20L, 10L);
    }

    private BukkitRunnable task;

    public void stop() {
        if (task != null) task.cancel();
    }

    /**
     * Menu action ("Get PokeMap"): open the radar popup on Bedrock, or show/hide
     * the off-hand corner minimap on the Java client.
     */
    public void toggle(Player player) {
        if (plugin.bedrock().isBedrock(player)) {
            plugin.bedrock().tryOpenRadarForm(player);
            return;
        }
        if (hideMinimap(player)) {
            player.sendMessage(Component.text("PokeMap hidden.", NamedTextColor.GRAY));
        } else {
            showMinimap(player);
            player.sendMessage(Component.text(
                    "PokeMap is in your off-hand - it shows as a minimap in the corner.",
                    NamedTextColor.AQUA));
        }
    }

    /** Build a fresh minimap for the player's world and put it in the off-hand. */
    public void showMinimap(Player player) {
        MapView view = plugin.getServer().createMap(player.getWorld());
        view.setScale(MapView.Scale.CLOSE);
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(true);
        view.setCenterX(player.getLocation().getBlockX());
        view.setCenterZ(player.getLocation().getBlockZ());
        for (MapRenderer r : view.getRenderers()) {
            if (r instanceof MinimapRenderer) view.removeRenderer(r);
        }
        view.addRenderer(new MinimapRenderer(plugin));
        views.put(player.getUniqueId(), view);

        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text("PokeMap", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Red = wild pokemon, blue = players", NamedTextColor.GRAY),
                Component.text("Shows as a minimap in the corner", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyMap, PersistentDataType.BYTE, (byte) 1);
        map.setItemMeta(meta);

        ItemStack current = player.getInventory().getItemInOffHand();
        if (current != null && current.getType() != Material.AIR && !isPokeMap(current)) {
            var leftover = player.getInventory().addItem(current);
            leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
        player.getInventory().setItemInOffHand(map);
    }

    /** Remove the PokeMap from the off-hand if it's there. @return true if hidden */
    public boolean hideMinimap(Player player) {
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isPokeMap(off)) {
            player.getInventory().setItemInOffHand(null);
            views.remove(player.getUniqueId());
            return true;
        }
        return false;
    }

    public boolean isPokeMap(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyMap, PersistentDataType.BYTE);
    }

    private void follow() {
        for (Map.Entry<UUID, MapView> e : views.entrySet()) {
            Player player = plugin.getServer().getPlayer(e.getKey());
            MapView view = e.getValue();
            if (player == null) continue;
            if (!player.getWorld().equals(view.getWorld())) continue; // needs a re-show in the new world
            view.setCenterX(player.getLocation().getBlockX());
            view.setCenterZ(player.getLocation().getBlockZ());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // clean up the old PokeNav compass - the minimap opens from the menu now
        Player player = e.getPlayer();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(keyNav, PersistentDataType.BYTE)) {
                player.getInventory().remove(item);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        views.remove(e.getPlayer().getUniqueId());
    }
}
