package dev.thanhtin.pokecraft.shop;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.capture.PokeballItem;
import dev.thanhtin.pokecraft.item.UsableItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.function.Function;

/** Pokemart: buy pokeballs, potions and evolution stones with PokeDollars. */
public class ShopGui implements Listener {

    private record Entry(String configKey, long defaultPrice, int amount, String label,
                         Function<PokeCraftPlugin, ItemStack> factory) {}

    private final List<Entry> entries = List.of(
            new Entry("poke-ball", 200, 8, "8x Poke Ball",
                    pl -> pl.pokeballs().create(PokeballItem.BallType.POKE_BALL, 8)),
            new Entry("great-ball", 600, 8, "8x Great Ball",
                    pl -> pl.pokeballs().create(PokeballItem.BallType.GREAT_BALL, 8)),
            new Entry("ultra-ball", 1500, 8, "8x Ultra Ball",
                    pl -> pl.pokeballs().create(PokeballItem.BallType.ULTRA_BALL, 8)),
            new Entry("potion", 150, 1, "Potion",
                    pl -> pl.items().create(UsableItems.ItemType.POTION, 1)),
            new Entry("super-potion", 400, 1, "Super Potion",
                    pl -> pl.items().create(UsableItems.ItemType.SUPER_POTION, 1)),
            new Entry("hyper-potion", 1000, 1, "Hyper Potion",
                    pl -> pl.items().create(UsableItems.ItemType.HYPER_POTION, 1)),
            new Entry("thunder-stone", 3000, 1, "Thunder Stone",
                    pl -> pl.items().create(UsableItems.ItemType.THUNDER_STONE, 1)),
            new Entry("fire-stone", 3000, 1, "Fire Stone",
                    pl -> pl.items().create(UsableItems.ItemType.FIRE_STONE, 1)),
            new Entry("water-stone", 3000, 1, "Water Stone",
                    pl -> pl.items().create(UsableItems.ItemType.WATER_STONE, 1)),
            new Entry("leaf-stone", 3000, 1, "Leaf Stone",
                    pl -> pl.items().create(UsableItems.ItemType.LEAF_STONE, 1)),
            new Entry("moon-stone", 3000, 1, "Moon Stone",
                    pl -> pl.items().create(UsableItems.ItemType.MOON_STONE, 1)));

    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public ShopGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    private long price(Entry entry) {
        return plugin.getConfig().getLong("shop.prices." + entry.configKey(), entry.defaultPrice());
    }

    public void open(Player player) {
        Holder holder = new Holder();
        long balance = plugin.economy().balance(player.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Pokemart - balance " + plugin.economy().format(balance)));
        holder.inventory = inv;

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            ItemStack display = entry.factory().apply(plugin);
            display.setAmount(Math.max(1, Math.min(entry.amount(), 64)));
            ItemMeta meta = display.getItemMeta();
            meta.displayName(Component.text(entry.label(), NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Price: " + plugin.economy().format(price(entry)),
                    balance >= price(entry) ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text("Click to buy", NamedTextColor.GRAY));
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(i + 9, display); // middle row
        }

        ItemStack info = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("Balance: " + plugin.economy().format(balance), NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Earn money by winning battles", NamedTextColor.GRAY)));
        info.setItemMeta(meta);
        inv.setItem(4, info);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        int raw = e.getRawSlot();
        int index = raw - 9;
        if (index < 0 || index >= entries.size()) return;
        Entry entry = entries.get(index);
        long cost = price(entry);
        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Not enough money (need "
                    + plugin.economy().format(cost) + ").", NamedTextColor.RED));
            return;
        }
        var leftovers = player.getInventory().addItem(entry.factory().apply(plugin));
        leftovers.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        player.sendMessage(Component.text("Bought " + entry.label() + " for "
                + plugin.economy().format(cost) + ".", NamedTextColor.GREEN));
        plugin.getServer().getScheduler().runTask(plugin, () -> open(player)); // refresh balance
    }
}
