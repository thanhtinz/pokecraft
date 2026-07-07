package dev.thanhtin.pokecraft.minimap;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A filled-map "PokeMap" that follows the player and marks nearby wild pokemon
 * and players. Filled maps render on Bedrock via Geyser, unlike overlay
 * minimap mods which are client-only and never show on mobile.
 */
public class MinimapManager implements Listener {
    private final PokeCraftPlugin plugin;
    private final Map<UUID, MapView> views = new ConcurrentHashMap<>();
    private BukkitRunnable task;

    public MinimapManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = new BukkitRunnable() {
            @Override public void run() { follow(); }
        };
        task.runTaskTimer(plugin, 20L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    /** Builds (or rebuilds) a minimap for the player's current world and gives it. */
    public void give(Player player) {
        MapView view = plugin.getServer().createMap(player.getWorld());
        view.setScale(MapView.Scale.CLOSE);
        view.setTrackingPosition(true);
        view.setUnlimitedTracking(true);
        view.setCenterX(player.getLocation().getBlockX());
        view.setCenterZ(player.getLocation().getBlockZ());
        // keep the vanilla terrain renderer, add ours for cursors
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
                Component.text("Hold it to see your surroundings", NamedTextColor.GRAY)));
        map.setItemMeta(meta);
        var leftover = player.getInventory().addItem(map);
        leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        player.sendMessage(Component.text("Here's your PokeMap - hold it to use it.", NamedTextColor.GREEN));
    }

    private void follow() {
        for (Map.Entry<UUID, MapView> e : views.entrySet()) {
            Player player = plugin.getServer().getPlayer(e.getKey());
            MapView view = e.getValue();
            if (player == null) continue;
            if (!player.getWorld().equals(view.getWorld())) continue; // needs a re-give in the new world
            view.setCenterX(player.getLocation().getBlockX());
            view.setCenterZ(player.getLocation().getBlockZ());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        views.remove(e.getPlayer().getUniqueId());
    }
}
