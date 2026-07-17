package dev.thanhtin.survivalcore.auction;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database.Auction;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Player market: list the held item for a price, others buy it from a GUI. */
public class AuctionManager {

    private final SurvivalCore plugin;

    public AuctionManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    public String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public ItemStack decode(String base64) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
        } catch (Throwable t) {
            return null;
        }
    }

    public void sell(Player seller, double price) {
        double min = plugin.getConfig().getDouble("auction.min-price", 1.0);
        double max = plugin.getConfig().getDouble("auction.max-price", 1.0E9);
        if (price < min || price > max) {
            Msg.error(seller, "Price must be between " + plugin.economy().format(min)
                    + " and " + plugin.economy().format(max) + ".");
            return;
        }
        int limit = plugin.getConfig().getInt("auction.max-listings-per-player", 10);
        if (plugin.db().auctionsBySeller(seller.getUniqueId()).size() >= limit) {
            Msg.error(seller, "You already have " + limit + " listings.");
            return;
        }
        ItemStack item = seller.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            Msg.error(seller, "Hold the item you want to sell.");
            return;
        }
        long id = plugin.db().addAuction(seller.getUniqueId(), seller.getName(), encode(item), price);
        if (id < 0) { Msg.error(seller, "Could not list the item."); return; }
        seller.getInventory().setItemInMainHand(null);
        Msg.ok(seller, "Listed " + item.getAmount() + "x " + niceName(item.getType().name())
                + " for " + plugin.economy().format(price) + ".");
    }

    public void buy(Player buyer, long id) {
        Auction a = plugin.db().getAuction(id);
        if (a == null) { Msg.error(buyer, "That listing is gone."); return; }
        if (a.seller().equals(buyer.getUniqueId())) {
            Msg.error(buyer, "That's your own listing - use cancel to take it back.");
            return;
        }
        if (!plugin.economy().has(buyer.getUniqueId(), a.price())) {
            Msg.error(buyer, "You can't afford " + plugin.economy().format(a.price()) + ".");
            return;
        }
        // remove first: guards against two buyers racing the same listing
        if (!plugin.db().removeAuction(id)) { Msg.error(buyer, "Someone else bought it."); return; }
        ItemStack item = decode(a.itemBase64());
        if (item == null) { Msg.error(buyer, "This listing was corrupted."); return; }
        plugin.economy().withdraw(buyer.getUniqueId(), a.price());
        plugin.economy().deposit(a.seller(), a.price());
        giveOrDrop(buyer, item);
        Msg.ok(buyer, "Bought " + niceName(item.getType().name()) + " for "
                + plugin.economy().format(a.price()) + ".");
        Player seller = plugin.getServer().getPlayer(a.seller());
        if (seller != null) {
            Msg.ok(seller, buyer.getName() + " bought your " + niceName(item.getType().name())
                    + " for " + plugin.economy().format(a.price()) + ".");
        }
    }

    public void cancel(Player seller, long id) {
        Auction a = plugin.db().getAuction(id);
        if (a == null || !a.seller().equals(seller.getUniqueId())) {
            Msg.error(seller, "That isn't your listing.");
            return;
        }
        if (!plugin.db().removeAuction(id)) { Msg.error(seller, "Already gone."); return; }
        ItemStack item = decode(a.itemBase64());
        if (item != null) giveOrDrop(seller, item);
        Msg.ok(seller, "Listing cancelled, item returned.");
    }

    private void giveOrDrop(Player p, ItemStack item) {
        var leftover = p.getInventory().addItem(item);
        leftover.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    public String niceName(String material) {
        String s = material.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
