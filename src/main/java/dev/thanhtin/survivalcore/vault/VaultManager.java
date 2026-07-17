package dev.thanhtin.survivalcore.vault;

import dev.thanhtin.survivalcore.SurvivalCore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Personal storage vaults. Each page is a 54-slot inventory serialized to the
 * database. Number of pages a player may open is permission-gated.
 */
public class VaultManager {

    public static final int SIZE = 54;

    private final SurvivalCore plugin;

    public VaultManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    /** How many vault pages this player may open (default from config, or per-permission override). */
    public int maxPages(Player player) {
        int def = plugin.getConfig().getInt("vaults.default-pages", 1);
        int best = def;
        for (int n = 1; n <= 27; n++) {
            if (player.hasPermission("survivalcore.vault." + n)) best = Math.max(best, n);
        }
        return Math.max(1, best);
    }

    public ItemStack[] load(java.util.UUID uuid, int page) {
        String data = plugin.db().getVault(uuid, page);
        if (data == null || data.isBlank()) return new ItemStack[SIZE];
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                int len = in.readInt();
                ItemStack[] items = new ItemStack[SIZE];
                for (int i = 0; i < len && i < SIZE; i++) {
                    items[i] = (ItemStack) in.readObject();
                }
                return items;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load vault page " + page + ": " + t.getMessage());
            return new ItemStack[SIZE];
        }
    }

    public void save(java.util.UUID uuid, int page, ItemStack[] contents) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            int len = Math.min(contents.length, SIZE);
            out.writeInt(len);
            for (int i = 0; i < len; i++) out.writeObject(contents[i]);
            out.flush();
            plugin.db().setVault(uuid, page, Base64.getEncoder().encodeToString(bos.toByteArray()));
        } catch (Throwable t) {
            plugin.getLogger().severe("Failed to save vault page " + page + ": " + t.getMessage());
        }
    }
}
