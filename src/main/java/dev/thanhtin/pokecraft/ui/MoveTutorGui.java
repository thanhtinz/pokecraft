package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
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

/**
 * Move relearner: teach a party pokemon any move from its species learnset that
 * it is high enough level for, for a fee. If it already knows four moves, you
 * pick one to overwrite. Reuses the level-up learnset data, so no extra config.
 */
public class MoveTutorGui implements Listener {

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyMove;
    private final NamespacedKey keyReplace;

    private static class Holder implements InventoryHolder {
        final int slot;
        final String pendingMove; // null = move list, non-null = replace screen
        Inventory inventory;
        Holder(int slot, String pendingMove) { this.slot = slot; this.pendingMove = pendingMove; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public MoveTutorGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyMove = new NamespacedKey(plugin, "tutor_move");
        this.keyReplace = new NamespacedKey(plugin, "tutor_replace");
    }

    private long cost() {
        return plugin.getConfig().getLong("movetutor.cost", 500);
    }

    // ---------- open ----------

    public void open(Player player, int slot) {
        PlayerParty party = plugin.parties().get(player);
        PokemonInstance p = party.get(slot);
        if (p == null) {
            player.sendMessage(Component.text("No pokemon in that slot.", NamedTextColor.RED));
            return;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null) return;
        List<String> learnable = teachable(p, species);
        if (openForm(player, slot, p, species, learnable)) return;

        Holder holder = new Holder(slot, null);
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text("Move Tutor - " + p.displayName(species)));
        holder.inventory = inv;

