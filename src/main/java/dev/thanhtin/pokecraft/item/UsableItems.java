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
 * Consumable items: potions (heal party pokemon outside battle - they also
 * revive fainted ones, this game has no separate Revive) and evolution stones.
 * Right-clicking one opens a party picker; clicking a pokemon applies it.
 */
public class UsableItems implements Listener {

    public enum ItemType {
        POTION("Potion", Material.HONEY_BOTTLE, 20, "Heals 20 HP"),
        SUPER_POTION("Super Potion", Material.OMINOUS_BOTTLE, 60, "Heals 60 HP"),
        HYPER_POTION("Hyper Potion", Material.DRAGON_BREATH, -1, "Fully heals HP"),
        THUNDER_STONE("Thunder Stone", Material.LIGHTNING_ROD, 0, "Evolves certain pokemon"),
        FIRE_STONE("Fire Stone", Material.BLAZE_POWDER, 0, "Evolves certain pokemon"),
        WATER_STONE("Water Stone", Material.HEART_OF_THE_SEA, 0, "Evolves certain pokemon"),
        LEAF_STONE("Leaf Stone", Material.BIG_DRIPLEAF, 0, "Evolves certain pokemon"),
        MOON_STONE("Moon Stone", Material.QUARTZ, 0, "Evolves certain pokemon");

        public final String display;
        public final Material material;
        /** HP healed; -1 = full; 0 = not a potion. */
        public final int heal;
        public final String description;

        ItemType(String display, Material material, int heal, String description) {
            this.display = display;
            this.material = material;
            this.heal = heal;
            this.description = description;
        }

        public boolean isPotion() { return heal != 0; }

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyUsable;
    private final NamespacedKey keySlot;

    private static class PickerHolder implements InventoryHolder {
        final ItemType type;
        Inventory inventory;
        PickerHolder(ItemType type) { this.type = type; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public UsableItems(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyUsable = new NamespacedKey(plugin, "usable");
        this.keySlot = new NamespacedKey(plugin, "use_slot");
    }

    public ItemStack create(ItemType type, int amount) {
        ItemStack item = new ItemStack(type.material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.display, NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                Component.text(type.description, NamedTextColor.GRAY),
                Component.text("Right-click to use on a party pokemon", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyUsable, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public ItemType read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String name = item.getItemMeta().getPersistentDataContainer()
                .get(keyUsable, PersistentDataType.STRING);
        if (name == null) return null;
        try { return ItemType.valueOf(name); }
        catch (IllegalArgumentException e) { return null; }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemType type = read(e.getItem());
        if (type == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("You can't use items during a battle.", NamedTextColor.RED));
            return;
        }
        openPicker(player, type);
    }

    private void openPicker(Player player, ItemType type) {
        PickerHolder holder = new PickerHolder(type);
        Inventory inv = plugin.getServer().createInventory(holder, 9,
                Component.text("Use " + type.display + " on..."));
        holder.inventory = inv;
        PlayerParty party = plugin.parties().get(player);
        for (int i = 0; i < PlayerParty.SIZE; i++) {
            PokemonInstance p = party.get(i);
            if (p == null) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            if (species == null) continue;
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(p.displayName(species) + " Lv." + p.level, NamedTextColor.AQUA));
            meta.lore(List.of(Component.text("HP " + p.currentHp + "/" + p.maxHp(species),
                    p.currentHp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)));
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

        PokemonInstance p = plugin.parties().get(player).get(slot);
        if (p == null) return;
        // the item must still be in hand (re-read to prevent duplication tricks)
        ItemStack hand = player.getInventory().getItemInMainHand();
        ItemType type = read(hand);
        if (type != holder.type) {
            player.closeInventory();
            return;
        }
        if (apply(player, p, type)) {
            hand.setAmount(hand.getAmount() - 1);
            plugin.parties().saveParty(player.getUniqueId());
        }
        plugin.getServer().getScheduler().runTask(plugin, (Runnable) () -> player.closeInventory());
    }

    /** @return true if the item was consumed */
    private boolean apply(Player player, PokemonInstance p, ItemType type) {
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        if (species == null) return false;
        if (type.isPotion()) {
            int max = p.maxHp(species);
            if (p.currentHp >= max) {
                player.sendMessage(Component.text(p.displayName(species) + " is already at full HP.",
                        NamedTextColor.RED));
                return false;
            }
            p.currentHp = type.heal < 0 ? max : Math.min(max, p.currentHp + type.heal);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
            player.sendMessage(Component.text(p.displayName(species) + " recovered to "
                    + p.currentHp + "/" + max + " HP.", NamedTextColor.GREEN));
            return true;
        }
        // evolution stone
        if (plugin.evolutions().tryItemEvolve(player, p, type.id())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
            return true;
        }
        player.sendMessage(Component.text("It has no effect on " + p.displayName(species) + ".",
                NamedTextColor.RED));
        return false;
    }
}
