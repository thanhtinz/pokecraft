package dev.thanhtin.pokecraft.battle.pvp;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.battle.DamageCalculator;
import dev.thanhtin.pokecraft.battle.MoveData;
import dev.thanhtin.pokecraft.pokemon.PokemonInstance;
import dev.thanhtin.pokecraft.pokemon.StatusCondition;
import dev.thanhtin.pokecraft.species.PokemonSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PvP duels: /poke duel <player> to challenge, both players pick an action
 * each turn (move or switch); the turn resolves when both have chosen.
 */
public class PvpBattleManager implements Listener {
    private static final long CHALLENGE_TIMEOUT_MILLIS = 60_000;

    private final PokeCraftPlugin plugin;
    private final Map<UUID, PvpBattle> battles = new ConcurrentHashMap<>();
    /** challenger -> (target, time) */
    private final Map<UUID, Challenge> challenges = new ConcurrentHashMap<>();

    private record Challenge(UUID target, long at) {}

    public PvpBattleManager(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    public PvpBattle get(Player player) { return battles.get(player.getUniqueId()); }

    // ---------- challenge flow ----------

    public void challenge(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(Component.text("You can't duel yourself.", NamedTextColor.RED));
            return;
        }
        if (!canFight(from, from) || !canFight(to, from)) return;
        double maxDistance = plugin.getConfig().getDouble("pvp.max-distance", 50);
        if (!from.getWorld().equals(to.getWorld())
                || from.getLocation().distance(to.getLocation()) > maxDistance) {
            from.sendMessage(Component.text("You must be within " + (int) maxDistance
                    + " blocks of each other.", NamedTextColor.RED));
            return;
        }
        challenges.put(from.getUniqueId(), new Challenge(to.getUniqueId(), System.currentTimeMillis()));
        from.sendMessage(Component.text("Duel challenge sent to " + to.getName() + ".", NamedTextColor.GREEN));
        to.sendMessage(Component.text(from.getName() + " challenges you to a pokemon duel! "
                + "/poke duel accept (60s) or /poke duel deny.", NamedTextColor.GOLD));
    }

    /** @param notify who receives the reason when the check fails */
    private boolean canFight(Player player, Player notify) {
        if (battles.containsKey(player.getUniqueId()) || plugin.battles().get(player) != null) {
            notify.sendMessage(Component.text(player.getName() + " is already in a battle.",
                    NamedTextColor.RED));
            return false;
        }
        if (plugin.parties().get(player).firstAlive() == null) {
            notify.sendMessage(Component.text(player.getName() + " has no usable pokemon.",
                    NamedTextColor.RED));
            return false;
        }
        return true;
    }

