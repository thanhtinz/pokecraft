package dev.thanhtin.survivalcore.shop;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.shop.ShopManager.ShopItem;
import dev.thanhtin.survivalcore.util.Msg;
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

/** Paginated shop menu. Left-click buys, right-click sells. */
public class ShopGui implements Listener {

    private static final int PAGE = 45;

    private final SurvivalCore plugin;
    private final NamespacedKey keyIndex;
    private final NamespacedKey keyNav;

    public ShopGui(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyIndex = new NamespacedKey(plugin, "shop_index");
        this.keyNav = new NamespacedKey(plugin, "shop_nav");
    }

    private static class Holder implements InventoryHolder {
        int page;
        Inventory inv;
        Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player, int page) {
        List<ShopItem> items = plugin.shop().items();
        int maxPage = Math.max(0, (items.size() - 1) / PAGE);
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        Holder holder = new Holder(page);
        Inventory inv = plugin.getServer().createInventory(holder, 54, Msg.legacy(plugin.shop().title()));
        holder.inv = inv;

        int start = page * PAGE;
        for (int i = 0; i < PAGE && start + i < items.size(); i++) {
            inv.setItem(i, icon(items.get(start + i), start + i));
        }
        if (page > 0) inv.setItem(45, nav(Material.ARROW, "&ePrevious page", "prev"));
        if (page < maxPage) inv.setItem(53, nav(Material.ARROW, "&eNext page", "next"));
        inv.setItem(49, nav(Material.EMERALD, "&aYour balance: "
                + plugin.economy().format(plugin.economy().balance(player.getUniqueId())), "none"));
        player.openInventory(inv);
    }

    private ItemStack icon(ShopItem si, int index) {
        ItemStack item = new ItemStack(si.material(), si.amount());
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (si.canBuy()) {
            lore.add(Component.text("Left-click: buy " + si.amount() + " for "
                    + plugin.economy().format(si.buy()), NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (si.canSell()) {
            lore.add(Component.text("Right-click: sell " + si.amount() + " for "
                    + plugin.economy().format(si.sell()), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyIndex, PersistentDataType.INTEGER, index);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack nav(Material mat, String name, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.legacy(name).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(keyNav, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        var pdc = clicked.getItemMeta().getPersistentDataContainer();

        String nav = pdc.get(keyNav, PersistentDataType.STRING);
        if (nav != null) {
            if (nav.equals("next")) open(p, h.page + 1);
            else if (nav.equals("prev")) open(p, h.page - 1);
            return;
        }
        Integer index = pdc.get(keyIndex, PersistentDataType.INTEGER);
        if (index == null || index < 0 || index >= plugin.shop().items().size()) return;
        ShopItem si = plugin.shop().items().get(index);
        if (e.isRightClick()) plugin.shop().sell(p, si);
        else plugin.shop().buy(p, si);
        // refresh the balance indicator
        e.getInventory().setItem(49, nav(Material.EMERALD, "&aYour balance: "
                + plugin.economy().format(plugin.economy().balance(p.getUniqueId())), "none"));
    }
}
