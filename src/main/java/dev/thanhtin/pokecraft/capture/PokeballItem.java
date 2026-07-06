package dev.thanhtin.pokecraft.capture;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class PokeballItem {

    public enum BallType {
        POKE_BALL("Poke Ball", 1.0, 1),
        GREAT_BALL("Great Ball", 1.5, 2),
        ULTRA_BALL("Ultra Ball", 2.0, 3),
        MASTER_BALL("Master Ball", 255.0, 4);

        public final String display;
        public final double bonus;
        public final int customModelData;

        BallType(String display, double bonus, int customModelData) {
            this.display = display;
            this.bonus = bonus;
            this.customModelData = customModelData;
        }
    }

    private final NamespacedKey keyBall;

    public PokeballItem(PokeCraftPlugin plugin) {
        this.keyBall = new NamespacedKey(plugin, "ball");
    }

    public ItemStack create(BallType type, int amount) {
        ItemStack item = new ItemStack(Material.SNOWBALL, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.display, NamedTextColor.RED));
        meta.lore(List.of(Component.text("Throw at a wild pokemon to capture it", NamedTextColor.GRAY)));
        meta.setCustomModelData(type.customModelData);
        meta.getPersistentDataContainer().set(keyBall, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public BallType read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String name = item.getItemMeta().getPersistentDataContainer()
                .get(keyBall, PersistentDataType.STRING);
        if (name == null) return null;
        try { return BallType.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    public NamespacedKey key() { return keyBall; }
}
