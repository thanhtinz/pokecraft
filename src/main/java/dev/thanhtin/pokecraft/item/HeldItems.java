package dev.thanhtin.pokecraft.item;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Held items: right-click one to give it to a party pokemon; it works
 * automatically in battle. Unequip from the pokemon's Summary screen.
 */
public class HeldItems implements Listener {

    public enum HeldType {
        LEFTOVERS("Leftovers", Material.APPLE, "Restores 1/16 HP every battle turn"),
        MUSCLE_BAND("Muscle Band", Material.LEATHER, "+10% physical damage"),
        WISE_GLASSES("Wise Glasses", Material.SPYGLASS, "+10% special damage"),
        QUICK_CLAW("Quick Claw", Material.FLINT, "20% chance to move first"),
        LUCKY_EGG("Lucky Egg", Material.TURTLE_EGG, "+50% EXP from battles"),
        EVERSTONE("Everstone", Material.SMOOTH_STONE, "Prevents evolution"),
        FOCUS_BAND("Focus Band", Material.LEAD, "10% chance to survive on 1 HP"),
        ORAN_BERRY("Oran Berry (held)", Material.GLOW_BERRIES, "Eaten to heal 30 HP when below half (once)"),
        SITRUS_BERRY("Sitrus Berry (held)", Material.SWEET_BERRIES, "Eaten to heal 1/4 HP when below half (once)"),
        LUM_BERRY("Lum Berry (held)", Material.NETHER_WART, "Eaten to cure any status in battle (once)");

        public final String display;
        public final Material material;
        public final String description;

        HeldType(String display, Material material, String description) {
            this.display = display;
            this.material = material;
            this.description = description;
        }

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyHeld;
    private final NamespacedKey keySlot;

    private static class PickerHolder implements InventoryHolder {
        final HeldType type;
        Inventory inventory;
        PickerHolder(HeldType type) { this.type = type; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public HeldItems(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyHeld = new NamespacedKey(plugin, "held_item");
        this.keySlot = new NamespacedKey(plugin, "held_slot");
    }

    public ItemStack create(HeldType type, int amount) {
        ItemStack item = new ItemStack(type.material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.display, NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text(type.description, NamedTextColor.GRAY),
                Component.text("Right-click to give to a party pokemon", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyHeld, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public HeldType read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String name = item.getItemMeta().getPersistentDataContainer()
                .get(keyHeld, PersistentDataType.STRING);
        if (name == null) return null;
        try { return HeldType.valueOf(name); }
        catch (IllegalArgumentException e) { return null; }
    }

    public HeldType byId(String id) {
        if (id == null) return null;
        try { return HeldType.valueOf(id.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Removes the pokemon's held item and returns it to the player's inventory. */
    public void unequip(Player player, PokemonInstance p) {
        HeldType type = byId(p.heldItem);
        p.heldItem = null;
        if (type != null) {
            var leftover = player.getInventory().addItem(create(type, 1));
            leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(Component.text("Took back the " + type.display + ".",
                    NamedTextColor.GREEN));
        }
        plugin.parties().saveParty(player.getUniqueId());
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        HeldType type = read(e.getItem());
        if (type == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("You can't do that during a battle.", NamedTextColor.RED));
            return;
        }
        openPicker(player, type);
    }

    private void openPicker(Player player, HeldType type) {
        if (openForm(player, type)) return;
        PickerHolder holder = new PickerHolder(type);
        Inventory inv = plugin.getServer().createInventory(holder, 9,
                Component.text("Give " + type.display + " to..."));
        holder.inventory = inv;
        PlayerParty party = plugin.parties().get(player);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            if (species == null) continue;
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(p.displayName(species) + " Lv." + p.level,
                    NamedTextColor.AQUA));
            HeldType current = byId(p.heldItem);
            meta.lore(List.of(Component.text(current != null
                    ? "Holding: " + current.display + " (will be swapped)"
                    : "Holding nothing", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keySlot, PersistentDataType.INTEGER, i);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onPick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PickerHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        Integer slot = clicked.getItemMeta().getPersistentDataContainer()
                .get(keySlot, PersistentDataType.INTEGER);
        if (slot == null) return;
        giveHeld(player, slot, holder.type);
    }

    /** Gives the held item currently in hand to the party pokemon at {@code slot}. Shared by chest GUI and Bedrock form. */
    private void giveHeld(Player player, int slot, HeldType expected) {
        PokemonInstance p = plugin.parties().get(player).get(slot);
        if (p == null) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        HeldType type = read(hand);
        if (type != expected) {
            player.closeInventory();
            return;
        }
        HeldType previous = byId(p.heldItem);
        p.heldItem = type.id();
        hand.setAmount(hand.getAmount() - 1);
        if (previous != null) {
            var leftover = player.getInventory().addItem(create(previous, 1));
            leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        plugin.parties().saveParty(player.getUniqueId());
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
        player.sendMessage(Component.text(p.displayName(species) + " is now holding the "
                + type.display + "." + (previous != null ? " (returned the " + previous.display + ")" : ""),
                NamedTextColor.GREEN));
        plugin.getServer().getScheduler().runTask(plugin, (Runnable) () -> player.closeInventory());
    }

    private boolean openForm(Player player, HeldType type) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        String title = "Give " + type.display + " to...";
        PlayerParty party = plugin.parties().get(player);
        java.util.List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons =
                new java.util.ArrayList<>();
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            if (species == null) continue;
            HeldType current = byId(p.heldItem);
            String label = p.displayName(species) + " Lv." + p.level + "\n" + (current != null
                    ? "Holding: " + current.display + " (will be swapped)"
                    : "Holding nothing");
            int slot = i;
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    label, () -> giveHeld(player, slot, type)));
        }
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        return plugin.bedrock().openForm(player, title, "", buttons);
    }
}
