package dev.thanhtin.survivalcore.shop;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A config-driven server shop: fixed buy and sell prices per item. */
public class ShopManager {

    public record ShopItem(Material material, int amount, double buy, double sell) {
        public boolean canBuy() { return buy > 0; }
        public boolean canSell() { return sell > 0; }
    }

    private final SurvivalCore plugin;
    private final List<ShopItem> items = new ArrayList<>();
    private String title = "Shop";

    public ShopManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        items.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("shop");
        if (root == null) { plugin.getLogger().info("[OK] Loaded 0 shop item(s)"); return; }
        title = root.getString("title", "Shop");
        for (Map<?, ?> raw : root.getMapList("items")) {
            Material mat = raw.get("item") instanceof String s ? matOr(s) : null;
            if (mat == null) continue;
            int amount = raw.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            double buy = raw.get("buy") instanceof Number n ? n.doubleValue() : 0;
            double sell = raw.get("sell") instanceof Number n ? n.doubleValue() : 0;
            items.add(new ShopItem(mat, amount, buy, sell));
        }
        plugin.getLogger().info("[OK] Loaded " + items.size() + " shop item(s)");
    }

    public List<ShopItem> items() { return items; }

    public String title() { return title; }

    public void buy(Player player, ShopItem item) {
        if (!item.canBuy()) { Msg.error(player, "That item isn't for sale."); return; }
        if (!plugin.economy().has(player.getUniqueId(), item.buy())) {
            Msg.error(player, "You need " + plugin.economy().format(item.buy()) + ".");
            return;
        }
        // make sure there's room before charging
        ItemStack give = new ItemStack(item.material(), item.amount());
        if (player.getInventory().firstEmpty() == -1 && !fits(player, give)) {
            Msg.error(player, "Your inventory is full.");
            return;
        }
        plugin.economy().withdraw(player.getUniqueId(), item.buy());
        player.getInventory().addItem(give).values()
                .forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
        Msg.ok(player, "Bought " + item.amount() + "x " + nice(item.material())
                + " for " + plugin.economy().format(item.buy()) + ".");
    }

    public void sell(Player player, ShopItem item) {
        if (!item.canSell()) { Msg.error(player, "That item can't be sold here."); return; }
        int have = count(player, item.material());
        if (have < item.amount()) {
            Msg.error(player, "You need " + item.amount() + "x " + nice(item.material()) + " to sell.");
            return;
        }
        player.getInventory().removeItem(new ItemStack(item.material(), item.amount()));
        plugin.economy().deposit(player.getUniqueId(), item.sell());
        Msg.ok(player, "Sold " + item.amount() + "x " + nice(item.material())
                + " for " + plugin.economy().format(item.sell()) + ".");
    }

    private int count(Player player, Material mat) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == mat) total += it.getAmount();
        }
        return total;
    }

    private boolean fits(Player player, ItemStack give) {
        // rough check: an existing non-full stack of the same type can absorb it
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) return true;
            if (it.isSimilar(give) && it.getAmount() + give.getAmount() <= it.getMaxStackSize()) return true;
        }
        return false;
    }

    private String nice(Material mat) {
        return mat.name().toLowerCase().replace('_', ' ');
    }

    private Material matOr(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