    /** Name of the player whose (unexpired) challenge targets this player, or null. */
    public String pendingChallengerName(Player target) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Challenge> e : challenges.entrySet()) {
            if (!e.getValue().target().equals(target.getUniqueId())) continue;
            if (now - e.getValue().at() > CHALLENGE_TIMEOUT_MILLIS) continue;
            Player challenger = plugin.getServer().getPlayer(e.getKey());
            if (challenger != null) return challenger.getName();
        }
        return null;
    }

    public void accept(Player player) {
        for (Map.Entry<UUID, Challenge> e : challenges.entrySet()) {
            Challenge c = e.getValue();
            if (!c.target().equals(player.getUniqueId())) continue;
            challenges.remove(e.getKey());
            if (System.currentTimeMillis() - c.at() > CHALLENGE_TIMEOUT_MILLIS) continue;
            Player challenger = plugin.getServer().getPlayer(e.getKey());
            if (challenger == null) continue;
            if (!canFight(challenger, player) || !canFight(player, player)) return;
            start(challenger, player);
            return;
        }
        player.sendMessage(Component.text("No pending duel challenge.", NamedTextColor.RED));
    }

    public void deny(Player player) {
        for (Map.Entry<UUID, Challenge> e : challenges.entrySet()) {
            if (!e.getValue().target().equals(player.getUniqueId())) continue;
            challenges.remove(e.getKey());
            Player challenger = plugin.getServer().getPlayer(e.getKey());
            if (challenger != null) {
                challenger.sendMessage(Component.text(player.getName() + " declined the duel.",
                        NamedTextColor.RED));
            }
            player.sendMessage(Component.text("Duel declined.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("No pending duel challenge.", NamedTextColor.RED));
    }

    private void start(Player a, Player b) {
        plugin.rides().dismount(a);
        plugin.rides().dismount(b);
        PvpBattle battle = new PvpBattle(
                a.getUniqueId(), plugin.parties().get(a).firstAlive(),
                b.getUniqueId(), plugin.parties().get(b).firstAlive());
        battles.put(a.getUniqueId(), battle);
        battles.put(b.getUniqueId(), battle);
        broadcast(battle, Component.text("Duel: " + a.getName() + " vs " + b.getName() + "!",
                NamedTextColor.GOLD));
        openMenus(battle);
    }

    // ---------- choices ----------

    public void chooseMove(Player player, String moveId) {
        PvpBattle battle = battles.get(player.getUniqueId());
        if (battle == null || battle.finished || battle.awaitingSwitch(player.getUniqueId())) return;
        if (battle.choiceOf(player.getUniqueId()) != null) return;
        PokemonInstance active = battle.activeOf(player.getUniqueId());
        if (BattleManager.STRUGGLE_ID.equals(moveId)) {
            if (plugin.battles().hasUsableMove(active)) return;
        } else {
            MoveData move = plugin.species().getMove(moveId);
            if (move == null || active.moves == null || !active.moves.contains(moveId)) return;
            if (active.ppFor(move) <= 0) return;
        }
        battle.setChoice(player.getUniqueId(), "move:" + moveId);
        afterChoice(player, battle);
    }

    public void chooseSwitch(Player player, int slot) {
        PvpBattle battle = battles.get(player.getUniqueId());
        if (battle == null || battle.finished) return;
        UUID id = player.getUniqueId();
        PokemonInstance target = plugin.parties().get(player).get(slot);
        if (target == null || target.currentHp <= 0 || target == battle.activeOf(id)) {
            plugin.pvpUi().openSwitchMenu(player, battle, battle.awaitingSwitch(id));
            return;
        }
        if (battle.awaitingSwitch(id)) {
            // forced replacement after a faint - resolves immediately
            battle.setAwaitingSwitch(id, false);
            battle.setActive(id, target);
            broadcastSendOut(battle, player, target);
            if (!battle.anyAwaitingSwitch()) openMenus(battle);
            return;
        }
        if (battle.choiceOf(id) != null) return;
        battle.setChoice(id, "switch:" + slot);
        afterChoice(player, battle);
    }

    private void afterChoice(Player player, PvpBattle battle) {
        if (battle.bothChosen()) {
            resolveTurn(battle);
        } else {
            player.closeInventory();
            player.sendMessage(Component.text("Waiting for your opponent...", NamedTextColor.GRAY));
        }
    }

    // ---------- turn resolution ----------

    private void resolveTurn(PvpBattle battle) {
        // switches resolve before moves
        for (UUID side : new UUID[]{battle.p1, battle.p2}) {
            String choice = battle.choiceOf(side);
            if (choice == null || !choice.startsWith("switch:")) continue;
            Player player = plugin.getServer().getPlayer(side);
            if (player == null) continue;
            int slot = Integer.parseInt(choice.substring("switch:".length()));
            PokemonInstance target = plugin.parties().get(player).get(slot);
            if (target != null && target.currentHp > 0) {
                battle.setActive(side, target);
                broadcastSendOut(battle, player, target);
            }
        }

        List<UUID> movers = new ArrayList<>();
        for (UUID side : new UUID[]{battle.p1, battle.p2}) {
            String choice = battle.choiceOf(side);
            if (choice != null && choice.startsWith("move:")) movers.add(side);
        }
        movers.sort((a, b) -> {
            MoveData ma = chosenMove(battle, a);
            MoveData mb = chosenMove(battle, b);
            int pa = ma == null ? 0 : ma.priority;
            int pb = mb == null ? 0 : mb.priority;
            if (pa != pb) return Integer.compare(pb, pa);
            return Double.compare(effectiveSpeed(battle, b), effectiveSpeed(battle, a));
        });

        for (UUID actor : movers) {
            if (battle.finished) return;
            PokemonInstance attacker = battle.activeOf(actor);
            if (attacker.currentHp <= 0) continue; // fainted before it could act
            MoveData move = chosenMove(battle, actor);
            if (move == null) continue;
            executeMove(battle, actor, move);
            if (battle.finished) return;
        }

        endOfTurnResidual(battle);
        if (battle.finished) return;

        battle.clearChoices();
        if (battle.anyAwaitingSwitch()) {
            openForcedSwitchMenus(battle);
        } else {
            openMenus(battle);
        }
    }

    private MoveData chosenMove(PvpBattle battle, UUID side) {
        String choice = battle.choiceOf(side);
        if (choice == null || !choice.startsWith("move:")) return null;
        return plugin.species().getMove(choice.substring("move:".length()));
    }

    private double effectiveSpeed(PvpBattle battle, UUID side) {
        PokemonInstance p = battle.activeOf(side);
        PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
        double speed = p.stat(species, 5) * DamageCalculator.stageMultiplier(battle.stagesOf(side)[5]);
        if (p.status == StatusCondition.PARALYSIS) speed *= 0.5;
        // deterministic-ish tiebreak noise
        return speed + ThreadLocalRandom.current().nextDouble(0.5);
    }

    private void executeMove(PvpBattle battle, UUID actor, MoveData move) {
        UUID other = battle.opponentOf(actor);
        Player actorPlayer = plugin.getServer().getPlayer(actor);
        PokemonInstance attacker = battle.activeOf(actor);
        PokemonInstance defender = battle.activeOf(other);
        PokemonSpecies attackerSpecies = plugin.species().getSpecies(attacker.speciesId);
        PokemonSpecies defenderSpecies = plugin.species().getSpecies(defender.speciesId);
        String who = (actorPlayer != null ? actorPlayer.getName() : "???") + "'s " + attackerSpecies.name;

        if (!canAct(battle, attacker, attackerSpecies, who)) return;

        if (!BattleManager.STRUGGLE_ID.equals(move.id)) attacker.usePp(move);

        DamageCalculator.Result result = DamageCalculator.calculate(
                attacker, attackerSpecies, battle.stagesOf(actor),
                defender, defenderSpecies, battle.stagesOf(other), move);

        if (result.missed()) {
            broadcast(battle, Component.text(who + " used " + move.name + " but missed!",
                    NamedTextColor.GRAY));
            return;
        }

        if (move.category != MoveData.Category.STATUS) {
            defender.currentHp = Math.max(0, defender.currentHp - result.damage());
            StringBuilder msg = new StringBuilder(who + " used " + move.name
                    + " (" + result.damage() + " dmg)");
            if (result.critical()) msg.append(" - Critical hit!");
            if (result.effectiveness() > 1.0) msg.append(" It's super effective!");
            if (result.effectiveness() > 0 && result.effectiveness() < 1.0) msg.append(" Not very effective...");
            if (result.effectiveness() == 0) msg.append(" It had no effect.");
            broadcast(battle, Component.text(msg.toString(), NamedTextColor.AQUA));
        } else {
            broadcast(battle, Component.text(who + " used " + move.name + "!", NamedTextColor.AQUA));
        }

        if (result.effectiveness() > 0) {
            applyEffect(battle, actor, move, attacker, defender);
        }

        if (BattleManager.STRUGGLE_ID.equals(move.id)) {
            int recoil = Math.max(1, attacker.maxHp(attackerSpecies) / 4);
            attacker.currentHp = Math.max(0, attacker.currentHp - recoil);
            broadcast(battle, Component.text(who + " is hit by recoil (" + recoil + " dmg)!",
                    NamedTextColor.GRAY));
        }

        checkFaints(battle);
    }

    private boolean canAct(PvpBattle battle, PokemonInstance attacker, PokemonSpecies species, String who) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (attacker.status == StatusCondition.SLEEP) {
            if (attacker.sleepTurns > 0) {
                attacker.sleepTurns--;
                broadcast(battle, Component.text(who + " is fast asleep.", NamedTextColor.GRAY));
                return false;
            }
            attacker.status = null;
            broadcast(battle, Component.text(who + " woke up!", NamedTextColor.YELLOW));
            return true;
        }
        if (attacker.status == StatusCondition.FREEZE) {
            if (rnd.nextInt(100) < 20) {
                attacker.status = null;
                broadcast(battle, Component.text(who + " thawed out!", NamedTextColor.YELLOW));
                return true;
            }
            broadcast(battle, Component.text(who + " is frozen solid!", NamedTextColor.GRAY));
            return false;
        }
        if (attacker.status == StatusCondition.PARALYSIS && rnd.nextInt(100) < 25) {
            broadcast(battle, Component.text(who + " is paralyzed and can't move!", NamedTextColor.GRAY));
            return false;
        }
        return true;
    }

    private void applyEffect(PvpBattle battle, UUID actor, MoveData move,
                             PokemonInstance attacker, PokemonInstance defender) {
        MoveData.Effect effect = move.effect;
        if (effect == null) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        UUID other = battle.opponentOf(actor);

        if (effect.stat >= 1 && effect.stat <= 5 && effect.stages != 0) {
            boolean onSelf = effect.target == MoveData.Target.SELF;
            UUID targetSide = onSelf ? actor : other;
            int[] stages = battle.stagesOf(targetSide);
            PokemonInstance target = onSelf ? attacker : defender;
            PokemonSpecies targetSpecies = plugin.species().getSpecies(target.speciesId);
            int before = stages[effect.stat];
            stages[effect.stat] = Math.max(-6, Math.min(6, before + effect.stages));
            String statName = switch (effect.stat) {
                case 1 -> "Attack"; case 2 -> "Defense"; case 3 -> "Sp. Atk";
                case 4 -> "Sp. Def"; default -> "Speed";
            };
            Player targetPlayer = plugin.getServer().getPlayer(targetSide);
            String who = (targetPlayer != null ? targetPlayer.getName() : "???") + "'s " + targetSpecies.name;
            if (stages[effect.stat] != before) {
                broadcast(battle, Component.text(who + "'s " + statName
                        + (effect.stages > 0 ? " rose!" : " fell!"), NamedTextColor.YELLOW));
            }
        }

        if (effect.status != null && defender.status == null && defender.currentHp > 0
                && rnd.nextInt(100) < effect.statusChance) {
            defender.status = effect.status;
            if (effect.status == StatusCondition.SLEEP) defender.sleepTurns = 1 + rnd.nextInt(3);
            PokemonSpecies ds = plugin.species().getSpecies(defender.speciesId);
            Player otherPlayer = plugin.getServer().getPlayer(other);
            broadcast(battle, Component.text((otherPlayer != null ? otherPlayer.getName() : "???")
                    + "'s " + ds.name + " was " + effect.status.verb + "!", NamedTextColor.YELLOW));
        }
    }

    private void endOfTurnResidual(PvpBattle battle) {
        for (UUID side : new UUID[]{battle.p1, battle.p2}) {
            PokemonInstance p = battle.activeOf(side);
            if (p.currentHp <= 0 || p.status == null) continue;
            double fraction = p.status.residualDamage();
            if (fraction <= 0) continue;
            PokemonSpecies species = plugin.species().getSpecies(p.speciesId);
            int dmg = Math.max(1, (int) (p.maxHp(species) * fraction));
            p.currentHp = Math.max(0, p.currentHp - dmg);
            Player owner = plugin.getServer().getPlayer(side);
            broadcast(battle, Component.text((owner != null ? owner.getName() : "???") + "'s "
                    + species.name + " is hurt by its " + p.status.tag + " (" + dmg + " dmg)!",
                    NamedTextColor.GRAY));
        }
        checkFaints(battle);
    }

    private void checkFaints(PvpBattle battle) {
        boolean faint1 = battle.active1.currentHp <= 0 && !battle.awaitingSwitch1;
        boolean faint2 = battle.active2.currentHp <= 0 && !battle.awaitingSwitch2;
        Player pl1 = plugin.getServer().getPlayer(battle.p1);
        Player pl2 = plugin.getServer().getPlayer(battle.p2);

        if (faint1) {
            PokemonSpecies s = plugin.species().getSpecies(battle.active1.speciesId);
            broadcast(battle, Component.text((pl1 != null ? pl1.getName() : "???") + "'s "
                    + s.name + " fainted!", NamedTextColor.RED));
        }
        if (faint2) {
            PokemonSpecies s = plugin.species().getSpecies(battle.active2.speciesId);
            broadcast(battle, Component.text((pl2 != null ? pl2.getName() : "???") + "'s "
                    + s.name + " fainted!", NamedTextColor.RED));
        }
        if (!faint1 && !faint2) return;

        boolean out1 = faint1 && (pl1 == null || plugin.parties().get(pl1).firstAlive() == null);
        boolean out2 = faint2 && (pl2 == null || plugin.parties().get(pl2).firstAlive() == null);

        if (out1 && out2) {
            end(battle, null);
        } else if (out1) {
            end(battle, battle.p2);
        } else if (out2) {
            end(battle, battle.p1);
        } else {
            if (faint1) battle.awaitingSwitch1 = true;
            if (faint2) battle.awaitingSwitch2 = true;
        }
    }

    // ---------- ending ----------

    public void forfeit(Player player) {
        PvpBattle battle = battles.get(player.getUniqueId());
        if (battle == null || battle.finished) return;
        broadcast(battle, Component.text(player.getName() + " forfeited the duel.", NamedTextColor.GRAY));
        end(battle, battle.opponentOf(player.getUniqueId()));
    }

    /** @param winner null = draw */
    private void end(PvpBattle battle, UUID winner) {
        battle.finished = true;
        battles.remove(battle.p1);
        battles.remove(battle.p2);
        Player pl1 = plugin.getServer().getPlayer(battle.p1);
        Player pl2 = plugin.getServer().getPlayer(battle.p2);

        if (winner == null) {
            broadcast(battle, Component.text("The duel ended in a draw!", NamedTextColor.GOLD));
        } else {
            Player winPlayer = plugin.getServer().getPlayer(winner);
            long reward = plugin.getConfig().getLong("pvp.win-reward", 250);
            plugin.economy().deposit(winner, reward);
            plugin.economy().addPvpWin(winner);
            broadcast(battle, Component.text((winPlayer != null ? winPlayer.getName() : "???")
                    + " wins the duel! (+" + plugin.economy().format(reward) + ")", NamedTextColor.GOLD));
            if (winPlayer != null) {
                winPlayer.playSound(winPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        if (pl1 != null) {
            plugin.parties().saveParty(battle.p1);
            pl1.closeInventory();
        }
        if (pl2 != null) {
            plugin.parties().saveParty(battle.p2);
            pl2.closeInventory();
        }
    }

    // ---------- UI helpers ----------

    private void openMenus(PvpBattle battle) {
        Player pl1 = plugin.getServer().getPlayer(battle.p1);
        Player pl2 = plugin.getServer().getPlayer(battle.p2);
        if (pl1 != null) plugin.pvpUi().openMoveMenu(pl1, battle);
        if (pl2 != null) plugin.pvpUi().openMoveMenu(pl2, battle);
    }

    private void openForcedSwitchMenus(PvpBattle battle) {
        for (UUID side : new UUID[]{battle.p1, battle.p2}) {
            Player player = plugin.getServer().getPlayer(side);
            if (player == null) continue;
            if (battle.awaitingSwitch(side)) {
                plugin.pvpUi().openSwitchMenu(player, battle, true);
            } else {
                player.sendMessage(Component.text("Waiting for your opponent to send out a pokemon...",
                        NamedTextColor.GRAY));
            }
        }
    }

    private void broadcastSendOut(PvpBattle battle, Player player, PokemonInstance pokemon) {
        PokemonSpecies species = plugin.species().getSpecies(pokemon.speciesId);
        broadcast(battle, Component.text(player.getName() + " sent out "
                + pokemon.displayName(species) + " Lv." + pokemon.level + "!", NamedTextColor.YELLOW));
    }

    private void broadcast(PvpBattle battle, Component message) {
        Player pl1 = plugin.getServer().getPlayer(battle.p1);
        Player pl2 = plugin.getServer().getPlayer(battle.p2);
        if (pl1 != null) pl1.sendMessage(message);
        if (pl2 != null) pl2.sendMessage(message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PvpBattle battle = battles.get(e.getPlayer().getUniqueId());
        if (battle != null && !battle.finished) {
            broadcast(battle, Component.text(e.getPlayer().getName() + " left the duel.",
                    NamedTextColor.GRAY));
            end(battle, battle.opponentOf(e.getPlayer().getUniqueId()));
        }
    }
}
