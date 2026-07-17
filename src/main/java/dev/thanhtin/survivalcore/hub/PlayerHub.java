package dev.thanhtin.survivalcore.hub;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * A shared "menu" compass every player carries. It cannot be dropped, moved,
 * or lost on death, and re-appears if somehow missing. Right-clicking it opens
 * the hub - the one navigation menu players use to reach shop-style services;
 * roleplay systems (kits, jobs, crates) stay out in the world.
 */
public class PlayerHub implements Listener {

    private static final int SLOT = 8; // hotbar slot the compass is pinned to

    private final SurvivalCore plugin;
    private final NamespacedKey keyItem;
    private final NamespacedKey keyAction;

    public PlayerHub(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyItem = new NamespacedKey(plugin, "hub_item");
        this.keyAction = new NamespacedKey(plugin, "hub_action");
    }

    // ---------- the menu item ----------

    public ItemStack menuItem() {
        Material mat = matOr(plugin.getConfig().getString("hub.item", "NETHER_STAR"), Material.NETHER_STAR);
        String name = plugin.getConfig().getString("hub.name", "&b&lᴍᴇɴᴜ");
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.legacy(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Msg.legacy("&7Right-click to open the menu").decoration(TextDecoration.ITALIC, false)));
        if (plugin.getConfig().getBoolean("hub.glow", true)) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        meta.getPersistentDataContainer().set(keyItem, PersistentDataType.STRING, "1");
        item.setItemMeta(meta);
        return item;
    }

    private Material matOr(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean isMenuItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyItem, PersistentDataType.STRING);
    }

    /** Make sure the player has exactly one menu item, pinned to the last hotbar slot. */
    public void ensureItem(Player player) {
        // remove any stray copies first
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != SLOT && isMenuItem(contents[i])) player.getInventory().setItem(i, null);
        }
        ItemStack pinned = player.getInventory().getItem(SLOT);
        if (!isMenuItem(pinned)) {
            // don't destroy whatever is there - shift it into the inventory, then place the item
            if (pinned != null && !pinned.getType().isAir()) {
                player.getInventory().addItem(pinned).values()
                        .forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
            }
            player.getInventory().setItem(SLOT, menuItem());
        } else {
            // refresh so config changes to the icon/name take effect
            player.getInventory().setItem(SLOT, menuItem());
        }
    }

    // ---------- keep it from ever being lost ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ensureItem(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.getServer().getScheduler().runTask(plugin, () -> ensureItem(e.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isMenuItem); // never drop it on death
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isMenuItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isMenuItem(e.getMainHandItem()) || isMenuItem(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClickItem(InventoryClickEvent e) {
        // stop the menu item from being moved out of place (but allow our hub GUI clicks)
        if (e.getInventory().getHolder() instanceof Holder) return;
        if (isMenuItem(e.getCurrentItem()) || isMenuItem(e.getCursor())) e.setCancelled(true);
        if (e.getClick().isKeyboardClick() && e.getHotbarButton() == SLOT) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (isMenuItem(e.getOldCursor())) e.setCancelled(true);
    }

    // ---------- open on right-click ----------

    @EventHandler(ignoreCancelled = true)
    public void onHold(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMenuItem(e.getItem())) return;
        e.setCancelled(true);
        open(e.getPlayer());
    }

    // ---------- the hub menu ----------

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27, Component.text("Menu"));
        holder.inv = inv;
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(10, button(Material.EMERALD, "&aBalance",
                List.of("&7You have " + plugin.economy().format(
                        plugin.economy().balance(player.getUniqueId()))), "balance"));
        inv.setItem(11, button(Material.CHEST, "&6Market",
                List.of("&7Browse the auction house"), "market"));
        inv.setItem(12, button(Material.CLOCK, "&eDaily Reward",
                List.of("&7Claim your daily reward"), "daily"));
        inv.setItem(13, button(Material.PAPER, "&bVote",
                List.of("&7Vote for the server for rewards"), "vote"));
        inv.setItem(14, button(Material.RED_BED, "&dHomes",
                List.of("&7Teleport to a home"), "homes"));
        inv.setItem(15, button(Material.NETHER_STAR, "&fSpawn",
                List.of("&7Warp to spawn"), "spawn"));
        inv.setItem(16, button(Material.DIAMOND, "&bRank",
                List.of("&7Your rank and how to rank up"), "rank"));

        if (player.hasPermission("survivalcore.admin")) {
            inv.setItem(22, button(Material.COMMAND_BLOCK, "&c&lAdmin Panel",
                    List.of("&7Server settings and tools"), "admin"));
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onHubClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String act = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        if (act == null) return;
        p.closeInventory();
        switch (act) {
            case "balance" -> Msg.info(p, "Balance: "
                    + plugin.economy().format(plugin.economy().balance(p.getUniqueId())));
            case "market" -> plugin.auctionGui().open(p, 0, false);
            case "daily" -> plugin.rewards().claimDaily(p);
            case "vote" -> plugin.votes().showLinks(p);
            case "homes" -> p.performCommand("homes");
            case "spawn" -> p.performCommand("spawn");
            case "rank" -> p.performCommand("rank");
            case "admin" -> {
                if (p.hasPermission("survivalcore.admin")) plugin.adminPanel().open(p);
            }
            default -> { }
        }
    }

    private ItemStack button(Material mat, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.legacy(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.legacy(s).decoration(TextDecoration.ITALIC, false));
        l.add(Component.text("Click", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }
}
