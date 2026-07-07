package dev.thanhtin.pokecraft.minimap;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 * A filled-map "PokeMap" that follows the player and marks nearby wild pokemon
 * and players. Filled maps render on Bedrock via Geyser, and a map held in the
 * OFF-HAND shows as a small minimap in the screen corner on mobile - so the
 * PokeMap goes into the off-hand. A "PokeNav" compass toggles it on and off
 * (open the compass to pop the minimap up in the corner).
 */
public class MinimapManager implements Listener {
    private final PokeCraftPlugin plugin;
    private final Map<UUID, MapView> views = new ConcurrentHashMap<>();
    private final NamespacedKey keyMap;
    private final NamespacedKey keyNav;
    private BukkitRunnable task;

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

    public void stop() {
        if (task != null) task.cancel();
    }

    /**
     * Gives the player the PokeNav compass and pops the minimap into their
     * off-hand so it shows in the corner. Called by the menu's "Get PokeMap".
     */
    public void give(Player player) {
        if (!hasNav(player)) {
            var leftover = player.getInventory().addItem(createNav());
            leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
        showMinimap(player);
        player.sendMessage(Component.text(
                "PokeMap is now in the corner. Use the PokeNav compass to hide/show it.",
                NamedTextColor.GREEN));
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
                Component.text("Shows as a minimap in the corner on mobile", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyMap, PersistentDataType.BYTE, (byte) 1);
        map.setItemMeta(meta);

        // move whatever is in the off-hand into the inventory, then equip the map
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

    // ---------- the PokeNav compass ----------

    public ItemStack createNav() {
        ItemStack nav = new ItemStack(Material.COMPASS);
        ItemMeta meta = nav.getItemMeta();
        meta.displayName(Component.text("PokeNav", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Right-click / tap to show or hide", NamedTextColor.GRAY),
                Component.text("the minimap in the corner", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyNav, PersistentDataType.BYTE, (byte) 1);
        nav.setItemMeta(meta);
        return nav;
    }

    public boolean isNav(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyNav, PersistentDataType.BYTE);
    }

    public boolean isPokeMap(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyMap, PersistentDataType.BYTE);
    }

    private boolean hasNav(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isNav(item)) return true;
        }
        return false;
    }

    /** Compass action: pop the minimap up if hidden, put it away if shown. */
    public void toggle(Player player) {
        if (hideMinimap(player)) {
            player.sendMessage(Component.text("Minimap hidden.", NamedTextColor.GRAY));
        } else {
            showMinimap(player);
            player.sendMessage(Component.text("Minimap shown in the corner.", NamedTextColor.AQUA));
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isNav(e.getItem())) return;
        e.setCancelled(true);
        toggle(e.getPlayer());
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
        if (!plugin.getConfig().getBoolean("minimap.give-compass", true)) return;
        Player player = e.getPlayer();
        if (!hasNav(player)) {
            var leftover = player.getInventory().addItem(createNav());
            leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        views.remove(e.getPlayer().getUniqueId());
    }
}
