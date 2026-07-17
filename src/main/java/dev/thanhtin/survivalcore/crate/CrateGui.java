package dev.thanhtin.survivalcore.crate;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.crate.CrateManager.Crate;
import dev.thanhtin.survivalcore.crate.CrateManager.Reward;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** A short spin animation in a chest GUI that lands on the pre-rolled reward. */
public class CrateGui implements Listener {

    private static final int[] REEL = {10, 11, 12, 13, 14, 15, 16};
    private static final int WIN = 13;

    private final SurvivalCore plugin;

    public CrateGui(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    /** Consume a key (already checked) and play the opening animation, granting the reward. */
    public void spin(Player player, Crate crate) {
        Reward reward = plugin.crates().roll(crate);
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text(crate.display()));
        holder.inv = inv;
        // glass frame around the reel
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(WIN - 9, new ItemStack(Material.LIME_STAINED_GLASS_PANE));  // top pointer
        inv.setItem(WIN + 9, new ItemStack(Material.LIME_STAINED_GLASS_PANE));  // bottom pointer
        player.openInventory(inv);

        new BukkitRunnable() {
            int ticks = 0;
            final int total = 34; // ~1.7s

            @Override
            public void run() {
                if (!player.isOnline() || !(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                    // player closed it - still grant so the key isn't wasted
                    plugin.crates().grant(player, reward);
                    cancel();
                    return;
                }
                for (int slot : REEL) {
                    inv.setItem(slot, plugin.crates().icon(randomReward(crate)));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                ticks++;
                if (ticks >= total) {
                    inv.setItem(WIN, plugin.crates().icon(reward));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    plugin.crates().grant(player, reward);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 3L, 2L); // deceleration approximated by fixed 2-tick period
    }

    private Reward randomReward(Crate crate) {
        return crate.rewards().get(ThreadLocalRandom.current().nextInt(crate.rewards().size()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
