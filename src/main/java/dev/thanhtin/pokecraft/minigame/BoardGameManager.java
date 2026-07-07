package dev.thanhtin.pokecraft.minigame;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.ui.GuiFiller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single-player board games versus a simple AI. Chest GUIs, so they play the
 * same on PC and mobile. 1 = you, 2 = AI, 0 = empty.
 */
public class BoardGameManager implements Listener {

    // Tic-Tac-Toe uses a 3x3 mapped to these chest slots
    private static final int[] TTT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private final PokeCraftPlugin plugin;

    private static class TttHolder implements InventoryHolder {
        final int[] board = new int[9];
        boolean over;
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static class C4Holder implements InventoryHolder {
        final int[] board = new int[42]; // 6 rows x 7 cols, row0 = top
        boolean over;
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    // Minesweeper: 5x5 grid, reveal-only (mobile-safe, no flagging)
    private static final int MINES_COLS = 5, MINES_ROWS = 5, MINES_COUNT = 5;

    private static class MinesHolder implements InventoryHolder {
        final boolean[] mine = new boolean[MINES_COLS * MINES_ROWS];
        final boolean[] shown = new boolean[MINES_COLS * MINES_ROWS];
        boolean over;
        boolean seeded;
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static class HlHolder implements InventoryHolder {
        int current;   // 1..13 card value
        int streak;
        long pot;
        boolean over;
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public BoardGameManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String game) {
        switch (game) {
            case "tictactoe" -> openTtt(player, new TttHolder());
            case "connect4" -> openC4(player, new C4Holder());
            case "minesweeper" -> openMines(player, new MinesHolder());
            case "higherlower" -> openHl(player, new HlHolder());
            default -> {}
        }
    }

    private long reward() {
        return plugin.getConfig().getLong("minigame.board-reward", 500);
    }

    // ---------- Tic-Tac-Toe ----------

    private void openTtt(Player player, TttHolder holder) {
        if (openTttForm(player, holder)) return;
        Inventory inv = plugin.getServer().createInventory(holder, 45,
                Component.text("Tic-Tac-Toe (you = X)"));
        holder.inventory = inv;
        for (int i = 0; i < 9; i++) {
            inv.setItem(TTT_SLOTS[i], cell(holder.board[i]));
        }
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private ItemStack cell(int v) {
        Material m = v == 1 ? Material.BLUE_WOOL : v == 2 ? Material.RED_WOOL : Material.WHITE_STAINED_GLASS_PANE;
        String name = v == 1 ? "X (you)" : v == 2 ? "O (AI)" : "Empty";
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, v == 0 ? NamedTextColor.GRAY : NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    private void handleTtt(Player player, TttHolder holder, int rawSlot) {
        if (holder.over) return;
        int cellIndex = -1;
        for (int i = 0; i < 9; i++) if (TTT_SLOTS[i] == rawSlot) cellIndex = i;
        if (cellIndex < 0 || holder.board[cellIndex] != 0) return;

        holder.board[cellIndex] = 1;
        if (tttWin(holder.board, 1)) { finishTtt(player, holder, "You win!"); return; }
        if (tttFull(holder.board)) { finishTtt(player, holder, "Draw."); return; }

        int aiMove = tttAiMove(holder.board);
        if (aiMove >= 0) holder.board[aiMove] = 2;
        if (tttWin(holder.board, 2)) { finishTtt(player, holder, "The AI wins!"); return; }
        if (tttFull(holder.board)) { finishTtt(player, holder, "Draw."); return; }
        openTtt(player, holder);
    }

    private void finishTtt(Player player, TttHolder holder, String msg) {
        holder.over = true;
        boolean won = msg.startsWith("You win");
        payout(player, won, msg);
        openTtt(player, holder);
    }

    /** AI: win if it can, else block the player, else center/random. */
    private int tttAiMove(int[] b) {
        for (int i = 0; i < 9; i++) if (b[i] == 0) { b[i] = 2; boolean w = tttWin(b, 2); b[i] = 0; if (w) return i; }
        for (int i = 0; i < 9; i++) if (b[i] == 0) { b[i] = 1; boolean w = tttWin(b, 1); b[i] = 0; if (w) return i; }
        if (b[4] == 0) return 4;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (b[i] == 0) free.add(i);
        return free.isEmpty() ? -1 : free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }

    private boolean tttWin(int[] b, int p) {
        int[][] lines = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] l : lines) if (b[l[0]] == p && b[l[1]] == p && b[l[2]] == p) return true;
        return false;
    }

    private boolean tttFull(int[] b) {
        for (int v : b) if (v == 0) return false;
        return true;
    }

    // ---------- Connect Four ----------

    private void openC4(Player player, C4Holder holder) {
        if (openC4Form(player, holder)) return;
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text("Connect Four (you = blue)"));
        holder.inventory = inv;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                inv.setItem(row * 9 + col, c4cell(holder.board[row * 7 + col]));
            }
        }
        // column drop buttons on the far right column (slot col*9+8 area) - use bottom row 45..51 already cells;
        // instead put arrow hints at slot 7 of each row is empty; clicking any cell drops in that column.
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private ItemStack c4cell(int v) {
        Material m = v == 1 ? Material.BLUE_WOOL : v == 2 ? Material.RED_WOOL : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(v == 1 ? "You" : v == 2 ? "AI" : "Click a column to drop",
                v == 0 ? NamedTextColor.GRAY : NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    private void handleC4(Player player, C4Holder holder, int rawSlot) {
        if (holder.over) return;
        int col = rawSlot % 9;
        if (col > 6 || rawSlot >= 54) return;
        if (!c4drop(holder.board, col, 1)) return; // column full

        if (c4win(holder.board, 1)) { finishC4(player, holder, "You win!"); return; }
        if (c4full(holder.board)) { finishC4(player, holder, "Draw."); return; }

        int aiCol = c4AiMove(holder.board);
        if (aiCol >= 0) c4drop(holder.board, aiCol, 2);
        if (c4win(holder.board, 2)) { finishC4(player, holder, "The AI wins!"); return; }
        if (c4full(holder.board)) { finishC4(player, holder, "Draw."); return; }
        openC4(player, holder);
    }

    private void finishC4(Player player, C4Holder holder, String msg) {
        holder.over = true;
        payout(player, msg.startsWith("You win"), msg);
        openC4(player, holder);
    }

    /** Drop a piece into the lowest empty row of a column. @return false if full. */
    private boolean c4drop(int[] b, int col, int p) {
        for (int row = 5; row >= 0; row--) {
            if (b[row * 7 + col] == 0) { b[row * 7 + col] = p; return true; }
        }
        return false;
    }

    private int c4AiMove(int[] b) {
        // win if possible, else block, else random valid column
        for (int c = 0; c < 7; c++) { int[] copy = b.clone(); if (c4drop(copy, c, 2) && c4win(copy, 2)) return c; }
        for (int c = 0; c < 7; c++) { int[] copy = b.clone(); if (c4drop(copy, c, 1) && c4win(copy, 1)) return c; }
        List<Integer> valid = new ArrayList<>();
        for (int c = 0; c < 7; c++) if (b[c] == 0) valid.add(c);
        return valid.isEmpty() ? -1 : valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
    }

    private boolean c4win(int[] b, int p) {
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                if (b[r * 7 + c] != p) continue;
                if (c + 3 < 7 && b[r*7+c+1] == p && b[r*7+c+2] == p && b[r*7+c+3] == p) return true;
                if (r + 3 < 6 && b[(r+1)*7+c] == p && b[(r+2)*7+c] == p && b[(r+3)*7+c] == p) return true;
                if (r + 3 < 6 && c + 3 < 7 && b[(r+1)*7+c+1] == p && b[(r+2)*7+c+2] == p && b[(r+3)*7+c+3] == p) return true;
                if (r + 3 < 6 && c - 3 >= 0 && b[(r+1)*7+c-1] == p && b[(r+2)*7+c-2] == p && b[(r+3)*7+c-3] == p) return true;
            }
        }
        return false;
    }

