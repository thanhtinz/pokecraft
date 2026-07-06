package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.species.PokemonType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.List;

/** Picks a party pokemon to ride. */
public class RidePickerGui implements Listener {
    private final PokeCraftPlugin plugin;
    private final NamespacedKey keySlot;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public RidePickerGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keySlot = new NamespacedKey(plugin, "ride_slot");
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 9,
                Component.text("Ride which pokemon?"));
        holder.inventory = inv;
        PlayerParty party = plugin.parties().get(player);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            if (species == null) continue;
            boolean usable = p.currentHp > 0;
            boolean flying = species.types != null && species.types.contains(PokemonType.FLYING);
            ItemStack item = new ItemStack(usable
                    ? (flying ? Material.FEATHER : Material.SADDLE) : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(p.displayName(species) + " Lv." + p.level,
                    usable ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
            meta.lore(List.of(Component.text(usable
                    ? (flying ? "Can fly! Sneak to dismount" : "Sneak to dismount")
                    : "Fainted - heal it first", NamedTextColor.GRAY)));
            if (usable) meta.getPersistentDataContainer().set(keySlot, PersistentDataType.INTEGER, i);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        Integer slot = item.getItemMeta().getPersistentDataContainer()
                .get(keySlot, PersistentDataType.INTEGER);
        if (slot == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            plugin.rides().ride(player, slot);
        });
    }
}
