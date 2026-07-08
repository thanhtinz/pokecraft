package dev.thanhtin.pokecraft.integration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.npc.NpcManager.NpcType;
import dev.thanhtin.pokecraft.storage.StorageManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

/**
 * Citizens integration (soft-depend). Bind a Citizens NPC to a PokeCraft
 * service role with {@code /poke npc citizens <type>} (select the NPC first with
 * {@code /npc select}); right-clicking it then runs that role - so you get
 * Citizens' skins/pathing with PokeCraft's healer/shop/daycare/pc/tutor.
 * Trainer/gym roles keep using the built-in villager NPCs. Only instantiated
 * when Citizens is installed.
 */
public class CitizensHook implements Listener {

    private static final String META_KEY = "citizens_npcs";

    private final PokeCraftPlugin plugin;
    private final Gson gson = new Gson();
    private final Map<Integer, String> bindings = new HashMap<>(); // Citizens NPC id -> NpcType

    public CitizensHook(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Service roles a Citizens NPC may take (trainer/gym stay villager-based). */
    private boolean isServiceType(NpcType type) {
        return switch (type) {
            case HEALER, VENDOR, DAYCARE, PC, TUTOR -> true;
            case TRAINER, GYM -> false;
        };
    }

    public void bindSelected(Player player, String typeName) {
        NpcType type;
        try { type = NpcType.valueOf(typeName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text("Type must be healer, vendor, daycare, pc or tutor.",
                    NamedTextColor.RED));
            return;
        }
        if (!isServiceType(type)) {
            player.sendMessage(Component.text("Citizens NPCs support healer/vendor/daycare/pc/tutor. "
                    + "Use the built-in NPC for trainer/gym.", NamedTextColor.RED));
            return;
        }
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            player.sendMessage(Component.text("Select a Citizens NPC first with /npc select.",
                    NamedTextColor.RED));
            return;
        }
        bindings.put(npc.getId(), type.name());
        save();
        player.sendMessage(Component.text("Bound NPC #" + npc.getId() + " (" + npc.getName()
                + ") to " + type.name() + ".", NamedTextColor.GREEN));
    }

    public void unbindSelected(Player player) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (npc == null) {
            player.sendMessage(Component.text("Select a Citizens NPC first with /npc select.",
                    NamedTextColor.RED));
            return;
        }
        if (bindings.remove(npc.getId()) != null) {
            save();
            player.sendMessage(Component.text("Unbound NPC #" + npc.getId() + ".", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("That NPC isn't bound.", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent e) {
        String typeName = bindings.get(e.getNPC().getId());
        if (typeName == null) return;
        NpcType type;
        try { type = NpcType.valueOf(typeName); }
        catch (IllegalArgumentException ex) { return; }
        Player player = e.getClicker();
        StorageManager.NpcRow row = new StorageManager.NpcRow(
                type.name(), e.getNPC().getName(), null);
        plugin.npcs().handle(player, type, e.getNPC().getEntity(), row);
    }

    private void load() {
        String json = plugin.storage().getMeta(META_KEY, null);
        if (json == null || json.isBlank()) return;
        try {
            Map<String, String> saved = gson.fromJson(
                    json, new TypeToken<Map<String, String>>() {}.getType());
            if (saved != null) {
                for (Map.Entry<String, String> en : saved.entrySet()) {
                    try { bindings.put(Integer.parseInt(en.getKey()), en.getValue()); }
                    catch (NumberFormatException ignored) {}
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void save() {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<Integer, String> en : bindings.entrySet()) {
            out.put(String.valueOf(en.getKey()), en.getValue());
        }
        plugin.storage().setMeta(META_KEY, gson.toJson(out));
    }
}
