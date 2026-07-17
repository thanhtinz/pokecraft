package dev.thanhtin.survivalcore.crate;

import dev.thanhtin.survivalcore.SurvivalCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** A physical crate key: right-click the matching crate block to open it. */
public final class KeyItem {

    private final NamespacedKey tag;

    public KeyItem(SurvivalCore plugin) {
        this.tag = new NamespacedKey(plugin, "crate_key");
    }

    public NamespacedKey tag() { return tag; }

    public ItemStack create(CrateManager.Crate crate, int amount) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(dev.thanhtin.survivalcore.util.Msg.legacy(crate.display() + " &eKey")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click the " + crate.id() + " crate", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.text("to open it.", NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(tag, PersistentDataType.STRING, crate.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The crate id this item is a key for, or null if it isn't a crate key. */
    public String crateId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
    }

    public boolean isKey(ItemStack item) {
        return crateId(item) != null;
    }
}
