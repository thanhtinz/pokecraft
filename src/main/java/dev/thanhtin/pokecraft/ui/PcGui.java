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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Paginated PC box viewer. Clicking a pokemon withdraws it into the party. */
public class PcGui implements Listener {
    private static final int PAGE_SIZE = 45;

    private final PokeCraftPlugin plugin;

    public PcGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    private static class Holder implements InventoryHolder {
        final int page;
        Inventory inventory;
        Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public void open(Player player, int page) {
        PlayerParty party = plugin.parties().get(player);
        List<PokemonInstance> pc = party.pc();
        int pages = Math.max(1, (pc.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));

        Holder holder = new Holder(current);
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text("PC Box - page " + (current + 1) + "/" + pages
                        + " (" + pc.size() + " stored)"));
        holder.inventory = inv;

        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < pc.size(); i++) {
            PokemonInstance p = pc.get(start + i);
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            if (species == null) continue;
            ItemStack item = new ItemStack(p.shiny ? Material.NETHER_STAR : Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(p.displayName(species) + " Lv." + p.level,
                    p.shiny ? NamedTextColor.GOLD : NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("HP " + p.currentHp + "/" + p.maxHp(species), NamedTextColor.GREEN));
            lore.add(Component.text("Type: " + species.types, NamedTextColor.GRAY));
            lore.add(Component.text("Click to move to your party", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        if (current > 0) inv.setItem(45, button(Material.ARROW, "Previous page"));
        inv.setItem(49, button(Material.PAPER, "Party: " + party.partySize() + "/" + PlayerParty.SIZE));
        if (current < pages - 1) inv.setItem(53, button(Material.ARROW, "Next page"));

        player.openInventory(inv);
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
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) return;
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= 54) return;

        if (raw == 45 && holder.page > 0) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.page - 1));
            return;
        }
        if (raw == 53) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.page + 1));
            return;
        }
        if (raw >= PAGE_SIZE) return;

        PlayerParty party = plugin.parties().get(player);
        int pcIndex = holder.page * PAGE_SIZE + raw;
        if (pcIndex >= party.pc().size()) return;
        PokemonInstance p = party.pc().get(pcIndex);
        int slot = party.withdrawFromPc(pcIndex);
        if (slot < 0) {
            player.sendMessage(Component.text("Your party is full. Deposit one first (/poke party).",
                    NamedTextColor.RED));
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        player.sendMessage(Component.text(p.displayName(species) + " moved to party slot " + (slot + 1) + ".",
                NamedTextColor.GREEN));
        plugin.parties().saveParty(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> open(player, holder.page));
    }
}
