package dev.thanhtin.pokecraft.command;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.capture.PokeballItem;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PokeCommand implements TabExecutor {
    private static final long RELEASE_CONFIRM_MILLIS = 15_000;

    private final PokeCraftPlugin plugin;
    /** player -> "party:2"/"pc:14" + request time, for release confirmation */
    private final Map<UUID, PendingRelease> pendingRelease = new ConcurrentHashMap<>();

    private record PendingRelease(String key, long at) {}

    public PokeCommand(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[ERR] Player only.");
            return true;
        }
        if (args.length == 0) {
            plugin.mainMenu().open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> plugin.mainMenu().open(player);
            case "party" -> plugin.partyUi().open(player);
            case "pc" -> {
                if (inBattle(player)) return true;
                plugin.pcUi().open(player, 0);
            }
            case "nickname" -> nickname(player, args);
            case "movetutor", "relearn" -> {
                if (inBattle(player)) return true;
                plugin.moveTutorUi().open(player, parseInt(args.length > 1 ? args[1] : "1", 1) - 1);
            }
            case "release" -> release(player, args);
            case "balance", "bal", "money" -> player.sendMessage(Component.text(
                    "Balance: " + plugin.economy().format(plugin.economy().balance(player.getUniqueId())),
                    NamedTextColor.GOLD));
            case "pay" -> pay(player, args);
            case "shop" -> {
                if (inBattle(player)) return true;
                plugin.shop().open(player);
            }
            case "top" -> top(player, args);
            case "dex", "pokedex" -> plugin.pokedexUi().open(player, 0);
            case "duel" -> duel(player, args);
            case "marry" -> marry(player, args);
            case "divorce" -> plugin.marriage().divorce(player);
            case "trade" -> trade(player, args);
            case "daycare" -> daycare(player, args);
            case "ride" -> ride(player, args);
            case "npc" -> npc(player, args);
            case "admin", "setup" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                plugin.adminUi().open(player);
            }
            case "give" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                PokeballItem.BallType type = PokeballItem.BallType.POKE_BALL;
                if (args.length > 1) {
                    try { type = PokeballItem.BallType.valueOf(args[1].toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException ignored) {}
                }
                player.getInventory().addItem(plugin.pokeballs().create(type, 16));
                player.sendMessage(Component.text("Given 16x " + type.display, NamedTextColor.GREEN));
            }
            case "spawn" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /poke spawn <species> [level]", NamedTextColor.RED));
                    return true;
                }
                PokemonSpecies species = plugin.species().getSpecies(args[1].toLowerCase(Locale.ROOT));
                if (species == null) {
                    player.sendMessage(Component.text("Unknown species: " + args[1], NamedTextColor.RED));
                    return true;
                }
                int level = args.length > 2 ? parseInt(args[2], 5) : 5;
                PokemonInstance instance = PokemonInstance.generate(species, level,
                        plugin.getConfig().getInt("battle.shiny-rate", 4096));
                plugin.entities().spawnWild(species, instance, player.getLocation());
                player.sendMessage(Component.text("Spawned " + species.name + " Lv." + level, NamedTextColor.GREEN));
            }
            case "heal" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                if (inBattle(player)) return true;
                for (PokemonInstance p : plugin.parties().get(player).party()) {
                    PokemonSpecies s = plugin.species().getSpecies(p.speciesId);
                    if (s != null) p.heal(s);
                }
                plugin.parties().saveParty(player.getUniqueId());
                player.sendMessage(Component.text("Party fully healed (HP, PP and status).", NamedTextColor.GREEN));
            }
            case "rankreset" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                plugin.ranks().resetSeason(player);
            }
            case "reload" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                plugin.reloadConfig();
                plugin.species().load();
                plugin.fishing().invalidate();
                player.sendMessage(Component.text("PokeCraft reloaded.", NamedTextColor.GREEN));
            }
            case "model", "models" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                model(player, args);
            }
            case "hologram", "holo" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                hologram(player, args);
            }
            default -> {
                player.sendMessage(Component.text(
                        "Tip: everything is in the menu item (right-click / tap the star). "
                        + "Commands are optional.", NamedTextColor.YELLOW));
                player.sendMessage(Component.text(
                        "admin: /poke [give <ball>|spawn <species> [lvl]|heal|reload|rankreset]",
                        NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private void pay(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /poke pay <player> <amount>", NamedTextColor.RED));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You can't pay yourself.", NamedTextColor.RED));
            return;
        }
        long amount = parseInt(args[2], 0);
        if (amount <= 0) {
            player.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
            return;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), amount)) {
            player.sendMessage(Component.text("Not enough money.", NamedTextColor.RED));
            return;
        }
        plugin.economy().deposit(target.getUniqueId(), amount);
        player.sendMessage(Component.text("Sent " + plugin.economy().format(amount) + " to "
                + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text(player.getName() + " sent you "
                + plugin.economy().format(amount) + ".", NamedTextColor.GREEN));
    }

    private void top(Player player, String[] args) {
        String category = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "caught";
        String column = switch (category) {
            case "money", "balance" -> "balance";
            case "wins", "wild" -> "wild_wins";
            case "pvp" -> "pvp_wins";
            default -> "caught";
        };
        String title = switch (column) {
            case "balance" -> "Richest trainers";
            case "wild_wins" -> "Most wild battles won";
            case "pvp_wins" -> "Best duelists";
            default -> "Top catchers";
        };
        var entries = plugin.storage().top(column, 10);
        player.sendMessage(Component.text("=== " + title + " ===", NamedTextColor.GOLD));
        if (entries.isEmpty()) {
            player.sendMessage(Component.text("No entries yet.", NamedTextColor.GRAY));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            String value = column.equals("balance") ? plugin.economy().format(e.value())
                    : String.valueOf(e.value());
            player.sendMessage(Component.text("  " + (i + 1) + ". " + e.name() + " - " + value,
                    NamedTextColor.YELLOW));
        }
        player.sendMessage(Component.text("Categories: /poke top [caught|money|wins|pvp]",
                NamedTextColor.GRAY));
    }

    private void duel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /poke duel <player|accept|deny>", NamedTextColor.RED));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "accept" -> plugin.pvp().accept(player);
            case "deny" -> plugin.pvp().deny(player);
            default -> {
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED));
                    return;
                }
                plugin.pvp().challenge(player, target);
            }
        }
    }

    private void marry(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /poke marry <player|accept|deny>", NamedTextColor.RED));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "accept" -> plugin.marriage().accept(player);
            case "deny" -> plugin.marriage().deny(player);
            default -> {
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED));
                    return;
                }
                plugin.marriage().propose(player, target);
            }
        }
    }

    private void daycare(Player player, String[] args) {
        if (inBattle(player)) return;
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "deposit" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /poke daycare deposit <slot 1-6>",
                            NamedTextColor.RED));
                    return;
                }
                plugin.daycare().deposit(player, parseInt(args[2], 0) - 1);
            }
            case "withdraw" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /poke daycare withdraw <1|2>",
                            NamedTextColor.RED));
                    return;
                }
                plugin.daycare().withdraw(player, parseInt(args[2], 0) - 1);
            }
            default -> plugin.daycare().status(player);
        }
    }

    private void hologram(Player player, String[] args) {
        if (plugin.holograms() == null) {
            player.sendMessage(Component.text("DecentHolograms is not installed.", NamedTextColor.RED));
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "add", "create" -> {
                String type = args.length > 2 ? args[2] : "caught";
                String title = args.length > 3
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : null;
                plugin.holograms().add(player, type, title);
            }
            case "remove", "delete" -> plugin.holograms().removeNearest(player);
            default -> player.sendMessage(Component.text(
                    "/poke hologram add <money|caught|wins> [title]  |  /poke hologram remove",
                    NamedTextColor.YELLOW));
        }
    }

    private void model(Player player, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "panel";
        switch (sub) {
            case "panel", "gui", "list" -> plugin.modelUi().open(player, 0, false);
            case "coverage" -> {
                int[] c = plugin.models().coverage();
                player.sendMessage(Component.text("Models installed: " + c[0] + "/" + c[1]
                        + (plugin.entities().hasModelEngine() ? "" : " (BetterModel not installed)"),
                        NamedTextColor.YELLOW));
            }
            case "preview" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /poke model preview <blueprint>", NamedTextColor.RED));
                    return;
                }
                plugin.models().preview(player, args[2]);
            }
            case "set" -> {
                if (args.length < 4) {
                    player.sendMessage(Component.text("Usage: /poke model set <species> <blueprint>", NamedTextColor.RED));
                    return;
                }
                String sid = args[2].toLowerCase(Locale.ROOT);
                if (plugin.species().getSpecies(sid) == null) {
                    player.sendMessage(Component.text("Unknown species: " + args[2], NamedTextColor.RED));
                    return;
                }
                plugin.models().setOverride(sid, args[3]);
                player.sendMessage(Component.text("Bound " + sid + " -> blueprint \"" + args[3] + "\".",
                        NamedTextColor.GREEN));
            }
            case "clear" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /poke model clear <species>", NamedTextColor.RED));
                    return;
                }
                plugin.models().clearOverride(args[2].toLowerCase(Locale.ROOT));
                player.sendMessage(Component.text("Cleared model override for " + args[2] + ".",
                        NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "/poke model [panel|coverage|preview <bp>|set <species> <bp>|clear <species>]",
                    NamedTextColor.GRAY));
        }
    }

    private void npc(Player player, String[] args) {
        if (!player.hasPermission("pokecraft.admin")) {
            noPerm(player);
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "Usage: /poke npc <create <healer|vendor|trainer> [level] [name...]|remove>",
                    NamedTextColor.RED));
            return;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            plugin.npcs().removeNearest(player);
            return;
        }
        if (!args[1].equalsIgnoreCase("create") || args.length < 3) {
            player.sendMessage(Component.text(
                    "Usage: /poke npc create <healer|vendor|trainer> [level] [name...]",
                    NamedTextColor.RED));
            return;
        }
        dev.thanhtin.pokecraft.npc.NpcManager.NpcType type;
        try {
            type = dev.thanhtin.pokecraft.npc.NpcManager.NpcType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Unknown NPC type: " + args[2], NamedTextColor.RED));
            return;
        }
        int level = 10;
        int nameFrom = 3;
        if (type == dev.thanhtin.pokecraft.npc.NpcManager.NpcType.TRAINER
                && args.length > 3 && args[3].matches("\\d+")) {
            level = Math.max(2, Math.min(100, Integer.parseInt(args[3])));
            nameFrom = 4;
        }
        String name = args.length > nameFrom
                ? String.join(" ", List.of(args).subList(nameFrom, args.length))
                : switch (type) {
                    case HEALER -> "Nurse Joy";
                    case VENDOR -> "Shop Keeper";
                    case TRAINER -> "Trainer";
                    case GYM -> "Gym Leader";
                    case DAYCARE -> "Day-Care Man";
                    case PC -> "PC Attendant";
                    case TUTOR -> "Move Tutor";
                };
        plugin.npcs().create(player, type, name, level);
    }

    private void trade(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /poke trade <player|accept|deny>", NamedTextColor.RED));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "accept" -> plugin.trades().accept(player);
            case "deny" -> plugin.trades().deny(player);
            default -> {
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED));
                    return;
                }
                plugin.trades().request(player, target);
            }
        }
    }

    private void ride(Player player, String[] args) {
        if (args.length < 2) {
            if (plugin.rides().isRiding(player)) {
                plugin.rides().dismount(player);
            } else {
                player.sendMessage(Component.text("Usage: /poke ride <slot 1-6> (sneak to dismount)",
                        NamedTextColor.RED));
            }
            return;
        }
        plugin.rides().ride(player, parseInt(args[1], 0) - 1);
    }

    private void nickname(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /poke nickname <slot 1-6> <name>", NamedTextColor.RED));
            return;
        }
        int slot = parseInt(args[1], 0) - 1;
        PokemonInstance p = plugin.parties().get(player).get(slot);
        if (p == null) {
            player.sendMessage(Component.text("No pokemon in party slot " + args[1] + ".", NamedTextColor.RED));
            return;
        }
        String name = String.join(" ", List.of(args).subList(2, args.length)).trim();
        if (name.length() > 24) name = name.substring(0, 24);
        p.nickname = name.equalsIgnoreCase("off") ? null : name;
        plugin.parties().saveParty(player.getUniqueId());
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        player.sendMessage(Component.text(species.name + " is now known as "
                + p.displayName(species) + ". (\"off\" clears)", NamedTextColor.GREEN));
    }

    private void release(Player player, String[] args) {
        if (inBattle(player)) return;
        if (args.length < 3 || !(args[1].equalsIgnoreCase("party") || args[1].equalsIgnoreCase("pc"))) {
            player.sendMessage(Component.text("Usage: /poke release <party|pc> <number>", NamedTextColor.RED));
            return;
        }
        boolean fromParty = args[1].equalsIgnoreCase("party");
        int index = parseInt(args[2], 0) - 1;
        PlayerParty party = plugin.parties().get(player);
        PokemonInstance target = fromParty ? party.get(index)
                : (index >= 0 && index < party.pc().size() ? party.pc().get(index) : null);
        if (target == null) {
            player.sendMessage(Component.text("No pokemon at " + args[1] + " #" + args[2]
                    + (fromParty ? "" : " (see /poke pc for the box list)") + ".", NamedTextColor.RED));
            return;
        }
        if (fromParty && party.partySize() <= 1) {
            player.sendMessage(Component.text("You can't release your last party pokemon.", NamedTextColor.RED));
            return;
        }

        String key = (fromParty ? "party:" : "pc:") + index + ":" + target.uuid;
        PendingRelease pending = pendingRelease.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (pending == null || !pending.key().equals(key) || now - pending.at() > RELEASE_CONFIRM_MILLIS) {
            pendingRelease.put(player.getUniqueId(), new PendingRelease(key, now));
            PokemonSpecies species = plugin.species().getSpecies(target.speciesId);
            player.sendMessage(Component.text("Release " + target.displayName(species) + " Lv." + target.level
                    + "? Run the same command again within 15s to confirm.", NamedTextColor.GOLD));
            return;
        }
        pendingRelease.remove(player.getUniqueId());
        PokemonInstance removed = fromParty ? party.removeFromParty(index) : party.removeFromPc(index);
        if (removed == null) return;
        plugin.storage().delete(removed.uuid);
        PokemonSpecies species = plugin.species().getSpecies(removed.speciesId);
        player.sendMessage(Component.text("Bye bye, " + removed.displayName(species) + "!", NamedTextColor.GREEN));
    }

    private boolean inBattle(Player player) {
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null
                || plugin.trades().get(player) != null) {
            player.sendMessage(Component.text("Finish your battle first.", NamedTextColor.RED));
            return true;
        }
        return false;
    }

    private boolean noPerm(Player player) {
        player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
        return true;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("menu", "party", "pc", "dex", "shop", "balance", "pay", "top", "duel", "trade", "marry",
                    "divorce", "daycare", "ride", "nickname", "release", "give", "spawn", "heal", "reload", "npc"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            plugin.species().all().forEach(s -> out.add(s.id));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (PokeballItem.BallType t : PokeballItem.BallType.values()) out.add(t.name().toLowerCase(Locale.ROOT));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("release")) {
            out.addAll(List.of("party", "pc"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("nickname") || args[0].equalsIgnoreCase("ride"))) {
            out.addAll(List.of("1", "2", "3", "4", "5", "6"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            out.addAll(List.of("caught", "money", "wins", "pvp"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("duel") || args[0].equalsIgnoreCase("marry")
                || args[0].equalsIgnoreCase("trade"))) {
            out.addAll(List.of("accept", "deny"));
            plugin.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("daycare")) {
            out.addAll(List.of("status", "deposit", "withdraw"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            out.addAll(List.of("create", "remove"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("create")) {
            out.addAll(List.of("healer", "vendor", "trainer"));
        }
        return out;
    }
}
