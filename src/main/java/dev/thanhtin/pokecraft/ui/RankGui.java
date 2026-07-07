package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.rank.RankManager;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Seasonal PvP rank: your tier + points, progress, and this season's top 10. */
public class RankGui implements Listener {
    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public RankGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        int season = plugin.ranks().season();
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 36,
                Component.text("Rank Ladder - Season " + season));
        holder.inventory = inv;

        int points = plugin.ranks().points(player.getUniqueId());
        RankManager.Tier tier = plugin.ranks().tier(points);
        RankManager.Tier next = plugin.ranks().nextTier(points);

        ItemStack you = new ItemStack(Material.DIAMOND);
        ItemMeta youMeta = you.getItemMeta();
        youMeta.displayName(Component.text(tier.name() + " - " + points + " pts", tier.color()));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Win duels to earn rank points", NamedTextColor.GRAY));
        if (next != null) {
            lore.add(Component.text((next.min() - points) + " pts to " + next.name(),
                    NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Top tier reached!", NamedTextColor.LIGHT_PURPLE));
        }
        lore.add(Component.text("Resets each season - climb again!", NamedTextColor.DARK_GRAY));
        youMeta.lore(lore);
        you.setItemMeta(youMeta);
        inv.setItem(4, you);

        List<StorageManager.TopEntry> top = plugin.storage().topRanks(season, 10);
        for (int i = 0; i < top.size() && i < 9; i++) {
            StorageManager.TopEntry entry = top.get(i);
            ItemStack item = new ItemStack(i == 0 ? Material.NETHERITE_INGOT
                    : i == 1 ? Material.GOLD_INGOT : Material.IRON_INGOT);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text((i + 1) + ". " + entry.name(),
                    i == 0 ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text(entry.value() + " pts - "
                    + plugin.ranks().tier((int) entry.value()).name(), NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            inv.setItem(9 + i, item);
        }
        if (top.isEmpty()) {
            ItemStack none = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = none.getItemMeta();
            meta.displayName(Component.text("No ranked players yet - duel to start!",
                    NamedTextColor.GRAY));
            none.setItemMeta(meta);
            inv.setItem(13, none);
        }

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
