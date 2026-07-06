package dev.thanhtin.pokecraft.command;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.capture.PokeballItem;
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

public class PokeCommand implements TabExecutor {
    private final PokeCraftPlugin plugin;

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
                for (PokemonInstance p : plugin.parties().get(player).party()) {
                    PokemonSpecies s = plugin.species().getSpecies(p.speciesId);
                    if (s != null) p.currentHp = p.maxHp(s);
                }
                plugin.parties().saveParty(player.getUniqueId());
                player.sendMessage(Component.text("Party fully healed.", NamedTextColor.GREEN));
            }
            case "reload" -> {
                if (!player.hasPermission("pokecraft.admin")) return noPerm(player);
                plugin.reloadConfig();
                plugin.species().load();
                player.sendMessage(Component.text("PokeCraft reloaded.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "/poke [party|give <ball>|spawn <species> [lvl]|heal|reload]", NamedTextColor.YELLOW));
        }
        return true;
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
            out.addAll(List.of("party", "give", "spawn", "heal", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            plugin.species().all().forEach(s -> out.add(s.id));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (PokeballItem.BallType t : PokeballItem.BallType.values()) out.add(t.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
