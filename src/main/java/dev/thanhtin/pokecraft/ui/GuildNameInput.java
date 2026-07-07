package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Anvil text input for a new guild name (works on PC + Bedrock via Geyser). */
public class GuildNameInput implements Listener {
    private static final int RESULT_SLOT = 2;

    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public GuildNameInput(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, InventoryType.ANVIL,
                Component.text("Name your guild"));
        holder.inventory = inv;
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text("Guild name"));
        meta.lore(List.of(Component.text("Type a name, then click the result",
                NamedTextColor.GRAY)));
        paper.setItemMeta(meta);
        inv.setItem(0, paper);
        player.openInventory(inv);
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        String text = e.getInventory().getRenameText();
        ItemStack result = new ItemStack(Material.PAPER);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(text == null || text.isBlank() ? "..." : text));
        result.setItemMeta(meta);
        e.setResult(result);
        e.getInventory().setRepairCost(0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() != RESULT_SLOT) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;
        String name = ((AnvilInventory) e.getInventory()).getRenameText();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (name != null && !name.isBlank()) plugin.guilds().create(player, name);
            plugin.guildUi().open(player);
        });
    }
}
