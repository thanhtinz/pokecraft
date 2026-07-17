package dev.thanhtin.survivalcore.job;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.job.JobManager.Job;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
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

/** Browse jobs; click to join/leave. Shows your level and pay bonus. */
public class JobGui implements Listener {

    private final SurvivalCore plugin;
    private final NamespacedKey keyJob;

    public JobGui(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyJob = new NamespacedKey(plugin, "job");
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        List<Job> jobs = new ArrayList<>(plugin.jobs().all());
        int rows = Math.max(1, (jobs.size() + 8) / 9);
        int size = Math.min(54, rows * 9);
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, size,
                Component.text("Jobs (" + plugin.db().jobCount(player.getUniqueId())
                        + "/" + plugin.jobs().maxJoined() + ")"));
        holder.inv = inv;
        int slot = 0;
        for (Job job : jobs) {
            if (slot >= size) break;
            inv.setItem(slot++, icon(player, job));
        }
        player.openInventory(inv);
    }

    private ItemStack icon(Player player, Job job) {
        boolean joined = plugin.db().hasJob(player.getUniqueId(), job.id());
        ItemStack item = new ItemStack(job.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(job.display().replaceAll("&[0-9a-fk-or]", ""),
                joined ? NamedTextColor.GREEN : NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        if (joined) {
            int lvl = plugin.jobs().level(player.getUniqueId(), job.id());
            int bonus = (int) Math.round((plugin.jobs().multiplier(lvl) - 1) * 100);
            lore.add(Component.text("Level " + lvl + " (+" + bonus + "% pay)", NamedTextColor.AQUA));
            lore.add(Component.text("Click to leave", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Click to join", NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(keyJob, PersistentDataType.STRING, job.id());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyJob, PersistentDataType.STRING);
        if (id == null || !(e.getWhoClicked() instanceof Player p)) return;
        Job job = plugin.jobs().get(id);
        if (job == null) return;
        if (plugin.db().hasJob(p.getUniqueId(), job.id())) {
            plugin.jobs().leave(p, job);
        } else {
            plugin.jobs().join(p, job);
        }
        e.getInventory().setItem(e.getSlot(), icon(p, job));
    }
}
