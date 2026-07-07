package dev.thanhtin.pokecraft.social;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Player guilds: create, join, leave, a shared bank, and a wealth ranking. */
public class GuildManager {
    private final PokeCraftPlugin plugin;

    public GuildManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public StorageManager.GuildRow guildOf(Player player) {
        int id = plugin.storage().guildOf(player.getUniqueId());
        return id == 0 ? null : plugin.storage().getGuild(id);
    }

    public void create(Player player, String name) {
        if (plugin.storage().guildOf(player.getUniqueId()) != 0) {
            player.sendMessage(Component.text("You are already in a guild.", NamedTextColor.RED));
            return;
        }
        name = name.trim();
        if (name.length() < 3 || name.length() > 16 || !name.matches("[A-Za-z0-9_ ]+")) {
            player.sendMessage(Component.text("Guild name must be 3-16 letters/numbers.", NamedTextColor.RED));
            return;
        }
        if (plugin.storage().getGuildByName(name) != null) {
            player.sendMessage(Component.text("That guild name is taken.", NamedTextColor.RED));
            return;
        }
        long cost = plugin.getConfig().getLong("guild.create-cost", 5000);
        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Creating a guild costs "
                    + plugin.economy().format(cost) + ".", NamedTextColor.RED));
            return;
        }
        StorageManager.GuildRow guild = plugin.storage().createGuild(name, player.getUniqueId());
        if (guild == null) {
            plugin.economy().deposit(player.getUniqueId(), cost); // refund
            player.sendMessage(Component.text("Could not create the guild.", NamedTextColor.RED));
            return;
        }
        plugin.storage().addGuildMember(player.getUniqueId(), guild.id(), player.getName());
        plugin.getServer().broadcast(Component.text(player.getName() + " founded the guild \""
                + name + "\"!", NamedTextColor.AQUA));
    }

    public void join(Player player, String name) {
        if (plugin.storage().guildOf(player.getUniqueId()) != 0) {
            player.sendMessage(Component.text("Leave your current guild first.", NamedTextColor.RED));
            return;
        }
        StorageManager.GuildRow guild = plugin.storage().getGuildByName(name);
        if (guild == null) {
            player.sendMessage(Component.text("No guild named \"" + name + "\".", NamedTextColor.RED));
            return;
        }
        int max = plugin.getConfig().getInt("guild.max-members", 20);
        if (plugin.storage().guildMemberNames(guild.id()).size() >= max) {
            player.sendMessage(Component.text("That guild is full.", NamedTextColor.RED));
            return;
        }
        plugin.storage().addGuildMember(player.getUniqueId(), guild.id(), player.getName());
        player.sendMessage(Component.text("You joined \"" + guild.name() + "\".", NamedTextColor.GREEN));
    }

    public void leave(Player player) {
        StorageManager.GuildRow guild = guildOf(player);
        if (guild == null) {
            player.sendMessage(Component.text("You're not in a guild.", NamedTextColor.RED));
            return;
        }
        if (guild.owner().equals(player.getUniqueId())) {
            // owner leaving disbands the guild (bank refunded to owner)
            if (guild.bank() > 0) plugin.economy().deposit(player.getUniqueId(), guild.bank());
            plugin.storage().deleteGuild(guild.id());
            player.sendMessage(Component.text("You disbanded \"" + guild.name() + "\""
                    + (guild.bank() > 0 ? " (bank " + plugin.economy().format(guild.bank())
                    + " refunded)" : "") + ".", NamedTextColor.YELLOW));
            return;
        }
        plugin.storage().removeGuildMember(player.getUniqueId());
        player.sendMessage(Component.text("You left \"" + guild.name() + "\".", NamedTextColor.GRAY));
    }

    public void deposit(Player player, long amount) {
        StorageManager.GuildRow guild = guildOf(player);
        if (guild == null) {
            player.sendMessage(Component.text("You're not in a guild.", NamedTextColor.RED));
            return;
        }
        if (amount <= 0 || !plugin.economy().withdraw(player.getUniqueId(), amount)) {
            player.sendMessage(Component.text("Not enough money.", NamedTextColor.RED));
            return;
        }
        plugin.storage().setGuildBank(guild.id(), guild.bank() + amount);
        player.sendMessage(Component.text("Deposited " + plugin.economy().format(amount)
                + " into the guild bank.", NamedTextColor.GREEN));
    }
}
