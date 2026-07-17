package dev.thanhtin.survivalcore.command;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** All SurvivalCore commands in one dispatcher, routed by command name. */
public class Commands implements CommandExecutor, TabCompleter {

    private final SurvivalCore plugin;

    public Commands(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase();
        // economy commands that allow console for lookups
        switch (name) {
            case "balance" -> { return balance(sender, a); }
            case "baltop" -> { return baltop(sender); }
            case "eco" -> { return eco(sender, a); }
        }
        if (!(sender instanceof Player p)) {
            Msg.error(sender, "Players only.");
            return true;
        }
        switch (name) {
            case "pay" -> pay(p, a);
            case "sethome" -> setHome(p, a);
            case "home" -> home(p, a);
            case "delhome" -> delHome(p, a);
            case "homes" -> listHomes(p);
            case "setwarp" -> setWarp(p, a);
            case "delwarp" -> delWarp(p, a);
            case "warp" -> warp(p, a);
            case "warps" -> listWarps(p);
            case "spawn" -> spawn(p);
            case "setspawn" -> setSpawn(p);
            case "tpa" -> tpa(p, a, false);
            case "tpahere" -> tpa(p, a, true);
            case "tpaccept" -> plugin.tpa().accept(p);
            case "tpdeny" -> plugin.tpa().deny(p);
            case "back" -> back(p);
            case "rtp" -> plugin.rtp().rtp(p);
            case "claim" -> claim(p);
            case "unclaim" -> unclaim(p);
            case "trust" -> trust(p, a);
            case "untrust" -> untrust(p, a);
            case "claiminfo" -> claimInfo(p);
            case "claims" -> claimsList(p);
            case "ah" -> ah(p, a);
            case "sell" -> sell(p, a);
            default -> { return false; }
        }
        return true;
    }

    // ---------- economy ----------

    private boolean balance(CommandSender sender, String[] a) {
        if (a.length >= 1) {
            UUID id = plugin.db().uuidByName(a[0]);
            if (id == null) { Msg.error(sender, "Unknown player."); return true; }
            Msg.info(sender, a[0] + "'s balance: " + plugin.economy().format(plugin.economy().balance(id)));
            return true;
        }
        if (!(sender instanceof Player p)) { Msg.error(sender, "Specify a player."); return true; }
        Msg.info(p, "Balance: " + plugin.economy().format(plugin.economy().balance(p.getUniqueId())));
        return true;
    }

    private void pay(Player p, String[] a) {
        if (a.length < 2) { Msg.error(p, "Usage: /pay <player> <amount>"); return; }
        Player target = plugin.getServer().getPlayerExact(a[0]);
        if (target == null) { Msg.error(p, "Player not online."); return; }
        if (target.equals(p)) { Msg.error(p, "You can't pay yourself."); return; }
        double amount = parseAmount(a[1]);
        double min = plugin.getConfig().getDouble("economy.pay-minimum", 1.0);
        if (amount < min) { Msg.error(p, "Minimum is " + plugin.economy().format(min) + "."); return; }
        if (!plugin.economy().withdraw(p.getUniqueId(), amount)) {
            Msg.error(p, "Insufficient funds."); return;
        }
        plugin.economy().deposit(target.getUniqueId(), amount);
        Msg.ok(p, "Sent " + plugin.economy().format(amount) + " to " + target.getName() + ".");
        Msg.ok(target, "Received " + plugin.economy().format(amount) + " from " + p.getName() + ".");
    }

    private boolean baltop(CommandSender sender) {
        List<Database.TopEntry> top = plugin.db().topBalances(10);
        Msg.plain(sender, "== Richest players ==", NamedTextColor.GOLD);
        int i = 1;
        for (Database.TopEntry e : top) {
            sender.sendMessage(Component.text("#" + i + " " + e.name() + " - "
                    + plugin.economy().format(e.balance()), NamedTextColor.YELLOW));
            i++;
        }
        if (top.isEmpty()) Msg.info(sender, "No data yet.");
        return true;
    }

