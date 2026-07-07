package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.ExperienceCurve;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Party viewer/manager: click one pokemon then another slot to reorder,
 * deposit the selected pokemon to the PC, or jump to the PC box.
 */
public class PartyGui implements Listener {
    private static final int SLOT_DEPOSIT = 18;
    private static final int SLOT_DETAILS = 20;
    private static final int SLOT_INFO = 22;
    private static final int SLOT_PC = 26;

    private final PokeCraftPlugin plugin;
    private final Map<UUID, Integer> selected = new ConcurrentHashMap<>();

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public PartyGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerParty party = plugin.parties().get(player);
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27, Component.text("Your Party"));
        holder.inventory = inv;
        Integer sel = selected.get(player.getUniqueId());

        PokemonInstance[] slots = party.rawSlots();
        for (int i = 0; i < slots.length; i++) {
            PokemonInstance p = slots[i];
            if (p == null) {
                ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = empty.getItemMeta();
                meta.displayName(Component.text(sel != null ? "Move here" : "Empty slot",
                        sel != null ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
                empty.setItemMeta(meta);
                inv.setItem(i, empty);
                continue;
            }
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            boolean isSelected = sel != null && sel == i;
            ItemStack item = new ItemStack(isSelected ? Material.LIME_STAINED_GLASS
                    : p.shiny ? Material.NETHER_STAR : Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text((isSelected ? "> " : "")
                            + (p.status != null ? "[" + p.status.tag + "] " : "")
                            + p.displayName(species) + " Lv." + p.level,
                    isSelected ? NamedTextColor.GREEN : p.shiny ? NamedTextColor.GOLD : NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("HP " + p.currentHp + "/" + p.maxHp(species),
                    p.currentHp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text("Type: " + species.types + "  Nature: " + p.nature, NamedTextColor.GRAY));
            long next = ExperienceCurve.expForLevel(Math.min(p.level + 1, ExperienceCurve.MAX_LEVEL));
            lore.add(Component.text("EXP " + p.exp + " / next Lv " + next, NamedTextColor.GRAY));
            for (String moveId : p.moves) {
                MoveData m = plugin.species().getMove(moveId);
                if (m == null) continue;
                lore.add(Component.text(m.name + "  PP " + p.ppFor(m) + "/" + m.pp, NamedTextColor.DARK_AQUA));
            }
            lore.add(Component.text(isSelected ? "Click another slot to move/swap"
                    : "Click to select", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        inv.setItem(SLOT_DEPOSIT, button(Material.CHEST_MINECART,
                sel != null ? "Deposit selected to PC" : "Deposit: select a pokemon first",
                sel != null ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        inv.setItem(SLOT_DETAILS, button(Material.BOOK,
                sel != null ? "View details of selected" : "Details: select a pokemon first",
                sel != null ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        inv.setItem(SLOT_INFO, button(Material.PAPER,
                "PC stores " + party.pc().size() + " pokemon", NamedTextColor.GRAY));
        inv.setItem(SLOT_PC, button(Material.ENDER_CHEST, "Open PC Box", NamedTextColor.YELLOW));

        player.openInventory(inv);
    }

    private ItemStack button(Material material, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) return;
        int raw = e.getRawSlot();
        UUID id = player.getUniqueId();
        PlayerParty party = plugin.parties().get(player);
        Integer sel = selected.get(id);

        if (raw >= 0 && raw < PlayerParty.SIZE) {
            if (sel == null) {
                if (party.get(raw) != null) selected.put(id, raw);
            } else if (sel == raw) {
                selected.remove(id);
            } else {
                party.swap(sel, raw);
                selected.remove(id);
                plugin.parties().saveParty(id);
            }
            refresh(player);
            return;
        }

        if (raw == SLOT_DEPOSIT && sel != null) {
            if (party.partySize() <= 1) {
                player.sendMessage(Component.text("You must keep at least one pokemon in your party.",
                        NamedTextColor.RED));
                selected.remove(id);
                refresh(player);
                return;
            }
            PokemonInstance moved = party.depositToPc(sel);
            selected.remove(id);
            if (moved != null) {
                PokemonSpecies species = plugin.species().getSpecies(moved.speciesId);
                player.sendMessage(Component.text(moved.displayName(species) + " was sent to the PC.",
                        NamedTextColor.GREEN));
                plugin.parties().saveParty(id);
            }
            refresh(player);
            return;
        }

        if (raw == SLOT_DETAILS && sel != null) {
            final int detailSlot = sel;
            selected.remove(id);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.summaryUi().open(player, detailSlot));
            return;
        }

        if (raw == SLOT_PC) {
            selected.remove(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.pcUi().open(player, 0));
        }
    }

    private void refresh(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> open(player));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        // a refresh reopens the GUI next tick - only clear when it stays closed
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(e.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                selected.remove(e.getPlayer().getUniqueId());
            }
        });
    }
}
