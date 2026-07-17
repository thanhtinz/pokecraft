package dev.thanhtin.survivalcore.chat;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Formats chat as "[rank] name: message" using a config template. The player's
 * message component is inserted verbatim (so players can't inject formatting or
 * template tokens). PlaceholderAPI is applied to the prefix when present.
 */
public class ChatListener implements Listener {

    private final SurvivalCore plugin;

    public ChatListener(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;
        e.renderer((source, sourceDisplayName, message, viewer) -> render(source, message));
    }

    private Component render(Player source, Component message) {
        String format = plugin.getConfig().getString("chat.format", "{rank} &f{name}&7: &f{message}");
        String rank = plugin.ranks().prefix(source.getUniqueId());
        // resolve everything except the message, then splice the message component in
        String head = format.replace("{rank}", rank).replace("{name}", source.getName());
        head = applyPapi(source, head);
        String[] parts = head.split("\\{message\\}", 2);
        Component before = Msg.legacy(parts[0]);
        if (parts.length < 2) {
            return before.append(message);
        }
        return before.append(message).append(Msg.legacy(parts[1]));
    }

    private String applyPapi(Player player, String text) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }
}
