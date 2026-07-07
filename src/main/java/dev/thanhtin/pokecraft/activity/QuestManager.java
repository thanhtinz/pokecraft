package dev.thanhtin.pokecraft.activity;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Daily quests tracked from existing gameplay events, with money rewards. */
public class QuestManager {

    public record QuestDef(String id, String description, int target, long reward) {}

    public static final List<QuestDef> QUESTS = List.of(
            new QuestDef("catch", "Catch pokemon", 3, 300),
            new QuestDef("wild", "Win wild battles", 5, 400),
            new QuestDef("pvp", "Win a duel", 1, 500),
            new QuestDef("fish", "Fish up pokemon", 3, 300));

    private final PokeCraftPlugin plugin;

    public QuestManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public static QuestDef def(String id) {
        return QUESTS.stream().filter(q -> q.id().equals(id)).findFirst().orElse(null);
    }

    /** Reset the player's quests if they are for a previous day (or missing). */
    public void ensureToday(Player player) {
        long today = DailyManager.today();
        UUID id = player.getUniqueId();
        List<StorageManager.QuestRow> rows = plugin.storage().questsOf(id);
        for (QuestDef q : QUESTS) {
            StorageManager.QuestRow row = rows.stream()
                    .filter(r -> r.questId().equals(q.id())).findFirst().orElse(null);
            if (row == null || row.day() != today) {
                plugin.storage().resetQuest(id, q.id(), today);
            }
        }
    }

    /** Advance a quest; called from capture / battle / fishing hooks. */
    public void progress(Player player, String questId, int delta) {
        if (player == null) return;
        ensureToday(player);
        plugin.storage().addQuestProgress(player.getUniqueId(), questId, delta);
    }

    public void claim(Player player, String questId) {
        ensureToday(player);
        QuestDef def = def(questId);
        if (def == null) return;
        StorageManager.QuestRow row = plugin.storage().questsOf(player.getUniqueId()).stream()
                .filter(r -> r.questId().equals(questId)).findFirst().orElse(null);
        if (row == null || row.claimed()) return;
        if (row.progress() < def.target()) {
            player.sendMessage(Component.text("Quest not complete yet.", NamedTextColor.RED));
            return;
        }
        plugin.storage().claimQuest(player.getUniqueId(), questId);
        plugin.economy().deposit(player.getUniqueId(), def.reward());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        player.sendMessage(Component.text("Quest complete: " + def.description() + "! +"
                + plugin.economy().format(def.reward()), NamedTextColor.GREEN));
    }
}