        int i = 0;
        for (String moveId : learnable) {
            MoveData m = plugin.species().getMove(moveId);
            if (m == null) continue;
            inv.setItem(i++, moveItem(m, false));
            if (i >= 45) break;
        }
        if (learnable.isEmpty()) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            meta.displayName(Component.text("No new moves to teach", NamedTextColor.RED));
            none.setItemMeta(meta);
            inv.setItem(22, none);
        }
        inv.setItem(49, button(Material.ARROW, "Back", NamedTextColor.GRAY));
        player.openInventory(inv);
    }

    private void openReplace(Player player, int slot, String newMove) {
        PlayerParty party = plugin.parties().get(player);
        PokemonInstance p = party.get(slot);
        if (p == null) return;
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        MoveData nm = plugin.species().getMove(newMove);
        if (species == null || nm == null) return;
        if (openReplaceForm(player, slot, p, species, newMove, nm)) return;

        Holder holder = new Holder(slot, newMove);
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Replace a move with " + nm.name));
        holder.inventory = inv;
        for (int i = 0; i < p.moves.size() && i < 4; i++) {
            MoveData m = plugin.species().getMove(p.moves.get(i));
            if (m == null) continue;
            ItemStack item = moveItem(m, true);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(keyReplace, PersistentDataType.INTEGER, i);
            item.setItemMeta(meta);
            inv.setItem(10 + i, item);
        }
        inv.setItem(26, button(Material.ARROW, "Cancel", NamedTextColor.GRAY));
        player.openInventory(inv);
    }

    // ---------- teach logic (shared by chest + form) ----------

    private List<String> teachable(PokemonInstance p, PokemonSpecies species) {
        List<String> out = new ArrayList<>();
        for (String m : PokemonInstance.learnableMoves(species, p.level)) {
            if (!p.moves.contains(m)) out.add(m);
        }
        return out;
    }

    private void teach(Player player, int slot, String moveId) {
        PlayerParty party = plugin.parties().get(player);
        PokemonInstance p = party.get(slot);
        if (p == null || p.moves.contains(moveId)) return;
        if (p.moves.size() < 4) {
            if (!charge(player)) return;
            p.moves.add(moveId);
            plugin.parties().saveParty(player.getUniqueId());
            taught(player, moveId);
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player, slot));
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> openReplace(player, slot, moveId));
        }
    }

    private void replace(Player player, int slot, String newMove, int index) {
        PlayerParty party = plugin.parties().get(player);
        PokemonInstance p = party.get(slot);
        if (p == null || index < 0 || index >= p.moves.size()) return;
        if (p.moves.contains(newMove)) return;
        if (!charge(player)) return;
        p.moves.set(index, newMove);
        if (p.pp != null) p.pp.remove(newMove); // start the new move at full PP
        plugin.parties().saveParty(player.getUniqueId());
        taught(player, newMove);
        plugin.getServer().getScheduler().runTask(plugin, () -> open(player, slot));
    }

    private boolean charge(Player player) {
        if (!plugin.economy().withdraw(player.getUniqueId(), cost())) {
            player.sendMessage(Component.text("The move tutor charges "
                    + plugin.economy().format(cost()) + " - you can't afford it.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private void taught(Player player, String moveId) {
        MoveData m = plugin.species().getMove(moveId);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        player.sendMessage(Component.text("Taught " + (m != null ? m.name : moveId) + "!",
                NamedTextColor.GREEN));
    }

    // ---------- native Bedrock forms ----------

    private boolean openForm(Player player, int slot, PokemonInstance p,
                             PokemonSpecies species, List<String> learnable) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        for (String moveId : learnable) {
            MoveData m = plugin.species().getMove(moveId);
            if (m == null) continue;
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    m.name + " (" + m.type + " " + m.power + ")",
                    () -> teach(player, slot, moveId)));
        }
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        String content = "Teach a move for " + plugin.economy().format(cost()) + " each.\n"
                + "Current moves: " + String.join(", ", moveNames(p))
                + (learnable.isEmpty() ? "\n\nNo new moves to teach." : "");
        return plugin.bedrock().openForm(player, "Move Tutor - " + p.displayName(species), content, buttons);
    }

    private boolean openReplaceForm(Player player, int slot, PokemonInstance p,
                                    PokemonSpecies species, String newMove, MoveData nm) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        for (int i = 0; i < p.moves.size() && i < 4; i++) {
            MoveData m = plugin.species().getMove(p.moves.get(i));
            final int idx = i;
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    "Forget " + (m != null ? m.name : p.moves.get(i)),
                    () -> replace(player, slot, newMove, idx)));
        }
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                "Cancel", () -> open(player, slot)));
        return plugin.bedrock().openForm(player, "Replace a move with " + nm.name,
                p.displayName(species) + " already knows 4 moves. Pick one to forget.", buttons);
    }

    private List<String> moveNames(PokemonInstance p) {
        List<String> out = new ArrayList<>();
        for (String id : p.moves) {
            MoveData m = plugin.species().getMove(id);
            out.add(m != null ? m.name : id);
        }
        return out;
    }

    // ---------- items ----------

    private ItemStack moveItem(MoveData m, boolean known) {
        ItemStack item = new ItemStack(known ? Material.WRITABLE_BOOK : Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(m.name, NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + m.type + "  Power: " + m.power, NamedTextColor.GRAY));
        lore.add(Component.text("PP " + m.pp + "  Accuracy " + m.accuracy, NamedTextColor.GRAY));
        lore.add(Component.text(known ? "Click to forget this move"
                : "Click to teach (" + plugin.economy().format(cost()) + ")", NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyMove, PersistentDataType.STRING, m.id);
        item.setItemMeta(meta);
        return item;
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
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        int raw = e.getRawSlot();

        if (holder.pendingMove == null) {
            if (raw == 49) { plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.summaryUi().open(player, holder.slot)); return; }
            String moveId = moveIdOf(clicked);
            if (moveId != null) teach(player, holder.slot, moveId);
        } else {
            if (raw == 26) { plugin.getServer().getScheduler().runTask(plugin,
                    () -> open(player, holder.slot)); return; }
            Integer index = clicked != null && clicked.hasItemMeta()
                    ? clicked.getItemMeta().getPersistentDataContainer().get(keyReplace, PersistentDataType.INTEGER)
                    : null;
            if (index != null) replace(player, holder.slot, holder.pendingMove, index);
        }
    }

    private String moveIdOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyMove, PersistentDataType.STRING);
    }
}
