package dev.thanhtin.survivalcore.admin;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.crate.CrateManager.Crate;
import dev.thanhtin.survivalcore.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin settings as a clickable menu instead of typed commands. Placing NPCs,
 * binding crate/vault blocks, giving keys and toggling features are all done
 * here. Block-binding uses arm-then-right-click: pick the action in the menu,
 * the menu closes, then right-click the target block.
 */
public class AdminPanel implements Listener {

    private final SurvivalCore plugin;
    private final NamespacedKey keyAction;
    // admin -> a pending "right-click a block/NPC" action
    private final Map<UUID, String> pending = new HashMap<>();

    public AdminPanel(SurvivalCore plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "admin_action");
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = plugin.getServer().createInventory(holder, 54,
                Component.text("SurvivalCore Admin"));
        holder.inv = inv;

        ItemStack pane = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", null);
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        inv.setItem(4, named(new ItemStack(Material.NETHER_STAR), "&b&lAdmin Panel",
                List.of("&7Click a tool. For block tools,", "&7the menu closes and you",
                        "&7right-click the target block.")));

        // NPCs
        inv.setItem(10, action(Material.LEATHER_CHESTPLATE, "&bPlace Kit Master",
                List.of("&7Spawns a Kit Master NPC here."), "npc:kit_master"));
        inv.setItem(11, action(Material.WRITABLE_BOOK, "&6Place Job Board",
                List.of("&7Spawns a Job Board NPC here."), "npc:job_board"));
        inv.setItem(12, action(Material.GOLD_INGOT, "&aPlace Banker",
                List.of("&7Spawns a Banker NPC here."), "npc:banker"));
        inv.setItem(13, action(Material.EMERALD, "&2Place Shopkeeper",
                List.of("&7Spawns a Shop NPC here."), "npc:shop"));
        inv.setItem(14, action(Material.BARRIER, "&cRemove NPC",
                List.of("&7Then right-click the NPC to remove."), "removenpc"));

        // Vault + remove-binding tools
        inv.setItem(15, action(Material.ENDER_CHEST, "&5Bind Vault Block",
                List.of("&7Then right-click a block", "&7to open players' vaults."), "vaultbind"));
        inv.setItem(16, action(Material.SHEARS, "&eUnbind Block",
                List.of("&7Then right-click a crate/vault", "&7block to remove its binding."), "removeblock"));

        // Crates (one icon per configured crate)
        int slot = 28;
        for (Crate crate : plugin.crates().all()) {
            if (slot > 34) break;
            inv.setItem(slot++, action(Material.CHEST, crate.display() + " &7crate",
                    List.of("&aLeft-click&7: bind this block as the crate",
                            "&eRight-click&7: give yourself a key"),
                    "crate:" + crate.id()));
        }

        // Giftcode generator
        inv.setItem(40, action(Material.NAME_TAG, "&dCreate Giftcode",
                List.of("&7Generates a code with the default", "&7reward. Share it; players /redeem."),
                "giftcode"));

        // Feature toggles
        inv.setItem(45, toggle("Scoreboard", "scoreboard.enabled"));
        inv.setItem(46, toggle("Chat format", "chat.enabled"));
        inv.setItem(47, toggle("Tab list", "tab.enabled"));

