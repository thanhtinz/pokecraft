package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.storage.StorageManager;
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

/** Guild panel: browse/join/create when guildless, or manage your guild. */
public class GuildGui implements Listener {
    private static final long[] DEPOSITS = {1000, 5000, 25000};

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyJoin;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyDeposit;
    private final java.util.Set<java.util.UUID> leaveArmed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public GuildGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyJoin = new NamespacedKey(plugin, "guild_join");
        this.keyAction = new NamespacedKey(plugin, "guild_action");
        this.keyDeposit = new NamespacedKey(plugin, "guild_deposit");
    }

    public void open(Player player) {
        if (openForm(player)) return;
        StorageManager.GuildRow guild = plugin.guilds().guildOf(player);
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text(guild == null ? "Guilds" : "Guild: " + guild.name()));
        holder.inventory = inv;

        if (guild == null) {
            List<StorageManager.GuildRow> guilds = plugin.storage().allGuilds(45);
            for (int i = 0; i < guilds.size() && i < 45; i++) {
                StorageManager.GuildRow g = guilds.get(i);
                int members = plugin.storage().guildMemberNames(g.id()).size();
                ItemStack item = new ItemStack(Material.WHITE_BANNER);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(g.name(), NamedTextColor.AQUA));
                meta.lore(List.of(
                        Component.text("Members: " + members, NamedTextColor.GRAY),
                        Component.text("Bank: " + plugin.economy().format(g.bank()), NamedTextColor.GRAY),
                        Component.text("Click to join", NamedTextColor.YELLOW)));
                meta.getPersistentDataContainer().set(keyJoin, PersistentDataType.STRING, g.name());
                item.setItemMeta(meta);
                inv.setItem(i, item);
            }
            long cost = plugin.getConfig().getLong("guild.create-cost", 5000);
            ItemStack create = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = create.getItemMeta();
            meta.displayName(Component.text("Create a guild", NamedTextColor.GREEN));
            meta.lore(List.of(Component.text("Cost: " + plugin.economy().format(cost), NamedTextColor.GRAY),
                    Component.text("Click to name and create", NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "create");
            create.setItemMeta(meta);
            inv.setItem(49, create);
        } else {
            boolean owner = guild.owner().equals(player.getUniqueId());
            List<String> members = plugin.storage().guildMemberNames(guild.id());
            ItemStack info = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta infoMeta = info.getItemMeta();
            infoMeta.displayName(Component.text(guild.name(), NamedTextColor.GOLD));
            infoMeta.lore(List.of(
                    Component.text("Bank: " + plugin.economy().format(guild.bank()), NamedTextColor.GRAY),
                    Component.text("Members: " + members.size(), NamedTextColor.GRAY),
                    Component.text("You are the " + (owner ? "leader" : "member"), NamedTextColor.GRAY)));
            info.setItemMeta(infoMeta);
            inv.setItem(4, info);

            int slot = 9;
            for (String name : members) {
                if (slot >= 36) break;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta meta = head.getItemMeta();
                meta.displayName(Component.text(name
                        + (guild.owner().equals(player.getUniqueId()) && name.equals(player.getName())
                        ? " (you, leader)" : ""), NamedTextColor.AQUA));
                head.setItemMeta(meta);
                inv.setItem(slot++, head);
            }

            int ds = 45;
            for (long amount : DEPOSITS) {
                boolean afford = plugin.economy().balance(player.getUniqueId()) >= amount;
                ItemStack item = new ItemStack(afford ? Material.GOLD_INGOT : Material.GRAY_DYE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text("Deposit " + plugin.economy().format(amount),
                        afford ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
                if (afford) meta.getPersistentDataContainer().set(keyDeposit, PersistentDataType.LONG, amount);
                item.setItemMeta(meta);
                inv.setItem(ds++, item);
            }

            boolean armed = leaveArmed.contains(player.getUniqueId());
            ItemStack leave = new ItemStack(Material.BARRIER);
            ItemMeta leaveMeta = leave.getItemMeta();
            leaveMeta.displayName(Component.text(armed
                    ? (owner ? "Click again to DISBAND" : "Click again to LEAVE")
                    : (owner ? "Disband guild" : "Leave guild"), NamedTextColor.RED));
            leaveMeta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "leave");
            leave.setItemMeta(leaveMeta);
            inv.setItem(53, leave);
        }

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private boolean openForm(Player player) {
        if (!plugin.bedrock().isBedrock(player)) return false;

        StorageManager.GuildRow guild = plugin.guilds().guildOf(player);
        java.util.List<dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton> buttons =
                new java.util.ArrayList<>();
        String title;
        String content;

        if (guild == null) {
            title = "Guilds";
            content = "§7Join an existing guild or create your own";
            List<StorageManager.GuildRow> guilds = plugin.storage().allGuilds(45);
            for (int i = 0; i < guilds.size() && i < 45; i++) {
                StorageManager.GuildRow g = guilds.get(i);
                int members = plugin.storage().guildMemberNames(g.id()).size();
                final String gname = g.name();
                buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                        "Join " + g.name() + " (Members: " + members
                                + ", Bank: " + plugin.economy().format(g.bank()) + ")",
                        () -> performAction(player, gname, null, null)));
            }
            long cost = plugin.getConfig().getLong("guild.create-cost", 5000);
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    "Create a guild (Cost: " + plugin.economy().format(cost) + ")",
                    () -> performAction(player, null, "create", null)));
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        } else {
            boolean owner = guild.owner().equals(player.getUniqueId());
            List<String> members = plugin.storage().guildMemberNames(guild.id());
            title = "Guild: " + guild.name();
            StringBuilder sb = new StringBuilder();
            sb.append("§6").append(guild.name()).append("\n");
            sb.append("§7Bank: ").append(plugin.economy().format(guild.bank())).append("\n");
            sb.append("§7Members: ").append(members.size()).append("\n");
            sb.append("§7You are the ").append(owner ? "leader" : "member").append("\n");
            for (String name : members) {
                sb.append("§b").append(name)
                        .append(guild.owner().equals(player.getUniqueId()) && name.equals(player.getName())
                                ? " (you, leader)" : "").append("\n");
            }
            content = sb.toString();

            for (long amount : DEPOSITS) {
                boolean afford = plugin.economy().balance(player.getUniqueId()) >= amount;
                if (afford) {
                    final long amt = amount;
                    buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                            "Deposit " + plugin.economy().format(amount),
                            () -> performAction(player, null, null, amt)));
                } else {
                    buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                            "Deposit " + plugin.economy().format(amount) + " (can't afford)", null));
                }
            }

            boolean armed = leaveArmed.contains(player.getUniqueId());
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton(
                    armed
                            ? (owner ? "Click again to DISBAND" : "Click again to LEAVE")
                            : (owner ? "Disband guild" : "Leave guild"),
                    () -> performAction(player, null, "leave", null)));
            buttons.add(new dev.thanhtin.pokecraft.bedrock.BedrockSupport.FormButton("Close", null));
        }

        return plugin.bedrock().openForm(player, title, content, buttons);
    }

    private void performAction(Player player, String join, String action, Long deposit) {
        java.util.UUID id = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (join != null) {
                leaveArmed.remove(id);
                plugin.guilds().join(player, join);
                open(player);
            } else if ("create".equals(action)) {
                leaveArmed.remove(id);
                plugin.guildNameInput().open(player);
            } else if (deposit != null) {
                plugin.guilds().deposit(player, deposit);
                open(player);
            } else if ("leave".equals(action)) {
                if (leaveArmed.remove(id)) {
                    plugin.guilds().leave(player);
                    open(player);
                } else {
                    leaveArmed.add(id);
                    open(player);
                }
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String join = pdc.get(keyJoin, PersistentDataType.STRING);
        String action = pdc.get(keyAction, PersistentDataType.STRING);
        Long deposit = pdc.get(keyDeposit, PersistentDataType.LONG);

        performAction(player, join, action, deposit);
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                leaveArmed.remove(player.getUniqueId());
            }
        });
    }
}
