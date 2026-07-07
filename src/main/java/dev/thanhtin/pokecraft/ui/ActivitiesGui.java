package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.activity.QuestManager;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
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

/** Daily check-in + daily quests in one panel. */
public class ActivitiesGui implements Listener {
    private static final int SLOT_DAILY = 4;
    private static final int[] QUEST_SLOTS = {19, 21, 23, 25};

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyDaily;
    private final NamespacedKey keyQuest;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public ActivitiesGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyDaily = new NamespacedKey(plugin, "act_daily");
        this.keyQuest = new NamespacedKey(plugin, "act_quest");
    }

    public void open(Player player) {
        plugin.quests().ensureToday(player);
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 36, Component.text("Activities"));
        holder.inventory = inv;

        boolean canClaim = plugin.daily().canClaim(player);
        int streak = plugin.daily().streakPreview(player);
        ItemStack daily = new ItemStack(canClaim ? Material.CHEST : Material.ENDER_CHEST);
        ItemMeta dailyMeta = daily.getItemMeta();
        dailyMeta.displayName(Component.text(canClaim ? "Daily reward - claim!" : "Daily reward - claimed",
                canClaim ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        dailyMeta.lore(List.of(
                Component.text((canClaim ? "Day " + streak + " streak available" : "Come back tomorrow"),
                        NamedTextColor.GRAY),
                Component.text("Longer streaks pay more; day 7 gives Ultra Balls", NamedTextColor.GRAY)));
        if (canClaim) dailyMeta.getPersistentDataContainer().set(keyDaily, PersistentDataType.BYTE, (byte) 1);
        daily.setItemMeta(dailyMeta);
        inv.setItem(SLOT_DAILY, daily);

        List<StorageManager.QuestRow> rows = plugin.storage().questsOf(player.getUniqueId());
        for (int i = 0; i < QuestManager.QUESTS.size() && i < QUEST_SLOTS.length; i++) {
            QuestManager.QuestDef def = QuestManager.QUESTS.get(i);
            StorageManager.QuestRow row = rows.stream()
                    .filter(r -> r.questId().equals(def.id())).findFirst().orElse(null);
            int progress = row == null ? 0 : Math.min(row.progress(), def.target());
            boolean claimed = row != null && row.claimed();
            boolean complete = progress >= def.target();
            ItemStack item = new ItemStack(claimed ? Material.LIME_DYE
                    : complete ? Material.GOLD_INGOT : Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(def.description() + " (" + progress + "/" + def.target() + ")",
                    claimed ? NamedTextColor.GRAY : complete ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Reward: " + plugin.economy().format(def.reward()), NamedTextColor.GRAY));
            lore.add(Component.text(claimed ? "Claimed - resets daily"
                    : complete ? "Click to claim!" : "In progress", NamedTextColor.GRAY));
            meta.lore(lore);
            if (complete && !claimed) {
                meta.getPersistentDataContainer().set(keyQuest, PersistentDataType.STRING, def.id());
            }
            item.setItemMeta(meta);
            inv.setItem(QUEST_SLOTS[i], item);
        }

        ItemStack fish = new ItemStack(Material.FISHING_ROD);
        ItemMeta fishMeta = fish.getItemMeta();
        fishMeta.displayName(Component.text("Go fishing!", NamedTextColor.AQUA));
        fishMeta.lore(List.of(Component.text("Fish near water - you may hook", NamedTextColor.GRAY),
                Component.text("a wild Water pokemon to battle", NamedTextColor.GRAY)));
        fish.setItemMeta(fishMeta);
        inv.setItem(31, fish);

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        boolean daily = item.getItemMeta().getPersistentDataContainer()
                .has(keyDaily, PersistentDataType.BYTE);
        String quest = item.getItemMeta().getPersistentDataContainer()
                .get(keyQuest, PersistentDataType.STRING);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (daily) plugin.daily().claim(player);
            else if (quest != null) plugin.quests().claim(player, quest);
            open(player);
        });
    }
}