    private boolean eco(CommandSender sender, String[] a) {
        if (!sender.hasPermission("survivalcore.admin")) { Msg.error(sender, "No permission."); return true; }
        if (a.length < 3) { Msg.error(sender, "Usage: /eco <give|take|set> <player> <amount>"); return true; }
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(a[1]);
        UUID id = target.getUniqueId();
        plugin.db().ensurePlayer(id, a[1],
                plugin.getConfig().getDouble("economy.starting-balance", 100));
        double amount = parseAmount(a[2]);
        switch (a[0].toLowerCase()) {
            case "give" -> plugin.economy().deposit(id, amount);
            case "take" -> plugin.economy().withdraw(id, amount);
            case "set" -> plugin.economy().set(id, amount);
            default -> { Msg.error(sender, "Use give / take / set."); return true; }
        }
        Msg.ok(sender, a[1] + " balance is now "
                + plugin.economy().format(plugin.economy().balance(id)) + ".");
        return true;
    }

    // ---------- homes ----------

    private int homeLimit(Player p) {
        int limit = plugin.getConfig().getInt("homes.default-limit", 3);
        for (int n = 100; n >= 1; n--) {
            if (p.hasPermission("survivalcore.homes.limit." + n)) { limit = Math.max(limit, n); break; }
        }
        return limit;
    }

    private void setHome(Player p, String[] a) {
        String home = a.length >= 1 ? a[0].toLowerCase() : "home";
        boolean isNew = plugin.db().getHome(p.getUniqueId(), home) == null;
        if (isNew && plugin.db().homeCount(p.getUniqueId()) >= homeLimit(p)) {
            Msg.error(p, "Home limit reached (" + homeLimit(p) + "). Delete one or rank up.");
            return;
        }
        plugin.db().setHome(p.getUniqueId(), home, p.getLocation());
        Msg.ok(p, "Home '" + home + "' set.");
    }

    private void home(Player p, String[] a) {
        List<String> homes = plugin.db().homeNames(p.getUniqueId());
        if (homes.isEmpty()) { Msg.error(p, "You have no homes. /sethome"); return; }
        String home = a.length >= 1 ? a[0].toLowerCase() : homes.get(0);
        Location loc = plugin.db().getHome(p.getUniqueId(), home);
        if (loc == null) { Msg.error(p, "No home named '" + home + "'. /homes"); return; }
        plugin.teleports().teleport(p, loc, "home '" + home + "'");
    }

    private void delHome(Player p, String[] a) {
        String home = a.length >= 1 ? a[0].toLowerCase() : "home";
        Msg.ok(p, plugin.db().deleteHome(p.getUniqueId(), home)
                ? "Home '" + home + "' deleted." : "No such home.");
    }

    private void listHomes(Player p) {
        List<String> homes = plugin.db().homeNames(p.getUniqueId());
        Msg.info(p, homes.isEmpty() ? "You have no homes."
                : "Homes (" + homes.size() + "/" + homeLimit(p) + "): " + String.join(", ", homes));
    }

    // ---------- warps ----------

    private void setWarp(Player p, String[] a) {
        if (!p.hasPermission("survivalcore.admin")) { Msg.error(p, "No permission."); return; }
        if (a.length < 1) { Msg.error(p, "Usage: /setwarp <name>"); return; }
        plugin.db().setWarp(a[0], p.getLocation());
        Msg.ok(p, "Warp '" + a[0].toLowerCase() + "' set.");
    }

    private void delWarp(Player p, String[] a) {
        if (!p.hasPermission("survivalcore.admin")) { Msg.error(p, "No permission."); return; }
        if (a.length < 1) { Msg.error(p, "Usage: /delwarp <name>"); return; }
        Msg.ok(p, plugin.db().deleteWarp(a[0]) ? "Warp deleted." : "No such warp.");
    }

    private void warp(Player p, String[] a) {
        if (a.length < 1) { listWarps(p); return; }
        Location loc = plugin.db().getWarp(a[0]);
        if (loc == null) { Msg.error(p, "No warp named '" + a[0].toLowerCase() + "'."); return; }
        plugin.teleports().teleport(p, loc, "warp '" + a[0].toLowerCase() + "'");
    }

    private void listWarps(Player p) {
        List<String> warps = plugin.db().warpNames();
        Msg.info(p, warps.isEmpty() ? "No warps set." : "Warps: " + String.join(", ", warps));
    }

    // ---------- spawn / back ----------

    private void spawn(Player p) {
        Location loc = plugin.db().getSpawn();
        if (loc == null) loc = p.getWorld().getSpawnLocation();
        plugin.teleports().teleport(p, loc, "spawn");
    }

    private void setSpawn(Player p) {
        if (!p.hasPermission("survivalcore.admin")) { Msg.error(p, "No permission."); return; }
        plugin.db().setSpawn(p.getLocation());
        Msg.ok(p, "Server spawn set.");
    }

