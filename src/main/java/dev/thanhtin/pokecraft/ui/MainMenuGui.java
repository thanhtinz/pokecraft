package dev.thanhtin.pokecraft.ui;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-game hub menu so nothing requires typing commands. Every player gets a
 * menu item in the hotbar on join; right-clicking (or tapping, on Bedrock)
 * opens this panel. Chest GUIs work on Java and on mobile via Geyser.
 */
public class MainMenuGui implements Listener {
    private static final int SLOT_PARTY = 10;
    private static final int SLOT_PC = 11;
    private static final int SLOT_SHOP = 12;
    private static final int SLOT_DAYCARE = 13;
    private static final int SLOT_RIDE = 14;
    private static final int SLOT_DUEL = 15;
    private static final int SLOT_TOP = 16;
    private static final int SLOT_BALANCE = 4;
    private static final int SLOT_MARRY = 22;
    private static final int SLOT_DEX = 21;
    private static final int SLOT_TRADE = 23;
    private static final int SLOT_ACTIVITIES = 30;
    private static final int SLOT_GUILD = 32;
    private static final int SLOT_RANK = 33;
    private static final int SLOT_DUNGEON = 34;
    private static final int SLOT_ADMIN = 8;
    private static final int SLOT_MAP = 5;
    private static final int SLOT_MINIGAMES = 6;
    private static final int SLOT_WALK = 7;

    private final PokeCraftPlugin plugin;
    private final NamespacedKey keyMenuItem;
    /** players who armed the divorce button (need a second click) */
    private final java.util.Set<java.util.UUID> divorceArmed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public MainMenuGui(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        this.keyMenuItem = new NamespacedKey(plugin, "menu_item");
    }

    // ---------- the hotbar menu item ----------

