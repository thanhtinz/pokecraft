package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.gym.BadgeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Shows the eight gym badges and which ones the player has earned. */
public class BadgesGui implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};

    private final PokeCraftPlugin plugin;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public BadgesGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (openForm(player)) return;
        Holder holder = new Holder();
        Set<String> earned = plugin.badges().badgesOf(player.getUniqueId());
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Badges  " + earned.size() + "/8"));
        holder.inventory = inv;

        List<BadgeService.Gym> gyms = BadgeService.GYMS;
        for (int i = 0; i < gyms.size() && i < SLOTS.length; i++) {
            BadgeService.Gym g = gyms.get(i);
            boolean got = earned.contains(g.badge());
            ItemStack item = new ItemStack(got ? g.icon() : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(g.badgeName(),
                    got ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Leader: " + g.leader(), NamedTextColor.GRAY));
            lore.add(Component.text("Team Lv." + g.level(), NamedTextColor.GRAY));
            lore.add(got ? Component.text("EARNED", NamedTextColor.GREEN)
                    : Component.text("Not yet earned", NamedTextColor.RED));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(SLOTS[i], item);
        }
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private boolean openForm(Player player) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        Set<String> earned = plugin.badges().badgesOf(player.getUniqueId());
        String title = "Badges  " + earned.size() + "/8";
        StringBuilder content = new StringBuilder();
        List<BadgeService.Gym> gyms = BadgeService.GYMS;
        for (int i = 0; i < gyms.size() && i < SLOTS.length; i++) {
            BadgeService.Gym g = gyms.get(i);
            boolean got = earned.contains(g.badge());
            if (i > 0) content.append("\n\n");
            content.append(got ? "§6" : "§8").append(g.badgeName()).append("\n");
            content.append("§7Leader: ").append(g.leader()).append("\n");
            content.append("§7Team Lv.").append(g.level()).append("\n");
            content.append(got ? "§aEARNED" : "§cNot yet earned");
        }
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        return plugin.bedrock().openForm(player, title, content.toString(), buttons);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
