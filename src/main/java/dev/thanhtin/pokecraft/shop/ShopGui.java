package dev.thanhtin.pokecraft.shop;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.capture.PokeballItem;
import dev.thanhtin.pokecraft.item.HeldItems;
import dev.thanhtin.pokecraft.ui.GuiFiller;
import dev.thanhtin.pokecraft.item.UsableItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
            new Entry("full-heal", 300, 1, "Full Heal",
                    pl -> pl.items().create(UsableItems.ItemType.FULL_HEAL, 1)),
            new Entry("thunder-stone", 3000, 1, "Thunder Stone",
                    pl -> pl.items().create(UsableItems.ItemType.THUNDER_STONE, 1)),
            new Entry("fire-stone", 3000, 1, "Fire Stone",
                    pl -> pl.items().create(UsableItems.ItemType.FIRE_STONE, 1)),
            new Entry("water-stone", 3000, 1, "Water Stone",
                    pl -> pl.items().create(UsableItems.ItemType.WATER_STONE, 1)),
            new Entry("leaf-stone", 3000, 1, "Leaf Stone",
                    pl -> pl.items().create(UsableItems.ItemType.LEAF_STONE, 1)),
            new Entry("moon-stone", 3000, 1, "Moon Stone",
                    pl -> pl.items().create(UsableItems.ItemType.MOON_STONE, 1)),
            new Entry("leftovers", 4000, 1, "Leftovers",
                    pl -> pl.heldItems().create(HeldItems.HeldType.LEFTOVERS, 1)),
            new Entry("muscle-band", 3000, 1, "Muscle Band",
                    pl -> pl.heldItems().create(HeldItems.HeldType.MUSCLE_BAND, 1)),
            new Entry("wise-glasses", 3000, 1, "Wise Glasses",
                    pl -> pl.heldItems().create(HeldItems.HeldType.WISE_GLASSES, 1)),
            new Entry("quick-claw", 2500, 1, "Quick Claw",
                    pl -> pl.heldItems().create(HeldItems.HeldType.QUICK_CLAW, 1)),
            new Entry("lucky-egg", 5000, 1, "Lucky Egg",
                    pl -> pl.heldItems().create(HeldItems.HeldType.LUCKY_EGG, 1)),
            new Entry("everstone", 1000, 1, "Everstone",
                    pl -> pl.heldItems().create(HeldItems.HeldType.EVERSTONE, 1)),
            new Entry("focus-band", 3000, 1, "Focus Band",
                    pl -> pl.heldItems().create(HeldItems.HeldType.FOCUS_BAND, 1)),
            new Entry("berry-seed", 100, 3, "3x Berry Seed",
                    pl -> pl.farms().createSeed(3)));

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyAction;
    private final NamespacedKey keySellSlot;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    /** Marks the sell screen so its clicks route to the sell handler. */
    private static class SellHolder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public ShopGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "shop_action");
        this.keySellSlot = new NamespacedKey(plugin, "shop_sell_slot");
    }

    private long price(Entry entry) {
        return plugin.getConfig().getLong("shop.prices." + entry.configKey(), entry.defaultPrice());
    }

    public void open(Player player) {
        Holder holder = new Holder();
        long balance = plugin.economy().balance(player.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(holder, 36,
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

        ItemStack sell = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sellMeta = sell.getItemMeta();
        sellMeta.displayName(Component.text("Sell items", NamedTextColor.YELLOW));
        sellMeta.lore(List.of(Component.text("Turn your spare items into money", NamedTextColor.GRAY)));
        sellMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "sell");
        sell.setItemMeta(sellMeta);
        inv.setItem(0, sell);

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    /** Sell screen: lists the sellable items the player is carrying. */
    public void openSell(Player player) {
        SellHolder holder = new SellHolder();
        long balance = plugin.economy().balance(player.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text("Sell - balance " + plugin.economy().format(balance)));
        holder.inventory = inv;

        ItemStack[] contents = player.getInventory().getContents();
        int shown = 0;
        for (int slot = 0; slot < contents.length && shown < 45; slot++) {
            ItemStack item = contents[slot];
            long unit = sellPrice(item);
            if (unit <= 0) continue;
            ItemStack display = item.clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text("Sell 1 for " + plugin.economy().format(unit), NamedTextColor.GREEN));
            lore.add(Component.text("Click to sell one", NamedTextColor.GRAY));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(keySellSlot, PersistentDataType.INTEGER, slot);
            display.setItemMeta(meta);
            inv.setItem(shown++, display);
        }
        if (shown == 0) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.displayName(Component.text("Nothing to sell", NamedTextColor.RED));
            none.setItemMeta(meta);
            inv.setItem(22, none);
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back to Pokemart", NamedTextColor.GRAY));
        backMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "buy");
        back.setItemMeta(backMeta);
        inv.setItem(53, back);

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    /** Money paid for one unit of an item, or 0 if it can't be sold. */
    private long sellPrice(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;
        PokeballItem.BallType ball = plugin.pokeballs().read(item);
        if (ball != null) {
            return switch (ball) {
                case POKE_BALL -> 25;
                case GREAT_BALL -> 75;
                case ULTRA_BALL -> 190;
                default -> 0; // Master Ball not sellable
            };
        }
        UsableItems.ItemType use = plugin.items().read(item);
        if (use != null) {
            return switch (use) {
                case POTION -> 10;
                case SUPER_POTION -> 30;
                case HYPER_POTION -> 75;
                case ORAN_BERRY -> 15;
                case FULL_HEAL -> 150;
                default -> 375; // evolution stones
            };
        }
        if (plugin.heldItems().read(item) != null) return 300;
        return 0;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        boolean buyScreen = e.getInventory().getHolder() instanceof Holder;
        boolean sellScreen = e.getInventory().getHolder() instanceof SellHolder;
        if (!buyScreen && !sellScreen) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = e.getCurrentItem();
        String action = clicked != null && clicked.hasItemMeta()
                ? clicked.getItemMeta().getPersistentDataContainer().get(keyAction, PersistentDataType.STRING)
                : null;
        if ("sell".equals(action)) { plugin.getServer().getScheduler().runTask(plugin, () -> openSell(player)); return; }
        if ("buy".equals(action)) { plugin.getServer().getScheduler().runTask(plugin, () -> open(player)); return; }

        if (sellScreen) { handleSell(player, clicked); return; }

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

    private void handleSell(Player player, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        Integer slot = clicked.getItemMeta().getPersistentDataContainer()
                .get(keySellSlot, PersistentDataType.INTEGER);
        if (slot == null) return;
        ItemStack actual = player.getInventory().getItem(slot);
        long unit = sellPrice(actual);
        if (actual == null || unit <= 0) { // inventory changed under us
            plugin.getServer().getScheduler().runTask(plugin, () -> openSell(player));
            return;
        }
        if (actual.getAmount() <= 1) player.getInventory().setItem(slot, null);
        else actual.setAmount(actual.getAmount() - 1);
        plugin.economy().deposit(player.getUniqueId(), unit);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        player.sendMessage(Component.text("Sold 1 item for " + plugin.economy().format(unit) + ".",
                NamedTextColor.GREEN));
        plugin.getServer().getScheduler().runTask(plugin, () -> openSell(player));
    }
}
