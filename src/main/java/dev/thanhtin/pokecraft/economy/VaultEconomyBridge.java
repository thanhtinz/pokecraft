package dev.thanhtin.pokecraft.economy;

import dev.thanhtin.pokecraft.PokeCraftPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/**
 * Exposes PokeCraft's PokeDollar economy to Vault, so other plugins (shops,
 * jobs, etc.) can read and modify player balances. Registered only when Vault
 * is installed. Balances are whole PokeDollars, so amounts are rounded.
 * Bank accounts are not supported.
 */
public class VaultEconomyBridge implements Economy {

    private final PokeCraftPlugin plugin;

    public VaultEconomyBridge(PokeCraftPlugin plugin) {
        this.plugin = plugin;
    }

    private UUID uuid(String name) {
        return Bukkit.getOfflinePlayer(name).getUniqueId();
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "PokeCraft"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }
    @Override public String format(double amount) { return plugin.economy().format(Math.round(amount)); }
    @Override public String currencyNamePlural() { return "PokeDollars"; }
    @Override public String currencyNameSingular() { return "PokeDollar"; }

    // ---------- accounts ----------

    @Override public boolean hasAccount(String playerName) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override public boolean hasAccount(String playerName, String world) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player, String world) { return true; }

    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String world) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return true; }

    // ---------- balances ----------

    @Override public double getBalance(String playerName) { return plugin.economy().balance(uuid(playerName)); }
    @Override public double getBalance(OfflinePlayer player) { return plugin.economy().balance(player.getUniqueId()); }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(String playerName, double amount) { return getBalance(playerName) >= amount; }
    @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
    @Override public boolean has(String playerName, String world, double amount) { return getBalance(playerName) >= amount; }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return getBalance(player) >= amount; }

    // ---------- withdraw / deposit ----------

    private EconomyResponse withdraw(UUID id, double amount) {
        long amt = Math.round(amount);
        if (amt < 0) {
            return new EconomyResponse(0, plugin.economy().balance(id),
                    EconomyResponse.ResponseType.FAILURE, "Cannot withdraw a negative amount");
        }
        boolean ok = plugin.economy().withdraw(id, amt);
        return new EconomyResponse(ok ? amt : 0, plugin.economy().balance(id),
                ok ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE,
                ok ? null : "Insufficient funds");
    }

    private EconomyResponse deposit(UUID id, double amount) {
        long amt = Math.round(amount);
        if (amt < 0) {
            return new EconomyResponse(0, plugin.economy().balance(id),
                    EconomyResponse.ResponseType.FAILURE, "Cannot deposit a negative amount");
        }
        plugin.economy().deposit(id, amt);
        return new EconomyResponse(amt, plugin.economy().balance(id),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return withdraw(uuid(playerName), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) { return withdraw(player.getUniqueId(), amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String world, double amount) { return withdraw(uuid(playerName), amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) { return withdraw(player.getUniqueId(), amount); }

    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return deposit(uuid(playerName), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) { return deposit(player.getUniqueId(), amount); }
    @Override public EconomyResponse depositPlayer(String playerName, String world, double amount) { return deposit(uuid(playerName), amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) { return deposit(player.getUniqueId(), amount); }

    // ---------- banks (unsupported) ----------

    private EconomyResponse noBank() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "PokeCraft does not support bank accounts");
    }

    @Override public EconomyResponse createBank(String name, String player) { return noBank(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return noBank(); }
    @Override public EconomyResponse deleteBank(String name) { return noBank(); }
    @Override public EconomyResponse bankBalance(String name) { return noBank(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return noBank(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return noBank(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return noBank(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return noBank(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return noBank(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return noBank(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return noBank(); }
    @Override public List<String> getBanks() { return List.of(); }
}
