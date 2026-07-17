package dev.thanhtin.survivalcore.auction;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database.Auction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Chest GUI for the auction house - browse & buy, or view your own listings to cancel. */
public class AuctionGui implements Listener {

    private static final int PAGE = 45;

    private final SurvivalCore plugin;
    private final NamespacedKey keyId;
    private final NamespacedKey keyAction;

    public AuctionGui(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey(plugin, "ah_id");
        this.keyAction = new NamespacedKey(plugin, "ah_action");
    }

    private static class Holder implements InventoryHolder {
        int page;
        boolean mine;
        Inventory inv;
        Holder(int page, boolean mine) { this.page = page; this.mine = mine; }
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player, int page, boolean mine) {
        List<Auction> list = mine
                ? plugin.db().auctionsBySeller(player.getUniqueId())
                : plugin.db().listAuctions(PAGE, page * PAGE);
        Holder holder = new Holder(page, mine);
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text(mine ? "Your Listings" : "Auction House"));
        holder.inv = inv;

        int shown = mine ? Math.min(PAGE, list.size()) : list.size();
        for (int i = 0; i < shown && i < PAGE; i++) {
            Auction a = list.get(i);
            inv.setItem(i, listingIcon(a, mine));
        }

        if (!mine && page > 0) inv.setItem(45, nav("prevpage", Material.ARROW, "Previous page"));
        if (!mine && list.size() == PAGE) inv.setItem(53, nav("nextpage", Material.ARROW, "Next page"));
        inv.setItem(49, nav(mine ? "market" : "mine",
                mine ? Material.CHEST : Material.WRITABLE_BOOK,
                mine ? "Back to market" : "My listings"));
        player.openInventory(inv);
    }

    private ItemStack listingIcon(Auction a, boolean mine) {
        ItemStack item = plugin.auctions().decode(a.itemBase64());
        if (item == null) item = new ItemStack(Material.BARRIER);
        item = item.clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) lore.addAll(meta.lore());
        lore.add(Component.text("Seller: " + a.sellerName(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Price: " + plugin.economy().format(a.price()), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(mine ? Component.text("Click to cancel", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
                : Component.text("Click to buy", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyId, PersistentDataType.LONG, a.id());
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING,
                mine ? "cancel" : "buy");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nav(String action, Material mat, String label) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(keyAction, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "nextpage" -> open(player, holder.page + 1, false);
            case "prevpage" -> open(player, holder.page - 1, false);
            case "mine" -> open(player, 0, true);
            case "market" -> open(player, 0, false);
            case "buy" -> {
                Long id = pdc.get(keyId, PersistentDataType.LONG);
                if (id != null) { plugin.auctions().buy(player, id); open(player, holder.page, false); }
            }
            case "cancel" -> {
                Long id = pdc.get(keyId, PersistentDataType.LONG);
                if (id != null) { plugin.auctions().cancel(player, id); open(player, 0, true); }
            }
            default -> {}
        }
    }
}
