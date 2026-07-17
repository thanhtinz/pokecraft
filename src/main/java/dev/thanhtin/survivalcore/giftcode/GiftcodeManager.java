package dev.thanhtin.survivalcore.giftcode;

import dev.thanhtin.survivalcore.SurvivalCore;
import dev.thanhtin.survivalcore.storage.Database.Giftcode;
import dev.thanhtin.survivalcore.util.Msg;
import org.bukkit.entity.Player;

import java.security.SecureRandom;

/**
 * Giftcodes: admins generate a code (from the admin panel) with a default
 * reward; players enter it with /redeem to claim once. Codes come from outside
 * the game (Discord, events), so redemption is the one place a code is typed.
 */
public class GiftcodeManager {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    private final SurvivalCore plugin;

    public GiftcodeManager(SurvivalCore plugin) {
        this.plugin = plugin;
    }

    /** Create a code using the configured default reward. Returns the code. */
    public String createDefault() {
        double money = plugin.getConfig().getDouble("giftcode.default-money", 1000.0);
        String crate = plugin.getConfig().getString("giftcode.default-crate", "vote");
        int keys = plugin.getConfig().getInt("giftcode.default-keys", 1);
        int maxUses = plugin.getConfig().getInt("giftcode.default-max-uses", 0);
        String code = generate(8);
        plugin.db().createGiftcode(code, money, crate, keys, maxUses);
        return code;
    }

    public void redeem(Player player, String input) {
        if (input == null || input.isBlank()) { Msg.error(player, "Usage: /redeem <code>"); return; }
        String code = input.trim().toUpperCase();
        Giftcode gc = plugin.db().getGiftcode(code);
        if (gc == null) { Msg.error(player, "Invalid code."); return; }
        if (gc.maxUses() > 0 && gc.uses() >= gc.maxUses()) {
            Msg.error(player, "This code has reached its use limit.");
            return;
        }
        if (plugin.db().hasRedeemed(code, player.getUniqueId())) {
            Msg.error(player, "You've already redeemed this code.");
            return;
        }
        if (!plugin.db().redeemGiftcode(code, player.getUniqueId())) {
            Msg.error(player, "You've already redeemed this code.");
            return;
        }
        StringBuilder msg = new StringBuilder("Redeemed! ");
        if (gc.money() > 0) {
            plugin.economy().deposit(player.getUniqueId(), gc.money());
            msg.append(plugin.economy().format(gc.money())).append(" ");
        }
        if (gc.crate() != null && gc.keys() > 0
                && plugin.crates().giveKeys(player, gc.crate().toLowerCase(), gc.keys())) {
            msg.append("+ ").append(gc.keys()).append(" ").append(gc.crate()).append(" key ");
        }
        Msg.ok(player, msg.toString().trim());
    }

    private String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return sb.toString();
    }
}
