package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pokedex: per-player seen/caught progress over every registered species,
 * in dex order. Caught entries show full stats; seen entries show the name;
 * everything else is a "???" silhouette.
 */
public class PokedexGui implements Listener {
    private static final int PAGE_SIZE = 45;

    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        final int page;
        Inventory inventory;
        Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public PokedexGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<PokemonSpecies> all = new ArrayList<>(plugin.species().all());
        all.sort(Comparator.comparingInt(s -> s.dex));
        Map<String, Boolean> dex = plugin.storage().pokedexOf(player.getUniqueId());
        long caught = dex.values().stream().filter(Boolean::booleanValue).count();

        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder(current);
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text("Pokedex " + caught + "/" + all.size()
                        + " - page " + (current + 1) + "/" + pages));
        holder.inventory = inv;

        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            PokemonSpecies species = all.get(start + i);
            Boolean state = dex.get(species.id); // null = unseen, false = seen, true = caught
            inv.setItem(i, entry(species, state));
        }

        if (current > 0) inv.setItem(45, button(Material.ARROW, "Previous page"));
        inv.setItem(49, button(Material.PAPER, "Caught " + caught + " - seen "
                + dex.size() + " of " + all.size()));
        if (current < pages - 1) inv.setItem(53, button(Material.ARROW, "Next page"));
        player.openInventory(inv);
    }

    private ItemStack entry(PokemonSpecies species, Boolean state) {
        String number = "#" + String.format("%03d", species.dex) + " ";
        if (state == null) {
            ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(number + "???", NamedTextColor.DARK_GRAY));
            item.setItemMeta(meta);
            return item;
        }
        if (!state) {
            ItemStack item = new ItemStack(Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(number + species.name, NamedTextColor.GRAY));
            meta.lore(List.of(Component.text("Seen - not caught yet", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            return item;
        }
        ItemStack item = new ItemStack(Material.POPPED_CHORUS_FRUIT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(number + species.name, NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + species.types, NamedTextColor.GRAY));
        lore.add(Component.text("HP " + species.baseStats.hp + "  Atk " + species.baseStats.atk
                + "  Def " + species.baseStats.def, NamedTextColor.GRAY));
        lore.add(Component.text("SpA " + species.baseStats.spa + "  SpD " + species.baseStats.spd
                + "  Spe " + species.baseStats.spe, NamedTextColor.GRAY));
        for (PokemonSpecies.Evolution evo : species.allEvolutions()) {
            PokemonSpecies to = plugin.species().getSpecies(evo.to);
            if (to == null) continue;
            lore.add(Component.text("Evolves into " + to.name
                    + (evo.item != null ? " (" + evo.item.replace('_', ' ') + ")"
                    : " at Lv." + evo.level), NamedTextColor.LIGHT_PURPLE));
        }
        if (species.spawn != null && species.spawn.biomes != null) {
            lore.add(Component.text("Wild: " + String.join(", ", species.spawn.biomes)
                    .toLowerCase().replace('_', ' '), NamedTextColor.DARK_AQUA));
        }
        lore.add(Component.text("Caught!", NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String label) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        int raw = e.getRawSlot();
        if (raw == 45 && holder.page > 0) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.page - 1));
        } else if (raw == 53) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.page + 1));
        }
    }
}
