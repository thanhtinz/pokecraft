package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
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

/** Leaderboards panel: each category shows its top 10 in the item lore. */
public class LeaderboardGui implements Listener {

    private record Category(int slot, Material material, String title, String column, boolean money) {}

    private static final List<Category> CATEGORIES = List.of(
            new Category(10, Material.SNOWBALL, "Top Catchers", "caught", false),
            new Category(12, Material.GOLD_INGOT, "Richest Trainers", "balance", true),
            new Category(14, Material.IRON_SWORD, "Wild Battle Wins", "wild_wins", false),
            new Category(16, Material.DIAMOND_SWORD, "Best Duelists", "pvp_wins", false));

    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public LeaderboardGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (openForm(player)) return;
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Leaderboards"));
        holder.inventory = inv;

        for (Category category : CATEGORIES) {
            ItemStack item = new ItemStack(category.material());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(category.title(), NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            List<StorageManager.TopEntry> entries = plugin.storage().top(category.column(), 10);
            if (entries.isEmpty()) {
                lore.add(Component.text("No entries yet", NamedTextColor.DARK_GRAY));
            }
            for (int i = 0; i < entries.size(); i++) {
                StorageManager.TopEntry entry = entries.get(i);
                String value = category.money()
                        ? plugin.economy().format(entry.value())
                        : String.valueOf(entry.value());
                lore.add(Component.text((i + 1) + ". " + entry.name() + " - " + value,
                        i == 0 ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(category.slot(), item);
        }
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private boolean openForm(Player player) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        StringBuilder sb = new StringBuilder();
        for (Category category : CATEGORIES) {
            sb.append("§6").append(category.title()).append("§r\n");
            List<StorageManager.TopEntry> entries = plugin.storage().top(category.column(), 10);
            if (entries.isEmpty()) sb.append("§8No entries yet\n");
            for (int i = 0; i < entries.size(); i++) {
                StorageManager.TopEntry entry = entries.get(i);
                String value = category.money()
                        ? plugin.economy().format(entry.value()) : String.valueOf(entry.value());
                sb.append(i + 1).append(". ").append(entry.name()).append(" - ").append(value).append("\n");
            }
            sb.append("\n");
        }
        return plugin.bedrock().openForm(player, "Leaderboards", sb.toString(),
                List.of(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null)));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