    private boolean c4full(int[] b) {
        for (int c = 0; c < 7; c++) if (b[c] == 0) return false;
        return true;
    }

    // ---------- Minesweeper ----------

    private void openMines(Player player, MinesHolder holder) {
        if (openMinesForm(player, holder)) return;
        Inventory inv = plugin.getServer().createInventory(holder, 45,
                Component.text("Minesweeper - clear all safe tiles"));
        holder.inventory = inv;
        for (int r = 0; r < MINES_ROWS; r++) {
            for (int c = 0; c < MINES_COLS; c++) {
                inv.setItem(r * 9 + c, mineCell(holder, r * MINES_COLS + c));
            }
        }
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private ItemStack mineCell(MinesHolder h, int idx) {
        if (!h.shown[idx]) {
            ItemStack item = new ItemStack(h.over && h.mine[idx] ? Material.TNT : Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(h.over && h.mine[idx] ? "Mine" : "Hidden",
                    h.over && h.mine[idx] ? NamedTextColor.RED : NamedTextColor.GRAY));
            item.setItemMeta(meta);
            return item;
        }
        int n = mineNeighbours(h, idx);
        ItemStack item = new ItemStack(n == 0 ? Material.LIME_STAINED_GLASS_PANE : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (n > 0) item.setAmount(n);
        meta.displayName(Component.text(n == 0 ? "Clear" : (n + " nearby"),
                NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    private int mineNeighbours(MinesHolder h, int idx) {
        int r = idx / MINES_COLS, c = idx % MINES_COLS, n = 0;
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            if (dr == 0 && dc == 0) continue;
            int nr = r + dr, nc = c + dc;
            if (nr < 0 || nr >= MINES_ROWS || nc < 0 || nc >= MINES_COLS) continue;
            if (h.mine[nr * MINES_COLS + nc]) n++;
        }
        return n;
    }

    private void handleMines(Player player, MinesHolder h, int rawSlot) {
        if (h.over || rawSlot >= 45) return;
        int r = rawSlot / 9, c = rawSlot % 9;
        if (r >= MINES_ROWS || c >= MINES_COLS) return;
        int idx = r * MINES_COLS + c;
        if (h.shown[idx]) return;
        if (!h.seeded) seedMines(h, idx); // first click is always safe
        if (h.mine[idx]) {
            h.over = true;
            for (int i = 0; i < h.mine.length; i++) if (h.mine[i]) h.shown[i] = false;
            openMines(player, h);
            payout(player, false, "Boom! You hit a mine.");
            return;
        }
        reveal(h, idx);
        boolean win = true;
        for (int i = 0; i < h.mine.length; i++) if (!h.mine[i] && !h.shown[i]) win = false;
        if (win) {
            h.over = true;
            openMines(player, h);
            payout(player, true, "Swept the field!");
        } else {
            openMines(player, h);
        }
    }

    private void seedMines(MinesHolder h, int safeIdx) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int placed = 0;
        while (placed < MINES_COUNT) {
            int i = rnd.nextInt(h.mine.length);
            if (i == safeIdx || h.mine[i]) continue;
            h.mine[i] = true;
            placed++;
        }
        h.seeded = true;
    }

    /** Flood-reveal from a zero-neighbour tile. */
    private void reveal(MinesHolder h, int idx) {
        if (h.shown[idx] || h.mine[idx]) return;
        h.shown[idx] = true;
        if (mineNeighbours(h, idx) != 0) return;
        int r = idx / MINES_COLS, c = idx % MINES_COLS;
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            int nr = r + dr, nc = c + dc;
            if (nr < 0 || nr >= MINES_ROWS || nc < 0 || nc >= MINES_COLS) continue;
            reveal(h, nr * MINES_COLS + nc);
        }
    }

    // ---------- Higher or Lower ----------

    private void openHl(Player player, HlHolder holder) {
        if (holder.current == 0) holder.current = ThreadLocalRandom.current().nextInt(1, 14);
        if (openHlForm(player, holder)) return;
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text("Higher or Lower - streak " + holder.streak));
        holder.inventory = inv;

        ItemStack card = new ItemStack(Material.PAPER);
        card.setAmount(Math.max(1, holder.current));
        ItemMeta cm = card.getItemMeta();
        cm.displayName(Component.text("Card: " + holder.current + " (1-13)", NamedTextColor.YELLOW));
        cm.lore(List.of(Component.text("Pot: " + plugin.economy().format(holder.pot), NamedTextColor.GRAY),
                Component.text("Guess the next card", NamedTextColor.GRAY)));
        card.setItemMeta(cm);
        inv.setItem(4, card);

        inv.setItem(10, hlButton(Material.LIME_WOOL, "Higher"));
        inv.setItem(12, hlButton(Material.RED_WOOL, "Lower"));
        inv.setItem(16, hlButton(Material.GOLD_INGOT, "Cash out"));
        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    /** Native Bedrock Higher-or-Lower: current card as content, Higher/Lower/Cash-out buttons. */
    private boolean openHlForm(Player player, HlHolder holder) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = List.of(
                new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Higher",
                        () -> handleHl(player, holder, 10)),
                new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Lower",
                        () -> handleHl(player, holder, 12)),
                new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                        "Cash out (" + plugin.economy().format(holder.pot) + ")",
                        () -> handleHl(player, holder, 16)));
        return plugin.bedrock().openForm(player, "Higher or Lower - streak " + holder.streak,
                "Card: " + holder.current + " (1-13)\nPot: " + plugin.economy().format(holder.pot)
                        + "\nWill the next card be higher or lower?", buttons);
    }

    private ItemStack hlButton(Material m, String name) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        item.setItemMeta(meta);
        return item;
    }

    private void handleHl(Player player, HlHolder h, int slot) {
        if (h.over) return;
        long step = plugin.getConfig().getLong("minigame.board-reward", 500);
        if (slot == 16) { // cash out
            h.over = true;
            if (h.pot > 0) {
                plugin.economy().deposit(player.getUniqueId(), h.pot);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
                player.sendMessage(Component.text("Cashed out " + plugin.economy().format(h.pot)
                        + " (streak " + h.streak + ")", NamedTextColor.GREEN));
            }
            player.closeInventory();
            return;
        }
        if (slot != 10 && slot != 12) return;
        boolean guessHigher = slot == 10;
        int next = ThreadLocalRandom.current().nextInt(1, 14);
        boolean correct = next == h.current
                ? true // a tie is generously counted as a win
                : (guessHigher ? next > h.current : next < h.current);
        h.current = next;
        if (correct) {
            h.streak++;
            h.pot += step;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
            openHl(player, h);
        } else {
            h.over = true;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            player.sendMessage(Component.text("Wrong! It was " + next + ". You lose the pot of "
                    + plugin.economy().format(h.pot) + ".", NamedTextColor.RED));
            player.closeInventory();
        }
    }

    // ---------- native Bedrock forms for the grid games ----------
    // The board is drawn as a text grid in the form content; each tappable
    // cell/column is a button that reuses the same handle*/handler logic.

    private boolean openTttForm(Player player, TttHolder holder) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        StringBuilder sb = new StringBuilder("You = §9X§r   AI = §cO§r\n\n");
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int v = holder.board[r * 3 + c];
                sb.append(v == 1 ? "§9X" : v == 2 ? "§cO" : "§7·").append("§r");
                if (c < 2) sb.append("  |  ");
            }
            sb.append("\n");
        }
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        if (!holder.over) {
            for (int i = 0; i < 9; i++) {
                if (holder.board[i] != 0) continue;
                final int slot = TTT_SLOTS[i];
                int r = i / 3, c = i % 3;
                buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                        "Row " + (r + 1) + ", Col " + (c + 1),
                        () -> handleTtt(player, holder, slot)));
            }
        } else {
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    "Play again", () -> play(player, "tictactoe")));
        }
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        return plugin.bedrock().openForm(player, "Tic-Tac-Toe", sb.toString(), buttons);
    }

    private boolean openC4Form(Player player, C4Holder holder) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        StringBuilder sb = new StringBuilder("You = §9O§r   AI = §cO§r\n\n");
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                int v = holder.board[r * 7 + c];
                sb.append(v == 1 ? "§9O" : v == 2 ? "§cO" : "§7·").append("§r ");
            }
            sb.append("\n");
        }
        sb.append("§7 1  2  3  4  5  6  7");
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        if (!holder.over) {
            for (int c = 0; c < 7; c++) {
                if (holder.board[c] != 0) continue; // top row filled = column full
                final int col = c;
                buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                        "Drop in column " + (c + 1),
                        () -> handleC4(player, holder, col)));
            }
        } else {
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    "Play again", () -> play(player, "connect4")));
        }
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        return plugin.bedrock().openForm(player, "Connect Four", sb.toString(), buttons);
    }

    private boolean openMinesForm(Player player, MinesHolder holder) {
        if (!plugin.bedrock().isBedrock(player)) return false;
        StringBuilder sb = new StringBuilder("Reveal every safe tile. Numbers = mines nearby.\n\n");
        for (int r = 0; r < MINES_ROWS; r++) {
            for (int c = 0; c < MINES_COLS; c++) {
                int idx = r * MINES_COLS + c;
                if (holder.over && holder.mine[idx]) sb.append("§c✱§r ");
                else if (!holder.shown[idx]) sb.append("§7▨§r ");
                else {
                    int n = mineNeighbours(holder, idx);
                    sb.append(n == 0 ? "§8·§r " : "§e" + n + "§r ");
                }
            }
            sb.append("\n");
        }
        List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons = new ArrayList<>();
        if (!holder.over) {
            for (int r = 0; r < MINES_ROWS; r++) {
                for (int c = 0; c < MINES_COLS; c++) {
                    int idx = r * MINES_COLS + c;
                    if (holder.shown[idx]) continue;
                    final int slot = r * 9 + c;
                    buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                            "Reveal R" + (r + 1) + " C" + (c + 1),
                            () -> handleMines(player, holder, slot)));
                }
            }
        } else {
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    "Play again", () -> play(player, "minesweeper")));
        }
        buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        return plugin.bedrock().openForm(player, "Minesweeper", sb.toString(), buttons);
    }

    // ---------- shared ----------

    private void payout(Player player, boolean won, String msg) {
        if (won) {
            plugin.economy().deposit(player.getUniqueId(), reward());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            player.sendMessage(Component.text(msg + " +" + plugin.economy().format(reward()),
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(msg, NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getInventory().getHolder() instanceof TttHolder h) {
            e.setCancelled(true);
            handleTtt(player, h, e.getRawSlot());
        } else if (e.getInventory().getHolder() instanceof C4Holder h) {
            e.setCancelled(true);
            handleC4(player, h, e.getRawSlot());
        } else if (e.getInventory().getHolder() instanceof MinesHolder h) {
            e.setCancelled(true);
            handleMines(player, h, e.getRawSlot());
        } else if (e.getInventory().getHolder() instanceof HlHolder h) {
            e.setCancelled(true);
            handleHl(player, h, e.getRawSlot());
        }
    }
}
