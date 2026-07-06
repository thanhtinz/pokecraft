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
            plugin.partyUi().open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "party" -> plugin.partyUi().open(player);
            case "pc" -> {
                if (inBattle(player)) return true;
                plugin.pcUi().open(player, 0);
            }
            case "nickname" -> nickname(player, args);
            case "release" -> release(player, args);
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
            case "reload" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                plugin.reloadConfig();
                plugin.species().load();
                player.sendMessage(Component.text("PokeCraft reloaded.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "/poke [party|pc|nickname <slot> <name>|release <party|pc> <n>|give <ball>|spawn <species> [lvl]|heal|reload]",
                    NamedTextColor.YELLOW));
        }
        return true;
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
        if (plugin.battles().get(player) != null) {
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
            out.addAll(List.of("party", "pc", "nickname", "release", "give", "spawn", "heal", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            plugin.species().all().forEach(s -> out.add(s.id));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (PokeballItem.BallType t : PokeballItem.BallType.values()) out.add(t.name().toLowerCase(Locale.ROOT));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("release")) {
            out.addAll(List.of("party", "pc"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("nickname")) {
            out.addAll(List.of("1", "2", "3", "4", "5", "6"));
        }
        return out;
    }
}
