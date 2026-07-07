package dev.thanhtin.pokecraft.minigame;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.ui.GuiFiller;
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

import java.util.List;

/** Minigames hub: pick a mini-game to play. */
public class MinigamesGui implements Listener {
    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyGame;

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public MinigamesGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyGame = new NamespacedKey(plugin, "minigame");
    }

    public void open(Player player) {
        if (openForm(player)) return;
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 45, Component.text("Minigames"));
        holder.inventory = inv;

        // solo games
        inv.setItem(10, game("casino", Material.GOLD_INGOT, "Casino",
                List.of("Coin flip and slot machine", "Bet your PokeDollars")));
        inv.setItem(11, game("trivia", Material.BOOK, "Trivia Quiz",
                List.of("Answer pokemon questions", "for a money reward")));
        inv.setItem(12, game("tictactoe", Material.OAK_SIGN, "Tic-Tac-Toe (vs AI)",
                List.of("Beat the AI in a 3x3 grid", "for a small reward")));
        inv.setItem(13, game("connect4", Material.BLUE_WOOL, "Connect Four (vs AI)",
                List.of("Line up 4 vs the AI", "for a reward")));
        inv.setItem(14, game("minesweeper", Material.TNT, "Minesweeper",
                List.of("Clear every safe tile", "without hitting a mine")));
        inv.setItem(15, game("higherlower", Material.PAPER, "Higher or Lower",
                List.of("Guess the next card", "Build a streak, then cash out")));

        // two-player games (invite another player)
        inv.setItem(29, game("pvp_ttt", Material.CRIMSON_SIGN, "Tic-Tac-Toe (PvP)",
                List.of("Challenge another player", "Winner takes the reward")));
        inv.setItem(30, game("pvp_c4", Material.RED_WOOL, "Connect Four (PvP)",
                List.of("Challenge another player", "Winner takes the reward")));

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private boolean openForm(Player player) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        java.util.List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new java.util.ArrayList<>();
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Casino",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "casino"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Trivia Quiz",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "trivia"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Tic-Tac-Toe (vs AI)",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "tictactoe"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Connect Four (vs AI)",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "connect4"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Minesweeper",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "minesweeper"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Higher or Lower",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "higherlower"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Tic-Tac-Toe (PvP)",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "pvp_ttt"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Connect Four (PvP)",
                () -> plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, "pvp_c4"))));
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        return plugin.bedrock().openForm(player, "Minigames", "", buttons);
    }

    private void launchGame(Player player, String game) {
        switch (game) {
            case "casino" -> plugin.casinoUi().open(player);
            case "trivia" -> plugin.triviaUi().open(player);
            case "tictactoe", "connect4", "minesweeper", "higherlower" ->
                    plugin.boardGames().play(player, game);
            case "pvp_ttt" -> plugin.playerPickerUi().open(player,
                    dev.thanhtin.pokecraft.ui.PlayerPickerGui.Purpose.BOARD_TTT);
            case "pvp_c4" -> plugin.playerPickerUi().open(player,
                    dev.thanhtin.pokecraft.ui.PlayerPickerGui.Purpose.BOARD_C4);
            default -> {}
        }
    }

    private ItemStack game(String id, Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        java.util.List<Component> l = new java.util.ArrayList<>();
        for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY));
        meta.lore(l);
        meta.getPersistentDataContainer().set(keyGame, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String game = item.getItemMeta().getPersistentDataContainer()
                .get(keyGame, PersistentDataType.STRING);
        if (game == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> launchGame(player, game));
    }
}