        player.openInventory(inv);
    }

    // ---------- click routing ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String act = clicked.getItemMeta().getPersistentDataContainer()
                .get(keyAction, PersistentDataType.STRING);
        if (act == null) return;

        if (act.startsWith("npc:")) {
            String role = act.substring(4);
            long id = plugin.npcs().create(role, defaultNpcName(role), p.getLocation());
            p.closeInventory();
            if (id > 0) Msg.ok(p, "Placed " + role + " NPC here.");
            else Msg.error(p, "Could not place the NPC.");
            return;
        }
        if (act.startsWith("crate:")) {
            String id = act.substring(6);
            if (e.isRightClick()) {
                plugin.crates().giveKeys(p, id, 1);
                Msg.ok(p, "Gave you a " + id + " key.");
            } else {
                pending.put(p.getUniqueId(), "cratebind:" + id);
                p.closeInventory();
                Msg.info(p, "Right-click the block to make it the " + id + " crate.");
            }
            return;
        }
        if (act.startsWith("toggle:")) {
            String path = act.substring(7);
            boolean now = !plugin.getConfig().getBoolean(path, true);
            plugin.getConfig().set(path, now);
            plugin.saveConfig();
            Msg.ok(p, path + " = " + (now ? "ON" : "OFF"));
            open(p); // refresh
            return;
        }
        if (act.equals("giftcode")) {
            String code = plugin.giftcodes().createDefault();
            Msg.ok(p, "Created giftcode: " + code);
            Msg.info(p, "Share it - players claim with /redeem " + code);
            return;
        }
        switch (act) {
            case "vaultbind" -> {
                pending.put(p.getUniqueId(), "vaultbind");
                p.closeInventory();
                Msg.info(p, "Right-click a block to make it a vault.");
            }
            case "removeblock" -> {
                pending.put(p.getUniqueId(), "removeblock");
                p.closeInventory();
                Msg.info(p, "Right-click a crate or vault block to unbind it.");
            }
            case "removenpc" -> {
                pending.put(p.getUniqueId(), "removenpc");
                p.closeInventory();
                Msg.info(p, "Right-click the NPC you want to remove.");
            }
            default -> { }
        }
    }

    // ---------- pending block actions ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        String act = pending.get(e.getPlayer().getUniqueId());
        if (act == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        e.setCancelled(true);
        pending.remove(e.getPlayer().getUniqueId());
        Player p = e.getPlayer();
        Location loc = block.getLocation();

        if (act.startsWith("cratebind:")) {
            plugin.db().setCrateBlock(loc, act.substring("cratebind:".length()));
            Msg.ok(p, "Bound this block as a crate.");
        } else if (act.equals("vaultbind")) {
            plugin.db().addVaultBlock(loc);
            Msg.ok(p, "Bound this block as a vault.");
        } else if (act.equals("removeblock")) {
            if (plugin.db().removeCrateBlock(loc)) Msg.ok(p, "Removed the crate binding.");
            else if (plugin.db().removeVaultBlock(loc)) Msg.ok(p, "Removed the vault binding.");
            else Msg.warn(p, "That block isn't a crate or vault.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        String act = pending.get(e.getPlayer().getUniqueId());
        if (!"removenpc".equals(act)) return;
        Entity entity = e.getRightClicked();
        if (!plugin.npcs().isNpc(entity)) return;
        e.setCancelled(true);
        pending.remove(e.getPlayer().getUniqueId());
        Long id = plugin.npcs().idOf(entity);
        if (id != null && plugin.npcs().remove(id)) Msg.ok(e.getPlayer(), "Removed NPC #" + id + ".");
        else Msg.error(e.getPlayer(), "Could not remove that NPC.");
    }

    // ---------- item helpers ----------

    private ItemStack toggle(String label, String path) {
        boolean on = plugin.getConfig().getBoolean(path, true);
        ItemStack item = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
        return action(item, (on ? "&a" : "&7") + label + ": " + (on ? "ON" : "OFF"),
                List.of("&7Click to toggle."), "toggle:" + path);
    }

    private ItemStack action(Material mat, String name, List<String> lore, String action) {
        return action(new ItemStack(mat), name, lore, action);
    }

    private ItemStack action(ItemStack item, String name, List<String> lore, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.legacy(name).decoration(TextDecoration.ITALIC, false));
        if (lore != null) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.legacy(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.legacy(name).decoration(TextDecoration.ITALIC, false));
        if (lore != null) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.legacy(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String defaultNpcName(String role) {
        return switch (role) {
            case "kit_master" -> "&b&lKit Master";
            case "job_board" -> "&6&lJob Board";
            case "banker" -> "&a&lBanker";
            case "shop" -> "&2&lShopkeeper";
            default -> role;
        };
    }
}