    private void back(Player p) {
        Location loc = plugin.teleports().getBack(p);
        if (loc == null) { Msg.error(p, "Nowhere to go back to."); return; }
        plugin.teleports().teleport(p, loc, "your last location");
    }

    // ---------- claims ----------

    private void claim(Player p) {
        if (plugin.claims().ownerAt(p.getLocation()) != null) {
            Msg.error(p, "This chunk is already claimed."); return;
        }
        if (plugin.claims().claim(p)) {
            Msg.ok(p, "Chunk claimed. Claims: " + plugin.db().claimCount(p.getUniqueId())
                    + "/" + plugin.claims().maxClaims(p));
        } else {
            Msg.error(p, "Claim limit reached (" + plugin.claims().maxClaims(p) + ").");
        }
    }

    private void unclaim(Player p) {
        Msg.ok(p, plugin.claims().unclaim(p)
                ? "Chunk unclaimed." : "You don't own this chunk.");
    }

    private void trust(Player p, String[] a) {
        if (a.length < 1) { Msg.error(p, "Usage: /trust <player>"); return; }
        UUID target = plugin.db().uuidByName(a[0]);
        if (target == null) { Msg.error(p, "Unknown player (must have joined before)."); return; }
        plugin.db().addTrust(p.getUniqueId(), target);
        Msg.ok(p, a[0] + " can now build in all your claims.");
    }

    private void untrust(Player p, String[] a) {
        if (a.length < 1) { Msg.error(p, "Usage: /untrust <player>"); return; }
        UUID target = plugin.db().uuidByName(a[0]);
        if (target == null) { Msg.error(p, "Unknown player."); return; }
        Msg.ok(p, plugin.db().removeTrust(p.getUniqueId(), target)
                ? a[0] + " is no longer trusted." : a[0] + " wasn't trusted.");
    }

    private void claimInfo(Player p) {
        UUID owner = plugin.claims().ownerAt(p.getLocation());
        if (owner == null) { Msg.info(p, "This chunk is unclaimed."); return; }
        String name = plugin.db().nameByUuid(owner);
        Msg.info(p, "Owned by " + (name == null ? "someone" : name)
                + (owner.equals(p.getUniqueId()) ? " (you)" : "") + ".");
    }

    private void claimsList(Player p) {
        List<String> trusted = plugin.db().trustedNames(p.getUniqueId());
        Msg.info(p, "Your claims: " + plugin.db().claimCount(p.getUniqueId())
                + "/" + plugin.claims().maxClaims(p)
                + " | Trusted: " + (trusted.isEmpty() ? "none" : String.join(", ", trusted)));
    }

    // ---------- auction house ----------

    private void ah(Player p, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("sell")) {
            sell(p, java.util.Arrays.copyOfRange(a, 1, a.length));
            return;
        }
        if (a.length >= 1 && (a[0].equalsIgnoreCase("mine") || a[0].equalsIgnoreCase("me"))) {
            plugin.auctionGui().open(p, 0, true);
            return;
        }
        plugin.auctionGui().open(p, 0, false);
    }

    private void sell(Player p, String[] a) {
        if (a.length < 1) { Msg.error(p, "Usage: /sell <price> (holding the item)"); return; }
        plugin.auctions().sell(p, parseAmount(a[0]));
    }

    private void tpa(Player p, String[] a, boolean here) {
        if (a.length < 1) { Msg.error(p, "Usage: /" + (here ? "tpahere" : "tpa") + " <player>"); return; }
        Player target = plugin.getServer().getPlayerExact(a[0]);
        if (target == null) { Msg.error(p, "Player not online."); return; }
        plugin.tpa().request(p, target, here);
    }

    // ---------- helpers ----------

    private double parseAmount(String s) {
        try { return Math.max(0, Double.parseDouble(s)); }
        catch (NumberFormatException e) { return 0; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            if (name.equals("home") || name.equals("delhome")) {
                if (sender instanceof Player p) out.addAll(plugin.db().homeNames(p.getUniqueId()));
            } else if (name.equals("warp") || name.equals("delwarp")) {
                out.addAll(plugin.db().warpNames());
            } else if (name.equals("pay") || name.equals("tpa") || name.equals("tpahere")
                    || name.equals("trust") || name.equals("untrust")) {
                for (Player pl : plugin.getServer().getOnlinePlayers()) out.add(pl.getName());
            } else if (name.equals("eco")) {
                out.addAll(List.of("give", "take", "set"));
            }
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }
}
