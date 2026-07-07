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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Real two-player board games (Tic-Tac-Toe and Connect Four). Both players
 * open the <em>same</em> chest inventory instance, so a move by one player is
 * immediately visible to the other on PC and mobile alike. A challenge is
 * accepted through a confirm GUI - no chat commands needed.
 */
public class BoardPvpManager implements Listener {

    private static final int[] TTT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private final PokeCraftPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>(); // target -> challenge
    private final Map<UUID, Game> active = new HashMap<>();      // player -> game

    private record Pending(UUID from, String game, long at) {}

    public BoardPvpManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    private long reward() {
        return plugin.getConfig().getLong("minigame.board-pvp-reward", 1000);
    }

    // ---------- challenge / confirm ----------

    private static class ConfirmHolder implements InventoryHolder {
        final UUID from;
        final String game;
        Inventory inventory;
        ConfirmHolder(UUID from, String game) { this.from = from; this.game = game; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public void challenge(Player from, Player to, String game) {
        if (from.getUniqueId().equals(to.getUniqueId())) return;
        if (active.containsKey(from.getUniqueId()) || active.containsKey(to.getUniqueId())) {
            from.sendMessage(Component.text("One of you is already in a game.", NamedTextColor.RED));
            return;
        }
        pending.put(to.getUniqueId(), new Pending(from.getUniqueId(), game, System.currentTimeMillis()));
        from.sendMessage(Component.text("Challenge sent to " + to.getName() + ".", NamedTextColor.GREEN));

        ConfirmHolder holder = new ConfirmHolder(from.getUniqueId(), game);
        Inventory inv = plugin.getServer().createInventory(holder, 27,
                Component.text(from.getName() + " challenges you: " + label(game)));
        holder.inventory = inv;
        inv.setItem(11, button(Material.LIME_WOOL, "Accept", "Play " + label(game)));
        inv.setItem(15, button(Material.RED_WOOL, "Decline", "Say no thanks"));
        GuiFiller.fill(inv);
        to.openInventory(inv);
    }

    private ItemStack button(Material m, String name, String lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, m == Material.LIME_WOOL
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(java.util.List.of(Component.text(lore, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private String label(String game) {
        return "connect4".equals(game) ? "Connect Four" : "Tic-Tac-Toe";
    }

    // ---------- shared game ----------

    private static class Game implements InventoryHolder {
        final String game;
        final UUID p1;   // challenger, piece 1
        final UUID p2;   // acceptor, piece 2
        final int[] board;
        UUID turn;
        boolean over;
        Inventory inventory;
        Game(String game, UUID p1, UUID p2) {
            this.game = game;
            this.p1 = p1;
            this.p2 = p2;
            this.board = new int[("connect4".equals(game)) ? 42 : 9];
            this.turn = p1;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private void start(Player a, Player b, String game) {
        Game g = new Game(game, a.getUniqueId(), b.getUniqueId());
        int size = "connect4".equals(game) ? 54 : 45;
        Inventory inv = plugin.getServer().createInventory(g, size,
                Component.text(label(game) + ": " + a.getName() + " vs " + b.getName()));
        g.inventory = inv;
        active.put(a.getUniqueId(), g);
        active.put(b.getUniqueId(), g);
        render(g);
        a.openInventory(inv);
        b.openInventory(inv);
    }

    private void render(Game g) {
        Inventory inv = g.inventory;
        if ("connect4".equals(g.game)) {
            for (int row = 0; row < 6; row++)
                for (int col = 0; col < 7; col++)
                    inv.setItem(row * 9 + col, disc(g.board[row * 7 + col]));
        } else {
            for (int i = 0; i < 9; i++) inv.setItem(TTT_SLOTS[i], disc(g.board[i]));
        }
        GuiFiller.fill(inv);
        inv.setItem(inv.getSize() - 1, turnInfo(g));
    }

    private ItemStack disc(int v) {
        Material m = v == 1 ? Material.BLUE_WOOL : v == 2 ? Material.RED_WOOL
                : Material.WHITE_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(v == 1 ? "Blue" : v == 2 ? "Red" : "Empty",
                v == 0 ? NamedTextColor.GRAY : NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack turnInfo(Game g) {
        ItemStack item = new ItemStack(g.over ? Material.BARRIER : Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        String text;
        if (g.over) {
            text = "Game over";
        } else {
            Player t = plugin.getServer().getPlayer(g.turn);
            String name = t != null ? t.getName() : "?";
            String colour = g.turn.equals(g.p1) ? "Blue" : "Red";
            text = name + "'s turn (" + colour + ")";
        }
        meta.displayName(Component.text(text, g.over ? NamedTextColor.RED : NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof ConfirmHolder holder) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player player)) return;
            handleConfirm(player, holder, e.getSlot());
        } else if (e.getInventory().getHolder() instanceof Game g) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player player)) return;
            handleMove(player, g, e.getRawSlot());
        }
    }

    private void handleConfirm(Player player, ConfirmHolder holder, int slot) {
        if (slot != 11 && slot != 15) return; // ignore clicks off the two buttons
        Pending p = pending.get(player.getUniqueId());
        if (p == null || !p.from.equals(holder.from)) { player.closeInventory(); return; }
        pending.remove(player.getUniqueId());
        Player from = plugin.getServer().getPlayer(holder.from);
        if (slot != 11) { // Decline
            if (from != null) from.sendMessage(Component.text(player.getName()
                    + " declined the game.", NamedTextColor.GRAY));
            player.closeInventory();
            return;
        }
        if (from == null) {
            player.sendMessage(Component.text("The challenger is no longer online.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        if (active.containsKey(from.getUniqueId()) || active.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Someone is already in a game.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> start(from, player, holder.game));
    }

    private void handleMove(Player player, Game g, int rawSlot) {
        if (g.over || !player.getUniqueId().equals(g.turn)) return;
        if (rawSlot < 0 || rawSlot >= g.inventory.getSize()) return;
        int piece = player.getUniqueId().equals(g.p1) ? 1 : 2;

        boolean moved;
        if ("connect4".equals(g.game)) {
            int col = rawSlot % 9;
            if (col > 6 || rawSlot >= 54) return;
            moved = c4drop(g.board, col, piece);
        } else {
            int cell = -1;
            for (int i = 0; i < 9; i++) if (TTT_SLOTS[i] == rawSlot) cell = i;
            if (cell < 0 || g.board[cell] != 0) return;
            g.board[cell] = piece;
            moved = true;
        }
        if (!moved) return;

        boolean win = "connect4".equals(g.game) ? c4win(g.board, piece) : tttWin(g.board, piece);
        boolean full = "connect4".equals(g.game) ? c4full(g.board) : tttFull(g.board);
        if (win) { finish(g, player.getUniqueId()); return; }
        if (full) { finish(g, null); return; }
        g.turn = player.getUniqueId().equals(g.p1) ? g.p2 : g.p1;
        render(g);
    }

    private void finish(Game g, UUID winner) {
        g.over = true;
        render(g);
        Player p1 = plugin.getServer().getPlayer(g.p1);
        Player p2 = plugin.getServer().getPlayer(g.p2);
        active.remove(g.p1);
        active.remove(g.p2);
        if (winner == null) {
            announce(p1, "It's a draw.", false);
            announce(p2, "It's a draw.", false);
            return;
        }
        Player win = plugin.getServer().getPlayer(winner);
        Player lose = winner.equals(g.p1) ? p2 : p1;
        if (win != null) {
            plugin.economy().deposit(winner, reward());
            announce(win, "You win! +" + plugin.economy().format(reward()), true);
        }
        announce(lose, "You lost the game.", false);
    }

    private void announce(Player p, String msg, boolean good) {
        if (p == null) return;
        p.sendMessage(Component.text(msg, good ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        p.playSound(p.getLocation(), good ? Sound.ENTITY_PLAYER_LEVELUP
                : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, good ? 1.3f : 0.7f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        Game g = active.remove(e.getPlayer().getUniqueId());
        if (g == null || g.over) return;
        UUID otherId = e.getPlayer().getUniqueId().equals(g.p1) ? g.p2 : g.p1;
        active.remove(otherId);
        g.over = true;
        Player other = plugin.getServer().getPlayer(otherId);
        if (other != null) {
            plugin.economy().deposit(otherId, reward());
            announce(other, "Opponent left - you win! +" + plugin.economy().format(reward()), true);
            other.closeInventory();
        }
    }

    // ---------- game rules ----------

    private boolean c4drop(int[] b, int col, int p) {
        for (int row = 5; row >= 0; row--)
            if (b[row * 7 + col] == 0) { b[row * 7 + col] = p; return true; }
        return false;
    }

    private boolean c4win(int[] b, int p) {
        for (int r = 0; r < 6; r++) for (int c = 0; c < 7; c++) {
            if (b[r * 7 + c] != p) continue;
            if (c + 3 < 7 && b[r*7+c+1] == p && b[r*7+c+2] == p && b[r*7+c+3] == p) return true;
            if (r + 3 < 6 && b[(r+1)*7+c] == p && b[(r+2)*7+c] == p && b[(r+3)*7+c] == p) return true;
            if (r + 3 < 6 && c + 3 < 7 && b[(r+1)*7+c+1] == p && b[(r+2)*7+c+2] == p && b[(r+3)*7+c+3] == p) return true;
            if (r + 3 < 6 && c - 3 >= 0 && b[(r+1)*7+c-1] == p && b[(r+2)*7+c-2] == p && b[(r+3)*7+c-3] == p) return true;
        }
        return false;
    }

    private boolean c4full(int[] b) {
        for (int c = 0; c < 7; c++) if (b[c] == 0) return false;
        return true;
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
}
