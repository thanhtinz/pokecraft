package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.trade.TradeSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Trade screen: top row is your party (click to offer), the middle shows both
 * offers and confirm state, and the bottom has Confirm / Cancel.
 */
public class TradeGui implements Listener {
    private static final int SLOT_YOUR_OFFER = 20;
    private static final int SLOT_STATUS = 22;
    private static final int SLOT_THEIR_OFFER = 24;
    private static final int SLOT_CONFIRM = 29;
    private static final int SLOT_CANCEL = 33;

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keySlot;
    private final NamespacedKey keyAction;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public TradeGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keySlot = new NamespacedKey(plugin, "trade_slot");
        this.keyAction = new NamespacedKey(plugin, "trade_action");
    }

    public void open(Player player, TradeSession session) {
        UUID id = player.getUniqueId();
        Player other = plugin.getServer().getPlayer(session.other(id));
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 36,
                Component.text("Trade with " + (other != null ? other.getName() : "?")));
        holder.inventory = inv;

        // top row: your party, click to offer
        PlayerParty party = plugin.parties().get(player);
        UUID yourOffer = session.offerOf(id);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            if (species == null) continue;
            boolean offered = p.uuid.equals(yourOffer);
            ItemStack item = new ItemStack(offered ? Material.LIME_STAINED_GLASS
                    : p.shiny ? Material.NETHER_STAR : Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text((offered ? "> " : "") + p.displayName(species) + " Lv." + p.level,
                    offered ? NamedTextColor.GREEN : NamedTextColor.AQUA));
            meta.lore(List.of(Component.text(offered ? "Offering - click to remove" : "Click to offer",
                    NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer().set(keySlot, PersistentDataType.INTEGER, i);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        inv.setItem(SLOT_YOUR_OFFER, offerItem("Your offer", session.offerOf(id), player,
                session.confirmedBy(id)));
        inv.setItem(SLOT_THEIR_OFFER, offerItem(
                (other != null ? other.getName() : "Their") + " offer",
                session.offerOf(session.other(id)), other, session.confirmedBy(session.other(id))));

        boolean theirConfirm = session.confirmedBy(session.other(id));
        ItemStack status = new ItemStack(theirConfirm ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta statusMeta = status.getItemMeta();
        statusMeta.displayName(Component.text(theirConfirm
                ? "Other player has confirmed" : "Waiting for the other player",
                theirConfirm ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        status.setItemMeta(statusMeta);
        inv.setItem(SLOT_STATUS, status);

        boolean canConfirm = session.bothOffered();
        ItemStack confirm = new ItemStack(session.confirmedBy(id) ? Material.EMERALD_BLOCK
                : canConfirm ? Material.EMERALD : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(Component.text(session.confirmedBy(id) ? "Confirmed (waiting)"
                : canConfirm ? "Confirm trade" : "Both must offer first",
                session.confirmedBy(id) ? NamedTextColor.GRAY
                        : canConfirm ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        if (canConfirm && !session.confirmedBy(id)) {
            confirmMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "confirm");
        }
        confirm.setItemMeta(confirmMeta);
        inv.setItem(SLOT_CONFIRM, confirm);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("Cancel trade", NamedTextColor.RED));
        cancelMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "cancel");
        cancel.setItemMeta(cancelMeta);
        inv.setItem(SLOT_CANCEL, cancel);

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private ItemStack offerItem(String label, UUID offeredUuid, Player owner, boolean confirmed) {
        if (offeredUuid == null || owner == null) {
            ItemStack empty = new ItemStack(Material.ITEM_FRAME);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text(label + ": nothing", NamedTextColor.DARK_GRAY));
            empty.setItemMeta(meta);
            return empty;
        }
        PokemonInstance p = null;
        PlayerParty party = plugin.parties().get(owner);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance q = party.get(i);
            if (q != null && q.uuid.equals(offeredUuid)) { p = q; break; }
        }
        if (p == null) {
            ItemStack empty = new ItemStack(Material.ITEM_FRAME);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text(label + ": nothing", NamedTextColor.DARK_GRAY));
            empty.setItemMeta(meta);
            return empty;
        }
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        ItemStack item = new ItemStack(p.shiny ? Material.NETHER_STAR : Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label + ": " + p.displayName(species) + " Lv." + p.level,
                confirmed ? NamedTextColor.GREEN : NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + species.types, NamedTextColor.GRAY));
        lore.add(Component.text("HP " + p.currentHp + "/" + p.maxHp(species), NamedTextColor.GRAY));
        lore.add(Component.text("Nature: " + p.nature, NamedTextColor.GRAY));
        if (confirmed) lore.add(Component.text("Confirmed", NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (plugin.trades().get(player) == null) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        Integer slot = item.getItemMeta().getPersistentDataContainer()
                .get(keySlot, PersistentDataType.INTEGER);
        String action = item.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if ("cancel".equals(action)) plugin.trades().cancel(player);
            else if ("confirm".equals(action)) plugin.trades().confirm(player);
            else if (slot != null) plugin.trades().offer(player, slot);
        });
    }

    /** Closing the trade window (without a result) cancels the trade. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            TradeSession session = plugin.trades().get(player);
            // still open elsewhere? only cancel if this player really left the GUI
            if (session != null && !session.finished
                    && !(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                plugin.trades().cancel(player);
            }
        });
    }
}
