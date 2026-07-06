package dev.thanhtin.pokecraft.bedrock;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.Battle;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
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

    /** @return true if a native form was sent (caller should skip the chest GUI) */
    public boolean tryOpenBattleForm(Player player, Battle battle) {
        if (!floodgate || !plugin.getConfig().getBoolean("bedrock.use-forms", true)) return false;
        if (!isBedrock(player)) return false;
        try {
            PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
            PokemonSpecies wildSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
            PokemonInstance mine = battle.playerPokemon;
            PokemonInstance wild = battle.wildPokemon;

            List<String> buttonMoves = new ArrayList<>();
            Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleForm.getMethod("builder").invoke(null);
            Class<?> builderClass = builder.getClass();

            invoke(builderClass, builder, "title", mine.displayName(mySpecies) + " vs " + wild.displayName(wildSpecies));
            invoke(builderClass, builder, "content",
                    "Your HP: " + mine.currentHp + "/" + mine.maxHp(mySpecies)
                            + "\nWild HP: " + wild.currentHp + "/" + wild.maxHp(wildSpecies));
            for (String moveId : mine.moves) {
                MoveData m = plugin.species().getMove(moveId);
                if (m == null) continue;
                invoke(builderClass, builder, "button", m.name + " (" + m.type + " " + m.power + ")");
                buttonMoves.add(moveId);
            }
            invoke(builderClass, builder, "button", "Run");

            Method validResult = findMethod(builderClass, "validResultHandler", Consumer.class);
            Consumer<Object> handler = response -> {
                try {
                    int idx = (int) response.getClass().getMethod("clickedButtonId").invoke(response);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (idx >= 0 && idx < buttonMoves.size()) {
                            plugin.battles().useMove(player, buttonMoves.get(idx));
                        } else {
                            plugin.battles().flee(player);
                        }
                    });
                } catch (Exception ex) {
                    plugin.getLogger().warning("[WARN] Bedrock form response failed: " + ex.getMessage());
                }
            };
            validResult.invoke(builder, handler);

            Object form = builderClass.getMethod("build").invoke(builder);
            mSendForm.invoke(floodgateApi, player.getUniqueId(), form);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[WARN] Bedrock form failed, falling back to chest GUI: " + e.getMessage());
            return false;
        }
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
