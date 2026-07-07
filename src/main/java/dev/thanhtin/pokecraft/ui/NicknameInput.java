package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
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

/**
 * Text input via an anvil - Geyser translates the rename box to a native
 * Bedrock text prompt, so it works on both PC and mobile with no commands.
 * Used to set a pokemon's nickname from its Summary screen.
 */
public class NicknameInput implements Listener {
    private static final int RESULT_SLOT = 2;

    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        final int partySlot;
        Inventory inventory;
        Holder(int partySlot) { this.partySlot = partySlot; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public NicknameInput(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int partySlot) {
        PokemonInstance p = plugin.parties().get(player).get(partySlot);
        if (p == null) {
            plugin.partyUi().open(player);
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        String current = p.nickname != null && !p.nickname.isEmpty()
                ? p.nickname : (species != null ? species.name : p.speciesId);
        if (plugin.bedrock().openInputForm(player, "Type a nickname",
                "Name (blank or 'off' to clear)", current,
                text -> applyNickname(player, partySlot, text))) return;
        Holder holder = new Holder(partySlot);
        Inventory inv = plugin.getServer().createInventory(holder, InventoryType.ANVIL,
                Component.text("Type a nickname"));
        holder.inventory = inv;
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text(current));
        meta.lore(List.of(Component.text("Type a name, then click the result",
                NamedTextColor.GRAY)));
        paper.setItemMeta(meta);
        inv.setItem(0, paper);
        player.openInventory(inv);
    }

    /** Provide a costless result item so the output slot is always clickable. */
    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        String text = e.getInventory().getRenameText();
        ItemStack result = new ItemStack(Material.PAPER);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(text == null || text.isBlank() ? "off" : text));
        result.setItemMeta(meta);
        e.setResult(result);
        e.getInventory().setRepairCost(0);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (e.getRawSlot() != RESULT_SLOT) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        AnvilInventory anvil = (AnvilInventory) e.getInventory();
        applyNickname(player, holder.partySlot, anvil.getRenameText());
    }

    /** Commit logic shared by the anvil GUI and the native Bedrock input form. */
    private void applyNickname(Player player, int partySlot, String raw) {
        if (raw == null) raw = "";
        raw = raw.trim();
        if (raw.length() > 24) raw = raw.substring(0, 24);

        PokemonInstance p = plugin.parties().get(player).get(partySlot);
        if (p != null) {
            p.nickname = raw.isEmpty() || raw.equalsIgnoreCase("off") ? null : raw;
            plugin.parties().saveParty(player.getUniqueId());
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            player.sendMessage(Component.text("Nickname set: "
                    + (species != null ? p.displayName(species) : raw), NamedTextColor.GREEN));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.summaryUi().open(player, partySlot));
    }
}
