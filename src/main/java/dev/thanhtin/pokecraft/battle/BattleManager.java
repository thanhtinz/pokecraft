package dev.thanhtin.pokecraft.battle;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.pokemon.StatusCondition;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BattleManager implements Listener {
    public static final String STRUGGLE_ID = "struggle";

    private final PokeCraftPlugin plugin;
    private final Map<UUID, Battle> battles = new ConcurrentHashMap<>();

    public BattleManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public Battle get(Player player) { return battles.get(player.getUniqueId()); }

    public boolean isWildInBattle(UUID entityId) {
        for (Battle b : battles.values()) {
            if (b.wildEntity != null && b.wildEntity.getUniqueId().equals(entityId)) return true;
        }
        return false;
    }

    public void startWildBattle(Player player, Entity wildEntity) {
        Battle existing = battles.get(player.getUniqueId());
        if (existing != null) {
            // re-punching reopens the current battle menu
            if (existing.awaitingSwitch) plugin.battleUi().openSwitchMenu(player, existing, true);
            else plugin.battleUi().open(player, existing);
            return;
        }
        if (plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("You are in a duel right now.", NamedTextColor.RED));
            return;
        }
        if (isWildInBattle(wildEntity.getUniqueId())) {
            player.sendMessage(Component.text("That pokemon is already battling someone else.", NamedTextColor.RED));
            return;
        }
        if (plugin.rides().isRiding(player)) plugin.rides().dismount(player);
        PokemonInstance wild = plugin.entities().readData(wildEntity);
        if (wild == null) return;
        PokemonInstance mine = plugin.parties().get(player).firstAlive();
        if (mine == null) {
            player.sendMessage(Component.text(
                    "You have no usable pokemon. Catch one with a pokeball first (/poke give).",
                    NamedTextColor.RED));
            return;
        }
        PokemonSpecies wildSpecies = plugin.species().getSpecies(wild.speciesId);
        Battle battle = new Battle(player.getUniqueId(), mine, wild, wildEntity);
        battles.put(player.getUniqueId(), battle);
        player.sendMessage(Component.text("A wild " + wild.displayName(wildSpecies)
                + " Lv." + wild.level + " appeared!", NamedTextColor.YELLOW));
        plugin.battleUi().open(player, battle);
    }

    /** Starts a battle against an NPC trainer's team. */
    public void startTrainerBattle(Player player, Entity npcEntity,
                                   List<PokemonInstance> team, String trainerName, long reward) {
        Battle existing = battles.get(player.getUniqueId());
        if (existing != null) {
            if (existing.awaitingSwitch) plugin.battleUi().openSwitchMenu(player, existing, true);
            else plugin.battleUi().open(player, existing);
            return;
        }
        if (plugin.pvp().get(player) != null) {
            player.sendMessage(Component.text("You are in a duel right now.", NamedTextColor.RED));
            return;
        }
        if (team == null || team.isEmpty()) return;
        if (isWildInBattle(npcEntity.getUniqueId())) {
            player.sendMessage(Component.text(trainerName + " is already battling someone else.",
                    NamedTextColor.RED));
            return;
        }
        if (plugin.rides().isRiding(player)) plugin.rides().dismount(player);
        PokemonInstance mine = plugin.parties().get(player).firstAlive();
        if (mine == null) {
            player.sendMessage(Component.text("You have no usable pokemon.", NamedTextColor.RED));
            return;
        }
        Battle battle = new Battle(player.getUniqueId(), mine, team.get(0), npcEntity);
        battle.npcTeam = team;
        battle.npcIndex = 0;
        battle.npcName = trainerName;
        battle.npcReward = reward;
        battles.put(player.getUniqueId(), battle);
        PokemonSpecies first = plugin.species().getSpecies(team.get(0).speciesId);
        player.sendMessage(Component.text(trainerName + " challenges you with "
                + team.size() + " pokemon! First up: " + team.get(0).displayName(first)
                + " Lv." + team.get(0).level, NamedTextColor.GOLD));
        plugin.battleUi().open(player, battle);
    }

    private String opponentPrefix(Battle battle) {
        return battle.isTrainerBattle() ? battle.npcName + "'s " : "Wild ";
    }

    /** True if the pokemon has at least one move with PP left. */
    public boolean hasUsableMove(PokemonInstance p) {
        if (p.moves == null) return false;
        for (String id : p.moves) {
            MoveData m = plugin.species().getMove(id);
            if (m != null && p.ppFor(m) > 0) return true;
        }
        return false;
    }

    public void useMove(Player player, String moveId) {
        Battle battle = battles.get(player.getUniqueId());
        if (battle == null || battle.finished || battle.awaitingSwitch) return;
        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        PokemonSpecies wildSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
        if (mySpecies == null || wildSpecies == null) return;

        MoveData myMove;
        if (STRUGGLE_ID.equals(moveId)) {
            if (hasUsableMove(battle.playerPokemon)) return; // struggle only when out of PP
            myMove = plugin.species().getMove(STRUGGLE_ID);
        } else {
            if (battle.playerPokemon.moves == null || !battle.playerPokemon.moves.contains(moveId)) return;
            myMove = plugin.species().getMove(moveId);
            if (myMove != null && battle.playerPokemon.ppFor(myMove) <= 0) {
                player.sendMessage(Component.text("No PP left for that move!", NamedTextColor.RED));
                plugin.battleUi().open(player, battle);
                return;
            }
        }
        if (myMove == null) return;

        MoveData wildMove = pickWildMove(battle.wildPokemon);
        boolean playerFirst = playerActsFirst(battle, mySpecies, wildSpecies, myMove, wildMove);

        if (playerFirst) {
            if (act(player, battle, true, myMove)) return;
            if (wildMove != null && act(player, battle, false, wildMove)) return;
        } else {
            if (wildMove != null && act(player, battle, false, wildMove)) return;
            if (act(player, battle, true, myMove)) return;
        }
        if (endOfTurn(player, battle)) return;
        syncWildEntity(battle);
        plugin.battleUi().open(player, battle);
    }

    private boolean playerActsFirst(Battle battle, PokemonSpecies mySpecies, PokemonSpecies wildSpecies,
                                    MoveData myMove, MoveData wildMove) {
        int myPriority = myMove.priority;
        int wildPriority = wildMove == null ? 0 : wildMove.priority;
        if (myPriority != wildPriority) return myPriority > wildPriority;
        double mySpeed = effectiveSpeed(battle.playerPokemon, mySpecies, battle.playerStages);
        double wildSpeed = effectiveSpeed(battle.wildPokemon, wildSpecies, battle.wildStages);
        return mySpeed >= wildSpeed;
    }

    private double effectiveSpeed(PokemonInstance p, PokemonSpecies species, int[] stages) {
        double speed = p.stat(species, 5) * DamageCalculator.stageMultiplier(stages[5]);
        if (p.status == StatusCondition.PARALYSIS) speed *= 0.5;
        return speed;
    }

    /**
     * One side acts: pre-attack status check, damage, secondary effects, faint check.
     * @return true if the battle ended (or is waiting on a switch)
     */
    private boolean act(Player player, Battle battle, boolean byPlayer, MoveData move) {
        PokemonInstance attacker = byPlayer ? battle.playerPokemon : battle.wildPokemon;
        PokemonInstance defender = byPlayer ? battle.wildPokemon : battle.playerPokemon;
        PokemonSpecies attackerSpecies = plugin.species().getSpecies(attacker.speciesId);
        PokemonSpecies defenderSpecies = plugin.species().getSpecies(defender.speciesId);
        int[] attackerStages = byPlayer ? battle.playerStages : battle.wildStages;
        int[] defenderStages = byPlayer ? battle.wildStages : battle.playerStages;
        String prefix = byPlayer ? "Your " : opponentPrefix(battle);
        NamedTextColor color = byPlayer ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;

        if (!canAct(player, attacker, attackerSpecies, prefix)) return false;

        if (!STRUGGLE_ID.equals(move.id)) attacker.usePp(move);

        DamageCalculator.Result result = DamageCalculator.calculate(
                attacker, attackerSpecies, attackerStages, defender, defenderSpecies, defenderStages, move);

        if (result.missed()) {
            player.sendMessage(Component.text(prefix + attackerSpecies.name + " used "
                    + move.name + " but missed!", NamedTextColor.GRAY));
            return false;
        }

        if (move.category != MoveData.Category.STATUS) {
            defender.currentHp = Math.max(0, defender.currentHp - result.damage());
            StringBuilder msg = new StringBuilder(prefix + attackerSpecies.name + " used " + move.name
                    + " (" + result.damage() + " dmg)");
            if (result.critical()) msg.append(" - Critical hit!");
            if (result.effectiveness() > 1.0) msg.append(" It's super effective!");
            if (result.effectiveness() > 0 && result.effectiveness() < 1.0) msg.append(" Not very effective...");
            if (result.effectiveness() == 0) msg.append(" It had no effect.");
            player.sendMessage(Component.text(msg.toString(), color));
        } else {
            player.sendMessage(Component.text(prefix + attackerSpecies.name + " used " + move.name + "!", color));
        }

        if (result.effectiveness() > 0) {
            applyEffect(player, battle, byPlayer, move, attacker, defender, defenderSpecies);
        }

        if (STRUGGLE_ID.equals(move.id)) {
            int recoil = Math.max(1, attacker.maxHp(attackerSpecies) / 4);
            attacker.currentHp = Math.max(0, attacker.currentHp - recoil);
            player.sendMessage(Component.text(prefix + attackerSpecies.name
                    + " is hit by recoil (" + recoil + " dmg)!", NamedTextColor.GRAY));
        }

        return checkFaints(player, battle);
    }

    /** @return false if the attacker skips its turn (sleep/freeze/paralysis). */
    private boolean canAct(Player player, PokemonInstance attacker, PokemonSpecies species, String prefix) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (attacker.status == StatusCondition.SLEEP) {
            if (attacker.sleepTurns > 0) {
                attacker.sleepTurns--;
                player.sendMessage(Component.text(prefix + species.name + " is fast asleep.", NamedTextColor.GRAY));
                return false;
            }
            attacker.status = null;
            player.sendMessage(Component.text(prefix + species.name + " woke up!", NamedTextColor.YELLOW));
            return true;
        }
        if (attacker.status == StatusCondition.FREEZE) {
            if (rnd.nextInt(100) < 20) {
                attacker.status = null;
                player.sendMessage(Component.text(prefix + species.name + " thawed out!", NamedTextColor.YELLOW));
                return true;
            }
            player.sendMessage(Component.text(prefix + species.name + " is frozen solid!", NamedTextColor.GRAY));
            return false;
        }
        if (attacker.status == StatusCondition.PARALYSIS && rnd.nextInt(100) < 25) {
            player.sendMessage(Component.text(prefix + species.name + " is paralyzed and can't move!",
                    NamedTextColor.GRAY));
            return false;
        }
        return true;
    }

    private void applyEffect(Player player, Battle battle, boolean byPlayer, MoveData move,
                             PokemonInstance attacker, PokemonInstance defender, PokemonSpecies defenderSpecies) {
        MoveData.Effect effect = move.effect;
        if (effect == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        if (effect.stat >= 1 && effect.stat <= 5 && effect.stages != 0) {
            boolean onSelf = effect.target == MoveData.Target.SELF;
            int[] stages = (byPlayer == onSelf) ? battle.playerStages : battle.wildStages;
            PokemonInstance target = onSelf ? attacker : defender;
            PokemonSpecies targetSpecies = plugin.species().getSpecies(target.speciesId);
            int before = stages[effect.stat];
            stages[effect.stat] = Math.max(-6, Math.min(6, before + effect.stages));
            String statName = switch (effect.stat) {
                case 1 -> "Attack"; case 2 -> "Defense"; case 3 -> "Sp. Atk";
                case 4 -> "Sp. Def"; default -> "Speed";
            };
            String who = (byPlayer == onSelf ? "Your " : opponentPrefix(battle)) + targetSpecies.name;
            if (stages[effect.stat] == before) {
                player.sendMessage(Component.text(who + "'s " + statName + " can't go "
                        + (effect.stages > 0 ? "higher" : "lower") + "!", NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text(who + "'s " + statName
                        + (effect.stages > 0 ? " rose!" : " fell!"), NamedTextColor.YELLOW));
            }
        }

        if (effect.status != null && defender.status == null && defender.currentHp > 0
                && rnd.nextInt(100) < effect.statusChance) {
            defender.status = effect.status;
            if (effect.status == StatusCondition.SLEEP) defender.sleepTurns = 1 + rnd.nextInt(3);
            PokemonSpecies ds = plugin.species().getSpecies(defender.speciesId);
            player.sendMessage(Component.text((byPlayer ? opponentPrefix(battle) : "Your ") + ds.name
                    + " was " + effect.status.verb + "!", NamedTextColor.YELLOW));
        }
    }

    /** Residual burn/poison damage at the end of the round. @return true if the battle ended. */
    private boolean endOfTurn(Player player, Battle battle) {
        for (boolean playerSide : new boolean[]{true, false}) {
            PokemonInstance p = playerSide ? battle.playerPokemon : battle.wildPokemon;
            if (p.currentHp <= 0 || p.status == null) continue;
            double fraction = p.status.residualDamage();
            if (fraction <= 0) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            int dmg = Math.max(1, (int) (p.maxHp(species) * fraction));
            p.currentHp = Math.max(0, p.currentHp - dmg);
            player.sendMessage(Component.text((playerSide ? "Your " : opponentPrefix(battle)) + species.name
                    + " is hurt by its " + p.status.tag + " (" + dmg + " dmg)!", NamedTextColor.GRAY));
        }
        return checkFaints(player, battle);
    }

    /** @return true if the battle ended or is now waiting for a switch. */
    private boolean checkFaints(Player player, Battle battle) {
        if (battle.wildPokemon.currentHp <= 0) {
            if (battle.isTrainerBattle() && battle.npcIndex + 1 < battle.npcTeam.size()) {
                handleTrainerNext(player, battle);
            } else {
                endWithVictory(player, battle);
            }
            return true;
        }
        if (battle.playerPokemon.currentHp <= 0) {
            return handlePlayerFaint(player, battle);
        }
        return false;
    }

    /** The trainer's pokemon fainted but more remain: award exp, send out the next one. */
    private void handleTrainerNext(Player player, Battle battle) {
        awardExp(player, battle);
        battle.npcIndex++;
        battle.wildPokemon = battle.npcTeam.get(battle.npcIndex);
        battle.resetWildStages();
        PokemonSpecies next = plugin.species().getSpecies(battle.wildPokemon.speciesId);
        player.sendMessage(Component.text(battle.npcName + " sent out "
                + battle.wildPokemon.displayName(next) + " Lv." + battle.wildPokemon.level + "!",
                NamedTextColor.GOLD));
        plugin.parties().saveParty(player.getUniqueId());
        plugin.battleUi().open(player, battle);
    }

    /** Faint message + exp/level/evolution for the currently fainted opposing pokemon. */
    private void awardExp(Player player, Battle battle) {
        PokemonSpecies faintedSpecies = plugin.species().getSpecies(battle.wildPokemon.speciesId);
        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        double mult = plugin.getConfig().getDouble("battle.exp-multiplier", 1.0)
                * plugin.marriage().expMultiplier(player);
        long gained = Math.round(faintedSpecies.expYield * battle.wildPokemon.level / 7.0 * mult);
        int levels = battle.playerPokemon.addExp(mySpecies, gained);
        player.sendMessage(Component.text(opponentPrefix(battle) + faintedSpecies.name
                + " fainted! +" + gained + " EXP", NamedTextColor.GREEN));
        if (levels > 0) {
            player.sendMessage(Component.text(battle.playerPokemon.displayName(mySpecies)
                    + " grew to Lv." + battle.playerPokemon.level + "!", NamedTextColor.GOLD));
            plugin.evolutions().tryLevelEvolve(player, battle.playerPokemon, mySpecies);
        }
    }

    private boolean handlePlayerFaint(Player player, Battle battle) {
        PokemonSpecies mySpecies = plugin.species().getSpecies(battle.playerPokemon.speciesId);
        player.sendMessage(Component.text(battle.playerPokemon.displayName(mySpecies)
                + " fainted!", NamedTextColor.RED));
        PokemonInstance next = plugin.parties().get(player).firstAlive();
        if (next != null) {
            battle.awaitingSwitch = true;
            syncWildEntity(battle);
            plugin.battleUi().openSwitchMenu(player, battle, true);
            return true;
        }
        battle.finished = true;
        battles.remove(player.getUniqueId());
        player.sendMessage(Component.text("All your pokemon fainted. Heal at a Pokecenter (/poke heal - admin).",
                NamedTextColor.RED));
        plugin.parties().saveParty(player.getUniqueId());
        player.closeInventory();
        return true;
    }

    /**
     * Switch the active pokemon to the given party slot.
     * A voluntary switch gives the wild pokemon a free attack; a switch after
     * a faint does not.
     */
    public void switchPokemon(Player player, int slot) {
        Battle battle = battles.get(player.getUniqueId());
        if (battle == null || battle.finished) return;
        PokemonInstance target = plugin.parties().get(player).get(slot);
        if (target == null || target.currentHp <= 0 || target == battle.playerPokemon) {
            plugin.battleUi().openSwitchMenu(player, battle, battle.awaitingSwitch);
            return;
        }
        boolean afterFaint = battle.awaitingSwitch;
        battle.awaitingSwitch = false;
        battle.resetPlayerStages();
        battle.playerPokemon = target;
        PokemonSpecies species = plugin.species().getSpecies(target.speciesId);
        player.sendMessage(Component.text("Go, " + target.displayName(species) + "!", NamedTextColor.YELLOW));

        if (!afterFaint) {
            MoveData wildMove = pickWildMove(battle.wildPokemon);
            if (wildMove != null && act(player, battle, false, wildMove)) return;
            if (endOfTurn(player, battle)) return;
        }
        syncWildEntity(battle);
        plugin.battleUi().open(player, battle);
    }

    private MoveData pickWildMove(PokemonInstance wild) {
        List<String> moves = wild.moves;
        if (moves != null && !moves.isEmpty()) {
            List<String> usable = moves.stream()
                    .filter(id -> {
                        MoveData m = plugin.species().getMove(id);
                        return m != null && wild.ppFor(m) > 0;
                    }).toList();
            if (!usable.isEmpty()) {
                return plugin.species().getMove(
                        usable.get(ThreadLocalRandom.current().nextInt(usable.size())));
            }
        }
        MoveData struggle = plugin.species().getMove(STRUGGLE_ID);
        return struggle != null ? struggle : plugin.species().getMove("tackle");
    }

    private void syncWildEntity(Battle battle) {
        // keep entity data in sync so pokeball capture chance reflects damage/status
        if (battle.isTrainerBattle()) return; // NPC entity carries no pokemon data
        if (battle.wildEntity != null && battle.wildEntity.isValid()
                && plugin.entities().isWild(battle.wildEntity)) {
            plugin.entities().writeData(battle.wildEntity, battle.wildPokemon);
        }
    }

    private void endWithVictory(Player player, Battle battle) {
        battle.finished = true;
        battles.remove(player.getUniqueId());
        awardExp(player, battle);

        long money;
        if (battle.isTrainerBattle()) {
            money = battle.npcReward;
            plugin.economy().deposit(player.getUniqueId(), money);
            player.sendMessage(Component.text("You defeated " + battle.npcName + "! +"
                    + plugin.economy().format(money), NamedTextColor.GOLD));
        } else {
            if (battle.wildEntity.isValid()) battle.wildEntity.remove();
            money = plugin.economy().wildBattleReward(battle.wildPokemon.level);
            plugin.economy().deposit(player.getUniqueId(), money);
            plugin.economy().addWildWin(player.getUniqueId());
            player.sendMessage(Component.text("+" + plugin.economy().format(money),
                    NamedTextColor.GREEN));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        plugin.parties().saveParty(player.getUniqueId());
        player.closeInventory();
    }

    public void flee(Player player) {
        Battle battle = battles.remove(player.getUniqueId());
        if (battle != null) {
            player.sendMessage(Component.text("Got away safely!", NamedTextColor.GRAY));
            plugin.parties().saveParty(player.getUniqueId());
            player.closeInventory();
        }
    }

    /** Called when the wild pokemon of an active battle gets captured. */
    public void onWildCaptured(Player player) {
        Battle battle = battles.remove(player.getUniqueId());
        if (battle != null) {
            battle.finished = true;
            plugin.parties().saveParty(player.getUniqueId());
            player.closeInventory();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        battles.remove(e.getPlayer().getUniqueId());
    }
}
