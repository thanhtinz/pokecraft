package dev.thanhtin.pokecraft.bedrock;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.Battle;
import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.party.PlayerParty;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import dev.thanhtin.pokecraft.ui.PlayerPickerGui;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Floodgate integration via reflection (soft-depend).
 * Bedrock players get a native SimpleForm battle menu instead of a chest GUI.
 */
public class BedrockSupport {
    private final PokeCraftPlugin plugin;
    private boolean floodgate;
    private Object floodgateApi;
    private Method mIsFloodgatePlayer;
    private Method mSendForm;

    public BedrockSupport(PokeCraftPlugin plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            mIsFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            mSendForm = apiClass.getMethod("sendForm", UUID.class,
                    Class.forName("org.geysermc.cumulus.form.Form"));
            floodgate = true;
            plugin.getLogger().info("[OK] Floodgate hooked - native Bedrock forms enabled");
        } catch (Exception e) {
            floodgate = false;
            plugin.getLogger().info("[OK] Floodgate not found - Bedrock players will use chest GUIs via Geyser");
        }
    }

    public boolean isBedrock(Player player) {
        if (!floodgate) return false;
        try {
            return (boolean) mIsFloodgatePlayer.invoke(floodgateApi, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean formsEnabled(Player player) {
        return floodgate && plugin.getConfig().getBoolean("bedrock.use-forms", true) && isBedrock(player);
    }

    /** @return true if a native form was sent (caller should skip the chest GUI) */
    public boolean tryOpenBattleForm(Player player, Battle battle) {
        if (!formsEnabled(player)) return false;
        try {
            PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
            PokemonSpecies wildSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
            PokemonInstance mine = battle.playerPokemon;
            PokemonInstance wild = battle.wildPokemon;

            List<Runnable> actions = new ArrayList<>();
            Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleForm.getMethod("builder").invoke(null);
            Class<?> builderClass = builder.getClass();

            invoke(builderClass, builder, "title", mine.displayName(mySpecies) + " vs " + wild.displayName(wildSpecies));
            invoke(builderClass, builder, "content",
                    "Your HP: " + mine.currentHp + "/" + mine.maxHp(mySpecies) + statusSuffix(mine)
                            + "\nWild HP: " + wild.currentHp + "/" + wild.maxHp(wildSpecies) + statusSuffix(wild));
            boolean anyPp = false;
            for (String moveId : mine.moves) {
                MoveData m = plugin.species().getMove(moveId);
                if (m == null) continue;
                int pp = mine.ppFor(m);
                if (pp <= 0) continue;
                anyPp = true;
                invoke(builderClass, builder, "button",
                        m.name + " (" + m.type + " " + m.power + ") PP " + pp + "/" + m.pp);
                actions.add(() -> plugin.battles().useMove(player, moveId));
            }
            if (!anyPp) {
                invoke(builderClass, builder, "button", "Struggle (no PP left)");
                actions.add(() -> plugin.battles().useMove(player, BattleManager.STRUGGLE_ID));
            }
            invoke(builderClass, builder, "button", "Switch Pokemon");
            actions.add(() -> {
                if (!tryOpenSwitchForm(player, battle, false)) {
                    plugin.battleUi().openSwitchMenu(player, battle, false);
                }
            });
            invoke(builderClass, builder, "button", "Run");
            actions.add(() -> plugin.battles().flee(player));

            sendForm(player, simpleForm, builder, builderClass, actions);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Bedrock form failed, falling back to chest GUI: " + e.getMessage());
            return false;
        }
    }

    /** @return true if a native switch form was sent */
    public boolean tryOpenSwitchForm(Player player, Battle battle, boolean forced) {
        if (!formsEnabled(player)) return false;
        try {
            List<Runnable> actions = new ArrayList<>();
            Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleForm.getMethod("builder").invoke(null);
            Class<?> builderClass = builder.getClass();

            invoke(builderClass, builder, "title", forced ? "Choose your next pokemon" : "Switch pokemon");
            invoke(builderClass, builder, "content", forced
                    ? "Your pokemon fainted - pick a replacement."
                    : "Switching gives the wild pokemon a free hit.");
            PlayerParty party = plugin.parties().get(player);
            for (int i = 0; i < PlayerParty.SIZE; i++) {
                PokemonInstance p = party.get(i);
                if (p == null || p.currentHp <= 0 || p == battle.playerPokemon) continue;
                PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
                final int slot = i;
                invoke(builderClass, builder, "button",
                        p.displayName(species) + " Lv." + p.level + " (" + p.currentHp + "/" + p.maxHp(species) + ")");
                actions.add(() -> plugin.battles().switchPokemon(player, slot));
            }
            if (!forced) {
                invoke(builderClass, builder, "button", "Back");
                actions.add(() -> plugin.battleUi().open(player, battle));
            }

            sendForm(player, simpleForm, builder, builderClass, actions);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Bedrock switch form failed, falling back to chest GUI: " + e.getMessage());
            return false;
        }
    }

    /**
     * Native Bedrock main menu (mirrors the chest hub in MainMenuGui). Renders as
     * a SimpleForm button list on mobile instead of a translated chest GUI.
     * @return true if a native form was sent (caller should skip the chest GUI)
     */
    public boolean tryOpenMenuForm(Player player) {
        if (!formsEnabled(player)) return false;
        try {
            List<Runnable> actions = new ArrayList<>();
            Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleForm.getMethod("builder").invoke(null);
            Class<?> builderClass = builder.getClass();

            long balance = plugin.economy().balance(player.getUniqueId());
            invoke(builderClass, builder, "title", "PokeCraft Menu");
            invoke(builderClass, builder, "content",
                    "Balance: " + plugin.economy().format(balance));

            addButton(builderClass, builder, actions, "Party",
                    () -> plugin.partyUi().open(player));
            addButton(builderClass, builder, actions, "PC Box",
                    () -> { if (!menuBlocked(player)) plugin.pcUi().open(player, 0); });
            addButton(builderClass, builder, actions, "Pokemart",
                    () -> { if (!menuBlocked(player)) plugin.shop().open(player); });
            addButton(builderClass, builder, actions, "Daycare",
                    () -> { if (!menuBlocked(player)) plugin.daycareUi().open(player); });

            String challenger = plugin.pvp().pendingChallengerName(player);
            addButton(builderClass, builder, actions,
                    challenger != null ? "Accept duel vs " + challenger : "PvP Duel",
                    () -> {
                        if (menuBlocked(player)) return;
                        if (plugin.pvp().pendingChallengerName(player) != null) plugin.pvp().accept(player);
                        else plugin.playerPickerUi().open(player, PlayerPickerGui.Purpose.DUEL);
                    });

            String trader = plugin.trades().pendingRequesterName(player);
            addButton(builderClass, builder, actions,
                    trader != null ? "Accept trade from " + trader : "Trade",
                    () -> {
                        if (menuBlocked(player)) return;
                        if (plugin.trades().pendingRequesterName(player) != null) plugin.trades().accept(player);
                        else plugin.playerPickerUi().open(player, PlayerPickerGui.Purpose.TRADE);
                    });

            addButton(builderClass, builder, actions, "Pokedex",
                    () -> plugin.pokedexUi().open(player, 0));
            addButton(builderClass, builder, actions, "Leaderboards",
                    () -> plugin.leaderboardUi().open(player));
            addButton(builderClass, builder, actions, "Get PokeMap",
                    () -> plugin.minimap().give(player));
            addButton(builderClass, builder, actions, "Minigames",
                    () -> plugin.minigamesUi().open(player));
            addButton(builderClass, builder, actions, "Activities",
                    () -> plugin.activitiesUi().open(player));

            var guild = plugin.guilds().guildOf(player);
            addButton(builderClass, builder, actions,
                    guild != null ? "Guild: " + guild.name() : "Guilds",
                    () -> plugin.guildUi().open(player));
            addButton(builderClass, builder, actions, "Rank",
                    () -> plugin.rankUi().open(player));
            addButton(builderClass, builder, actions, "Dungeon",
                    () -> { if (!menuBlocked(player)) plugin.dungeons().start(player); });
            addButton(builderClass, builder, actions, "Send Money",
                    () -> { if (!menuBlocked(player)) plugin.playerPickerUi().open(player, PlayerPickerGui.Purpose.PAY); });

            if (player.hasPermission("pokecraft.admin")) {
                addButton(builderClass, builder, actions, "OP Setup",
                        () -> plugin.adminUi().open(player));
            }

            sendForm(player, simpleForm, builder, builderClass, actions);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Bedrock menu form failed, falling back to chest GUI: " + e.getMessage());
            return false;
        }
    }

    /**
     * A native "radar" popup for Bedrock/mobile: lists nearby wild pokemon and
     * players with a compass direction and distance. Bedrock can't render an
     * off-hand map as a corner minimap (that's a Java-client feature), so this
     * form is the mobile minimap.
     * @return true if the form was sent
     */
    public boolean tryOpenRadarForm(Player player) {
        if (!formsEnabled(player)) return false;
        try {
            List<Runnable> actions = new ArrayList<>();
            Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleForm.getMethod("builder").invoke(null);
            Class<?> builderClass = builder.getClass();

            int radius = plugin.getConfig().getInt("minimap.radius", 100);
            List<String> lines = new ArrayList<>();
            record Blip(double dist, String text) {}
            List<Blip> blips = new ArrayList<>();
            for (org.bukkit.entity.Entity ent : player.getNearbyEntities(radius, radius, radius)) {
                if (plugin.entities().isWild(ent)) {
                    var inst = plugin.entities().readData(ent);
                    PokemonSpecies sp = inst == null ? null : plugin.species().getSpecies(inst.speciesId);
                    String name = sp != null ? inst.displayName(sp) : "Wild pokemon";
                    double d = player.getLocation().distance(ent.getLocation());
                    blips.add(new Blip(d, "§c▲ " + name + "  §7" + Math.round(d)
                            + "m " + bearing(player.getLocation(), ent.getLocation())));
                } else if (ent instanceof Player other && !other.equals(player)) {
                    double d = player.getLocation().distance(ent.getLocation());
                    blips.add(new Blip(d, "§9● " + other.getName() + "  §7"
                            + Math.round(d) + "m " + bearing(player.getLocation(), ent.getLocation())));
                }
            }
            blips.sort((a, b) -> Double.compare(a.dist(), b.dist()));
            for (Blip b : blips) lines.add(b.text());

            invoke(builderClass, builder, "title", "PokeMap Radar");
            String content = lines.isEmpty()
                    ? "Nothing nearby within " + radius + " blocks."
                    : String.join("\n", lines);
            invoke(builderClass, builder, "content",
                    "§cWild pokemon  §9Players  §7(within " + radius + "m)\n\n" + content);
            invoke(builderClass, builder, "button", "Refresh");
            actions.add(() -> tryOpenRadarForm(player));
            invoke(builderClass, builder, "button", "Close");
            actions.add(() -> {});

            sendForm(player, simpleForm, builder, builderClass, actions);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Bedrock radar form failed: " + e.getMessage());
            return false;
        }
    }

    /** 8-point compass direction from one location to another (N = -Z). */
    private String bearing(org.bukkit.Location from, org.bukkit.Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) deg += 360;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.round(deg / 45.0) % 8];
    }

    private boolean menuBlocked(Player player) {
        if (plugin.battles().get(player) != null || plugin.pvp().get(player) != null) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "Finish your battle first.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        return false;
    }

    private void addButton(Class<?> builderClass, Object builder, List<Runnable> actions,
                           String label, Runnable action) throws Exception {
        invoke(builderClass, builder, "button", label);
        actions.add(action);
    }

    private void sendForm(Player player, Class<?> simpleForm, Object builder,
                          Class<?> builderClass, List<Runnable> actions) throws Exception {
        Method validResult = findMethod(builderClass, "validResultHandler", Consumer.class);
        Consumer<Object> handler = response -> {
            try {
                int idx = (int) response.getClass().getMethod("clickedButtonId").invoke(response);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (idx >= 0 && idx < actions.size()) actions.get(idx).run();
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("[WARN] Bedrock form response failed: " + ex.getMessage());
            }
        };
        validResult.invoke(builder, handler);

        // Without close/invalid handlers, dismissing a form without picking a
        // button leaves Floodgate thinking a form is still pending, so the next
        // sendForm is dropped and the menu "only opens once". No-op handlers let
        // the form close cleanly so it can be reopened.
        setNoOpHandler(builderClass, builder, "closedResultHandler");
        setNoOpHandler(builderClass, builder, "invalidResultHandler");
        setNoOpHandler(builderClass, builder, "closedOrInvalidResultHandler");

        Object form = builderClass.getMethod("build").invoke(builder);
        mSendForm.invoke(floodgateApi, player.getUniqueId(), form);
    }

    /** Attach a do-nothing handler for a builder callback, whatever its functional type. */
    private void setNoOpHandler(Class<?> builderClass, Object builder, String methodName) {
        for (Method m : builderClass.getMethods()) {
            if (!m.getName().equals(methodName) || m.getParameterCount() != 1) continue;
            Class<?> type = m.getParameterTypes()[0];
            if (!type.isInterface()) continue;
            try {
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                        type.getClassLoader(), new Class[]{type}, (p, method, a) -> null);
                m.invoke(builder, proxy);
            } catch (Exception ignored) {
                // handler couldn't be attached - not fatal, form still sends
            }
            return;
        }
    }

    private String statusSuffix(PokemonInstance p) {
        return p.status == null ? "" : " [" + p.status.tag + "]";
    }

    private void invoke(Class<?> cls, Object obj, String name, String arg) throws Exception {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == String.class) {
                m.invoke(obj, arg);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private Method findMethod(Class<?> cls, String name, Class<?> paramType) throws Exception {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(paramType)) {
                return m;
            }
        }
        throw new NoSuchMethodException(name);
    }
}
