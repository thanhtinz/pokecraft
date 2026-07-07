package dev.thanhtin.pokecraft.minigame;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import dev.thanhtin.pokecraft.ui.GuiFiller;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Casino: coin flip (double or nothing) and a 3-reel slot machine. */
public class CasinoGui implements Listener {
    private static final long[] BETS = {100, 500, 1000, 5000};
    private static final Material[] REELS = {
            Material.CHERRY_LEAVES, Material.GOLD_INGOT, Material.DIAMOND,
            Material.EMERALD, Material.NETHER_STAR};
    private static final String[] REEL_NAMES = {"Cherry", "Gold", "Diamond", "Emerald", "Star"};

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyFlip;
    private final NamespacedKey keySlot;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public CasinoGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyFlip = new NamespacedKey(plugin, "casino_flip");
        this.keySlot = new NamespacedKey(plugin, "casino_slot");
    }

    public void open(Player player) {
        Holder holder = new Holder();
        long balance = plugin.economy().balance(player.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Casino - balance " + plugin.economy().format(balance)));
        holder.inventory = inv;

        // coin flip bets (top row)
        int slot = 10;
        for (long bet : BETS) {
            ItemStack item = new ItemStack(Material.SUNFLOWER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Coin flip " + plugin.economy().format(bet),
                    NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("50% to double, 50% to lose it",
                    NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keyFlip, PersistentDataType.LONG, bet);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        // slot machine (bottom)
        long spin = plugin.getConfig().getLong("casino.slot-cost", 500);
        ItemStack slots = new ItemStack(Material.CHEST);
        ItemMeta sMeta = slots.getItemMeta();
        sMeta.displayName(Component.text("Slot machine - " + plugin.economy().format(spin) + "/spin",
                NamedTextColor.LIGHT_PURPLE));
        sMeta.lore(List.of(
                Component.text("3 match = x10, 2 match = x2", NamedTextColor.GRAY),
                Component.text("Click to spin", NamedTextColor.YELLOW)));
        sMeta.getPersistentDataContainer().set(keySlot, PersistentDataType.LONG, spin);
        slots.setItemMeta(sMeta);
        inv.setItem(22, slots);

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        Long flip = pdc.get(keyFlip, PersistentDataType.LONG);
        Long slot = pdc.get(keySlot, PersistentDataType.LONG);

        if (flip != null) coinFlip(player, flip);
        else if (slot != null) slots(player, slot);
        if (flip != null || slot != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player));
        }
    }

    private void coinFlip(Player player, long bet) {
        if (!plugin.economy().withdraw(player.getUniqueId(), bet)) {
            player.sendMessage(Component.text("Not enough money.", NamedTextColor.RED));
            return;
        }
        if (ThreadLocalRandom.current().nextBoolean()) {
            plugin.economy().deposit(player.getUniqueId(), bet * 2);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            player.sendMessage(Component.text("Heads! You won " + plugin.economy().format(bet) + "!",
                    NamedTextColor.GREEN));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
            player.sendMessage(Component.text("Tails... you lost " + plugin.economy().format(bet) + ".",
                    NamedTextColor.RED));
        }
    }

    private void slots(Player player, long cost) {
        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Not enough money.", NamedTextColor.RED));
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int a = rnd.nextInt(REELS.length), b = rnd.nextInt(REELS.length), c = rnd.nextInt(REELS.length);
        String line = REEL_NAMES[a] + " | " + REEL_NAMES[b] + " | " + REEL_NAMES[c];
        long payout;
        if (a == b && b == c) payout = cost * 10;
        else if (a == b || b == c || a == c) payout = cost * 2;
        else payout = 0;
        if (payout > 0) {
            plugin.economy().deposit(player.getUniqueId(), payout);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            player.sendMessage(Component.text("[" + line + "] You won "
                    + plugin.economy().format(payout) + "!", NamedTextColor.GREEN));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            player.sendMessage(Component.text("[" + line + "] No match.", NamedTextColor.GRAY));
        }
    }
}
