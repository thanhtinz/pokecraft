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
    private final NamespacedKey keyBadges;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public ActivitiesGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyDaily = new NamespacedKey(plugin, "act_daily");
        this.keyQuest = new NamespacedKey(plugin, "act_quest");
        this.keyBadges = new NamespacedKey(plugin, "act_badges");
    }

    public void open(Player player) {
        if (openForm(player)) return;
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

        ItemStack badges = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta badgesMeta = badges.getItemMeta();
        badgesMeta.displayName(Component.text("Gym Badges  " + plugin.badges().count(player.getUniqueId())
                + "/8", NamedTextColor.GOLD));
        badgesMeta.lore(List.of(Component.text("Beat gym leaders to earn badges", NamedTextColor.GRAY)));
        badgesMeta.getPersistentDataContainer().set(keyBadges, PersistentDataType.BYTE, (byte) 1);
        badges.setItemMeta(badgesMeta);
        inv.setItem(33, badges);

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private boolean openForm(Player player) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        plugin.quests().ensureToday(player);

        boolean canClaim = plugin.daily().canClaim(player);
        int streak = plugin.daily().streakPreview(player);

        StringBuilder content = new StringBuilder();
        content.append(canClaim
                ? "§6Daily reward - claim! §7Day " + streak + " streak available"
                : "§7Daily reward - claimed. Come back tomorrow");
        content.append("\n§7Longer streaks pay more; day 7 gives Ultra Balls");

        java.util.List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new java.util.ArrayList<>();
        if (canClaim) {
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Daily reward - claim!",
                    () -> plugin.getServer().getScheduler().runTask(plugin, () -> handleDaily(player))));
        }

        content.append("\n\n§eDaily quests:");
        List<StorageManager.QuestRow> rows = plugin.storage().questsOf(player.getUniqueId());
        for (int i = 0; i < QuestManager.QUESTS.size() && i < QUEST_SLOTS.length; i++) {
            QuestManager.QuestDef def = QuestManager.QUESTS.get(i);
            StorageManager.QuestRow row = rows.stream()
                    .filter(r -> r.questId().equals(def.id())).findFirst().orElse(null);
            int progress = row == null ? 0 : Math.min(row.progress(), def.target());
            boolean claimed = row != null && row.claimed();
            boolean complete = progress >= def.target();
            String label = def.description() + " (" + progress + "/" + def.target() + ")";
            content.append("\n§7").append(label).append(" - ")
                    .append(claimed ? "Claimed" : complete ? "Ready to claim!" : "In progress")
                    .append(" §8[Reward: ").append(plugin.economy().format(def.reward())).append("]");
            if (complete && !claimed) {
                final String questId = def.id();
                buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(label,
                        () -> plugin.getServer().getScheduler().runTask(plugin, () -> handleQuest(player, questId))));
            }
        }

        content.append("\n\n§bGo fishing! §7Fish near water - you may hook a wild Water pokemon to battle");

        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                "Gym Badges  " + plugin.badges().count(player.getUniqueId()) + "/8",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> handleBadges(player))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));

        return plugin.bedrock().openForm(player, "Activities", content.toString(), buttons);
    }

    private void handleDaily(Player player) {
        plugin.daily().claim(player);
        open(player);
    }

    private void handleQuest(Player player, String quest) {
        plugin.quests().claim(player, quest);
        open(player);
    }

    private void handleBadges(Player player) {
        plugin.badgesUi().open(player);
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
        boolean badges = item.getItemMeta().getPersistentDataContainer()
                .has(keyBadges, PersistentDataType.BYTE);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (badges) { plugin.badgesUi().open(player); return; }
            if (daily) plugin.daily().claim(player);
            else if (quest != null) plugin.quests().claim(player, quest);
            open(player);
        });
    }
}