    public ItemStack createMenuItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("PokeCraft Menu", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Right-click / tap to open", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(keyMenuItem, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMenuItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(keyMenuItem, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("menu.give-item", true)) return;
        Player player = e.getPlayer();
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMenuItem(item)) return;
        }
        int slot = plugin.getConfig().getInt("menu.item-slot", 8);
        if (slot >= 0 && slot <= 8 && player.getInventory().getItem(slot) == null) {
            player.getInventory().setItem(slot, createMenuItem());
        } else {
            player.getInventory().addItem(createMenuItem());
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMenuItem(e.getItem())) return;
        e.setCancelled(true);
        open(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isMenuItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                divorceArmed.remove(player.getUniqueId());
            }
        });
    }

    /** Keep the menu item out of chests and crafting grids. */
    @EventHandler
    public void onMove(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) return; // handled below
        if (isMenuItem(e.getCurrentItem()) || isMenuItem(e.getCursor())) {
            if (e.getInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                e.setCancelled(true);
            }
        }
    }

    // ---------- the hub panel ----------

    public void open(Player player) {
        if (plugin.bedrock().tryOpenMenuForm(player)) return;
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 36,
                Component.text("PokeCraft Menu"));
        holder.inventory = inv;

        long balance = plugin.economy().balance(player.getUniqueId());
        inv.setItem(SLOT_BALANCE, item(Material.GOLD_NUGGET,
                "Balance: " + plugin.economy().format(balance), NamedTextColor.GOLD,
                List.of("Win battles to earn PokeDollars", "Click to send money to a player")));

        inv.setItem(SLOT_MAP, item(Material.FILLED_MAP, "Get PokeMap", NamedTextColor.AQUA,
                List.of("A minimap that works on mobile", "Shows wild pokemon + players")));
        inv.setItem(SLOT_MINIGAMES, item(Material.OAK_SIGN, "Minigames", NamedTextColor.GOLD,
                List.of("Casino, trivia, tic-tac-toe", "and connect four")));
        boolean walking = plugin.walkers().isFollowing(player);
        inv.setItem(SLOT_WALK, item(Material.LEAD,
                walking ? "Buddy: out with you" : "Send out your Buddy",
                walking ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                walking ? List.of("Your lead pokemon walks beside you",
                                "Tap it in-world to ride it", "Sneak-tap it to put it away")
                        : List.of("Send your lead pokemon out to walk", "Then tap it to ride")));

        inv.setItem(SLOT_PARTY, item(Material.PLAYER_HEAD, "Party", NamedTextColor.AQUA,
                List.of("View, reorder and manage", "your 6 party pokemon")));
        inv.setItem(SLOT_PC, item(Material.ENDER_CHEST, "PC Box", NamedTextColor.LIGHT_PURPLE,
                List.of("Stored pokemon", "Click one there to withdraw")));
        inv.setItem(SLOT_SHOP, item(Material.EMERALD, "Pokemart", NamedTextColor.GREEN,
                List.of("Buy pokeballs, potions", "and evolution stones")));
        inv.setItem(SLOT_DAYCARE, item(Material.TURTLE_EGG, "Daycare", NamedTextColor.YELLOW,
                List.of("Drop pokemon off to level up", "Two compatible ones may breed")));
        inv.setItem(SLOT_RIDE, item(Material.SADDLE,
                plugin.rides().isRiding(player) ? "Dismount" : "Ride your Buddy", NamedTextColor.AQUA,
                List.of("Tap your walking pokemon to ride it",
                        "FLYING types can fly - sneak to dismount")));

        String challenger = plugin.pvp().pendingChallengerName(player);
        inv.setItem(SLOT_DUEL, item(Material.IRON_SWORD,
                challenger != null ? "Accept duel vs " + challenger : "PvP Duel", NamedTextColor.RED,
                challenger != null
                        ? List.of(challenger + " challenged you!", "Click to accept")
                        : List.of("Challenge a nearby player", "Winner earns money")));

        inv.setItem(SLOT_TOP, item(Material.GOLD_BLOCK, "Leaderboards", NamedTextColor.GOLD,
                List.of("Top catchers, richest,", "battle and duel winners")));

        inv.setItem(SLOT_DEX, item(Material.BOOK, "Pokedex", NamedTextColor.RED,
                List.of("Your seen/caught progress", "across the whole dex")));

        String trader = plugin.trades().pendingRequesterName(player);
        inv.setItem(SLOT_TRADE, item(Material.EMERALD, trader != null
                        ? "Accept trade from " + trader : "Trade", NamedTextColor.GREEN,
                trader != null ? List.of(trader + " wants to trade!", "Click to accept")
                        : List.of("Swap pokemon with", "a nearby player")));

        String proposer = plugin.marriage().pendingProposerName(player);
        UUID spouse = plugin.marriage().spouseOf(player.getUniqueId());
        String marryLabel;
        List<String> marryLore;
        if (proposer != null) {
            marryLabel = "Accept proposal from " + proposer;
            marryLore = List.of(proposer + " proposed to you!", "Click to accept ("
                    + "/poke marry deny to refuse)");
        } else if (spouse != null) {
            String name = plugin.getServer().getOfflinePlayer(spouse).getName();
            if (divorceArmed.contains(player.getUniqueId())) {
                marryLabel = "Click again to divorce " + (name != null ? name : "");
                marryLore = List.of("This ends the marriage");
            } else {
                marryLabel = "Married to " + (name != null ? name : "someone");
                marryLore = List.of("Bonus battle EXP while together online", "Click to divorce");
            }
        } else {
            marryLabel = "Marry a player";
            marryLore = List.of("Propose to another player", "Couples earn bonus EXP");
        }
        inv.setItem(SLOT_MARRY, item(Material.POPPY, marryLabel, NamedTextColor.LIGHT_PURPLE, marryLore));

        var guild = plugin.guilds().guildOf(player);
        inv.setItem(SLOT_GUILD, item(Material.WHITE_BANNER,
                guild != null ? "Guild: " + guild.name() : "Guilds", NamedTextColor.AQUA,
                guild != null ? List.of("Manage your guild + bank")
                        : List.of("Join or create a guild", "with your friends")));
        int rp = plugin.ranks().points(player.getUniqueId());
        inv.setItem(SLOT_RANK, item(Material.DIAMOND,
                "Rank: " + plugin.ranks().tier(rp).name() + " (" + rp + ")", NamedTextColor.BLUE,
                List.of("Seasonal PvP rank ladder", "Win duels to climb")));

        inv.setItem(SLOT_DUNGEON, item(Material.NETHERITE_SWORD, "Dungeon", NamedTextColor.DARK_RED,
                List.of("Fight waves of trainers", "and a boss for big rewards")));

        boolean daily = plugin.daily().canClaim(player);
        inv.setItem(SLOT_ACTIVITIES, item(Material.CLOCK,
                daily ? "Activities - daily reward ready!" : "Activities", NamedTextColor.YELLOW,
                daily ? List.of("Daily check-in + quests", "Your daily reward is waiting!")
                        : List.of("Daily check-in + quests", "Fish, catch and battle for rewards")));

        if (player.hasPermission("pokecraft.admin")) {
            inv.setItem(SLOT_ADMIN, item(Material.COMMAND_BLOCK, "OP Setup", NamedTextColor.RED,
                    List.of("Configure the whole plugin", "in-game (ops only)")));
        }

        GuiFiller.fill(inv);
        player.openInventory(inv);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) loreComponents.add(Component.text(line, NamedTextColor.GRAY));
        meta.lore(loreComponents);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        int raw = e.getRawSlot();

        boolean inBattle = plugin.battles().get(player) != null || plugin.pvp().get(player) != null;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (raw) {
                case SLOT_PARTY -> plugin.partyUi().open(player);
                case SLOT_PC -> {
                    if (blocked(player, inBattle)) return;
                    plugin.pcUi().open(player, 0);
                }
                case SLOT_SHOP -> {
                    if (blocked(player, inBattle)) return;
                    plugin.shop().open(player);
                }
                case SLOT_DAYCARE -> {
                    if (blocked(player, inBattle)) return;
                    plugin.daycareUi().open(player);
                }
                case SLOT_RIDE -> {
                    if (blocked(player, inBattle)) return;
                    player.closeInventory();
                    if (plugin.rides().isRiding(player)) {
                        plugin.rides().dismount(player);
                    } else if (plugin.walkers().isFollowing(player)) {
                        plugin.rides().rideFollower(player);
                    } else {
                        plugin.walkers().toggle(player); // send buddy out
                        player.sendMessage(Component.text(
                                "Tap your pokemon to hop on and ride it.", NamedTextColor.AQUA));
                    }
                }
                case SLOT_DUEL -> {
                    if (blocked(player, inBattle)) return;
                    if (plugin.pvp().pendingChallengerName(player) != null) {
                        player.closeInventory();
                        plugin.pvp().accept(player);
                    } else {
                        plugin.playerPickerUi().open(player, PlayerPickerGui.Purpose.DUEL);
                    }
                }
                case SLOT_TOP -> plugin.leaderboardUi().open(player);
                case SLOT_DEX -> plugin.pokedexUi().open(player, 0);
                case SLOT_MAP -> { player.closeInventory(); plugin.minimap().give(player); }
                case SLOT_MINIGAMES -> plugin.minigamesUi().open(player);
                case SLOT_WALK -> { plugin.walkers().toggle(player); open(player); }
                case SLOT_ACTIVITIES -> plugin.activitiesUi().open(player);
                case SLOT_GUILD -> plugin.guildUi().open(player);
                case SLOT_RANK -> plugin.rankUi().open(player);
                case SLOT_DUNGEON -> {
                    if (blocked(player, inBattle)) return;
                    player.closeInventory();
                    plugin.dungeons().start(player);
                }
                case SLOT_ADMIN -> {
                    if (player.hasPermission("pokecraft.admin")) plugin.adminUi().open(player);
                }
                case SLOT_TRADE -> {
                    if (blocked(player, inBattle)) return;
                    if (plugin.trades().pendingRequesterName(player) != null) {
                        player.closeInventory();
                        plugin.trades().accept(player);
                    } else {
                        plugin.playerPickerUi().open(player, PlayerPickerGui.Purpose.TRADE);
                    }
                }
                case SLOT_BALANCE -> {
                    if (blocked(player, inBattle)) return;
                    plugin.playerPickerUi().open(player, PlayerPickerGui.Purpose.PAY);
                }
                case SLOT_MARRY -> {
                    if (plugin.marriage().pendingProposerName(player) != null) {
                        divorceArmed.remove(player.getUniqueId());
                        player.closeInventory();
                        plugin.marriage().accept(player);
                    } else if (plugin.marriage().spouseOf(player.getUniqueId()) == null) {
                        divorceArmed.remove(player.getUniqueId());
                        plugin.playerPickerUi().open(player, PlayerPickerGui.Purpose.MARRY);
                    } else if (divorceArmed.remove(player.getUniqueId())) {
                        plugin.marriage().divorce(player);
                        open(player);
                    } else {
                        divorceArmed.add(player.getUniqueId());
                        open(player);
                    }
                }
                default -> {}
            }
        });
    }

    private boolean blocked(Player player, boolean inBattle) {
        if (inBattle) {
            player.sendMessage(Component.text("Finish your battle first.", NamedTextColor.RED));
            player.closeInventory();
            return true;
        }
        return false;
    }
}
