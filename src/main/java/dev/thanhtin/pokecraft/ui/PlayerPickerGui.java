package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Picks an online player as the target of a duel challenge or a proposal. */
public class PlayerPickerGui implements Listener {

    public enum Purpose { DUEL, MARRY, TRADE, PAY, BOARD_TTT, BOARD_C4 }

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyTarget;

    private static class Holder implements InventoryHolder {
        final Purpose purpose;
        Inventory inventory;
        Holder(Purpose purpose) { this.purpose = purpose; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public PlayerPickerGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyTarget = new NamespacedKey(plugin, "picker_target");
    }

    public void open(Player player, Purpose purpose) {
        if (openForm(player, purpose)) return;
        Holder holder = new Holder(purpose);
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text(switch (purpose) {
                    case DUEL -> "Duel who?";
                    case MARRY -> "Propose to whom?";
                    case TRADE -> "Trade with whom?";
                    case PAY -> "Pay whom?";
                    case BOARD_TTT -> "Tic-Tac-Toe with whom?";
                    case BOARD_C4 -> "Connect Four with whom?";
                }));
        holder.inventory = inv;

        double maxDistance = plugin.getConfig().getDouble("pvp.max-distance", 50);
        int slot = 0;
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (slot >= 54) break;
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            if (purpose == Purpose.DUEL || purpose == Purpose.TRADE) {
                if (!other.getWorld().equals(player.getWorld())
                        || other.getLocation().distance(player.getLocation()) > maxDistance) continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(other);
            meta.displayName(Component.text(other.getName(), NamedTextColor.AQUA));
            meta.lore(List.of(Component.text(switch (purpose) {
                    case DUEL -> "Click to challenge";
                    case MARRY -> "Click to propose";
                    case TRADE -> "Click to request a trade";
                    case PAY -> "Click to send money";
                    case BOARD_TTT, BOARD_C4 -> "Click to invite";
                }, NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(keyTarget, PersistentDataType.STRING, other.getName());
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        if (slot == 0) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta meta = none.getItemMeta();
            boolean anywhere = purpose == Purpose.MARRY || purpose == Purpose.PAY
                    || purpose == Purpose.BOARD_TTT || purpose == Purpose.BOARD_C4;
            meta.displayName(Component.text(anywhere
                    ? "No other players online" : "No players nearby", NamedTextColor.RED));
            none.setItemMeta(meta);
            inv.setItem(22, none);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String targetName = item.getItemMeta().getPersistentDataContainer()
                .get(keyTarget, PersistentDataType.STRING);
        if (targetName == null) return;
        select(player, holder.purpose, targetName);
    }

    /** Runs the chosen action against the named target. Shared by the chest GUI and the Bedrock form. */
    private void select(Player player, Purpose purpose, String targetName) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            Player target = plugin.getServer().getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage(Component.text("Player is no longer online.", NamedTextColor.RED));
                return;
            }
            switch (purpose) {
                case DUEL -> plugin.pvp().challenge(player, target);
                case MARRY -> plugin.marriage().propose(player, target);
                case TRADE -> plugin.trades().request(player, target);
                case PAY -> plugin.payUi().open(player, target.getName());
                case BOARD_TTT -> plugin.boardPvp().challenge(player, target, "tictactoe");
                case BOARD_C4 -> plugin.boardPvp().challenge(player, target, "connect4");
            }
        });
    }

    private boolean openForm(Player player, Purpose purpose) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        String title = switch (purpose) {
            case DUEL -> "Duel who?";
            case MARRY -> "Propose to whom?";
            case TRADE -> "Trade with whom?";
            case PAY -> "Pay whom?";
            case BOARD_TTT -> "Tic-Tac-Toe with whom?";
            case BOARD_C4 -> "Connect Four with whom?";
        };
        double maxDistance = plugin.getConfig().getDouble("pvp.max-distance", 50);
        java.util.List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons =
                new java.util.ArrayList<>();
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) continue;
            if (purpose == Purpose.DUEL || purpose == Purpose.TRADE) {
                if (!other.getWorld().equals(player.getWorld())
                        || other.getLocation().distance(player.getLocation()) > maxDistance) continue;
            }
            String targetName = other.getName();
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    targetName, () -> select(player, purpose, targetName)));
        }
        String content;
        if (buttons.isEmpty()) {
            boolean anywhere = purpose == Purpose.MARRY || purpose == Purpose.PAY
                    || purpose == Purpose.BOARD_TTT || purpose == Purpose.BOARD_C4;
            content = anywhere ? "No other players online" : "No players nearby";
        } else {
            content = "";
        }
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        return plugin.bedrock().openForm(player, title, content, buttons);
    }
}
