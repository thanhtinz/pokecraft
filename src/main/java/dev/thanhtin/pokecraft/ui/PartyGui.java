package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PartyGui implements Listener {
    public static final Component TITLE = Component.text("Your Party");
    private final PokeCraftPlugin plugin;

    public PartyGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerParty party = plugin.parties().get(player);
        Inventory inv = plugin.getServer().createInventory(null, 9, TITLE);
        PokemonInstance[] slots = party.rawSlots();
        for (int i = 0; i < slots.length; i++) {
            PokemonInstance p = slots[i];
            if (p == null) {
                ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = empty.getItemMeta();
                meta.displayName(Component.text("Empty slot", NamedTextColor.DARK_GRAY));
                empty.setItemMeta(meta);
                inv.setItem(i, empty);
                continue;
            }
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            ItemStack item = new ItemStack(p.shiny ? Material.NETHER_STAR : Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(p.displayName(species) + " Lv." + p.level,
                    p.shiny ? NamedTextColor.GOLD : NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("HP " + p.currentHp + "/" + p.maxHp(species), NamedTextColor.GREEN));
            lore.add(Component.text("Type: " + species.types, NamedTextColor.GRAY));
            lore.add(Component.text("Nature: " + p.nature, NamedTextColor.GRAY));
            lore.add(Component.text("Moves: " + String.join(", ", p.moves), NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().title().equals(TITLE)) e.setCancelled(true);
    }
}
