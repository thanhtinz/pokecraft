package dev.thanhtin.survivalcore.giftcode;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets players redeem a giftcode from the hub without a command: clicking the
 * hub button prompts them, and the next chat line they send is captured as the
 * code (that message is not broadcast). Chat input works cleanly through Geyser.
 */
public class RedeemPrompt implements Listener {

    private final SurvivalCore plugin;
    private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();

    public RedeemPrompt(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    /** Start waiting for the player's next chat message as a giftcode. */
    public void prompt(Player player) {
        player.closeInventory();
        awaiting.add(player.getUniqueId());
        Msg.info(player, "Type your giftcode in chat (or type 'cancel').");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!awaiting.remove(p.getUniqueId())) return;
        e.setCancelled(true); // don't broadcast the code
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (text.equalsIgnoreCase("cancel")) {
            Msg.info(p, "Redeem cancelled.");
            return;
        }
        // giftcode redemption touches the inventory, so run it on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.giftcodes().redeem(p, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaiting.remove(e.getPlayer().getUniqueId());
    }
}
