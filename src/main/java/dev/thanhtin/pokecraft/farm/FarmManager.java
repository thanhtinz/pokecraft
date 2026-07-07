package dev.thanhtin.pokecraft.farm;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.item.UsableItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Berry farming: plant a Berry Seed on the ground, wait for it to ripen, then
 * harvest berries (heal items) plus money. Plots persist in the farms table.
 */
public class FarmManager implements Listener {
    private final PokeCraftPlugin plugin;
    private final NamespacedKey keySeed;
    /** loc string -> plantedAt millis, cached for the growth task */
    private final Map<String, Long> plots = new ConcurrentHashMap<>();
    private BukkitRunnable task;

    public FarmManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keySeed = new NamespacedKey(plugin, "berry_seed");
    }

    public void start() {
        for (var row : plugin.storage().allFarms()) plots.put(row.loc(), row.plantedAt());
        task = new BukkitRunnable() {
            @Override public void run() { grow(); }
        };
        task.runTaskTimer(plugin, 200L, 200L); // every 10s
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public ItemStack createSeed(int amount) {
        ItemStack item = new ItemStack(Material.SWEET_BERRIES, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Berry Seed", NamedTextColor.GREEN));
        meta.lore(List.of(
                Component.text("Plant on grass or dirt", NamedTextColor.GRAY),
                Component.text("Wait for it to ripen, then harvest", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keySeed, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isSeed(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keySeed, PersistentDataType.BYTE);
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private long growSeconds() {
        return plugin.getConfig().getLong("farm.grow-seconds", 300);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player player = e.getPlayer();

        // harvest a ripe plot
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK
                && e.getClickedBlock().getType() == Material.SWEET_BERRY_BUSH) {
            String loc = key(e.getClickedBlock().getLocation());
            Long planted = plots.get(loc);
            if (planted == null) return; // natural bush, ignore
            e.setCancelled(true);
            if (System.currentTimeMillis() - planted < growSeconds() * 1000L) {
                player.sendMessage(Component.text("This berry isn't ripe yet.", NamedTextColor.GRAY));
                return;
            }
            harvest(player, e.getClickedBlock(), loc);
            return;
        }

        // plant a seed
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && isSeed(e.getItem())) {
            Material below = e.getClickedBlock().getType();
            if (below != Material.GRASS_BLOCK && below != Material.DIRT && below != Material.FARMLAND
                    && below != Material.COARSE_DIRT && below != Material.ROOTED_DIRT) {
                return;
            }
            Block above = e.getClickedBlock().getRelative(0, 1, 0);
            if (!above.getType().isAir()) return;
            e.setCancelled(true);
            above.setType(Material.SWEET_BERRY_BUSH);
            if (above.getBlockData() instanceof Ageable age) {
                age.setAge(0);
                above.setBlockData(age);
            }
            String loc = key(above.getLocation());
            long now = System.currentTimeMillis();
            plots.put(loc, now);
            plugin.storage().addFarm(loc, player.getUniqueId(), now);
            ItemStack hand = e.getItem();
            hand.setAmount(hand.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 1f);
            player.sendMessage(Component.text("Planted a berry. Come back in "
                    + (growSeconds() / 60) + " min to harvest.", NamedTextColor.GREEN));
        }
    }

    private void harvest(Player player, Block block, String loc) {
        plots.remove(loc);
        plugin.storage().removeFarm(loc);
        block.setType(Material.AIR);
        int berries = 1 + ThreadLocalRandom.current().nextInt(3);
        player.getInventory().addItem(plugin.items().create(UsableItems.ItemType.ORAN_BERRY, berries));
        long money = plugin.getConfig().getLong("farm.harvest-money", 150);
        plugin.economy().deposit(player.getUniqueId(), money);
        player.playSound(player.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1.2f);
        player.sendMessage(Component.text("Harvested " + berries + " Oran Berry + "
                + plugin.economy().format(money) + "!", NamedTextColor.GREEN));
    }

    /** Advance the visible ripeness of known plots. */
    private void grow() {
        long now = System.currentTimeMillis();
        long grow = growSeconds() * 1000L;
        for (Map.Entry<String, Long> entry : plots.entrySet()) {
            String[] p = entry.getKey().split(":");
            if (p.length != 4) continue;
            var world = plugin.getServer().getWorld(p[0]);
            if (world == null) continue;
            Block block = world.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]));
            if (block.getType() != Material.SWEET_BERRY_BUSH) {
                plots.remove(entry.getKey()); // block was destroyed
                plugin.storage().removeFarm(entry.getKey());
                continue;
            }
            double ratio = Math.min(1.0, (double) (now - entry.getValue()) / grow);
            if (block.getBlockData() instanceof Ageable age) {
                int target = (int) Math.round(ratio * age.getMaximumAge());
                if (age.getAge() != target) {
                    age.setAge(target);
                    block.setBlockData(age);
                }
            }
        }
    }
}
