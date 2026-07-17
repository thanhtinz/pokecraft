package dev.thanhtin.survivalcore;

import dev.thanhtin.survivalcore.auction.AuctionGui;
import dev.thanhtin.survivalcore.auction.AuctionManager;
import dev.thanhtin.survivalcore.claim.ClaimListener;
import dev.thanhtin.survivalcore.claim.ClaimManager;
import dev.thanhtin.survivalcore.command.Commands;
import dev.thanhtin.survivalcore.crate.CrateGui;
import dev.thanhtin.survivalcore.crate.CrateManager;
import dev.thanhtin.survivalcore.economy.EconomyManager;
import dev.thanhtin.survivalcore.economy.VaultBridge;
import dev.thanhtin.survivalcore.listener.PlayerListener;
import dev.thanhtin.survivalcore.storage.Database;
import dev.thanhtin.survivalcore.teleport.RtpManager;
import dev.thanhtin.survivalcore.teleport.TeleportManager;
import dev.thanhtin.survivalcore.teleport.TpaManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SurvivalCore - an all-in-one survival server core (economy, teleports/homes,
 * and more to come). Bedrock-friendly through Geyser; menus use chest GUIs that
 * Geyser translates, and later native Cumulus forms where it helps.
 */
public class SurvivalCore extends JavaPlugin {

    private Database db;
    private EconomyManager economy;
    private TeleportManager teleports;
    private TpaManager tpa;
    private RtpManager rtp;
    private ClaimManager claims;
    private AuctionManager auctions;
    private AuctionGui auctionGui;
    private CrateManager crates;
    private CrateGui crateGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        db = new Database(this);
        try {
            db.open();
            getLogger().info("[OK] Storage ready (sqlite)");
        } catch (Exception e) {
            getLogger().severe("Storage failed to open - disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = new EconomyManager(this);
        teleports = new TeleportManager(this);
        tpa = new TpaManager(this);
        rtp = new RtpManager(this);
        claims = new ClaimManager(this);
        auctions = new AuctionManager(this);
        auctionGui = new AuctionGui(this);
        crates = new CrateManager(this);
        crates.load();
        crateGui = new CrateGui(this);

        getServer().getPluginManager().registerEvents(teleports, this);
        getServer().getPluginManager().registerEvents(tpa, this);
        getServer().getPluginManager().registerEvents(new ClaimListener(this), this);
        getServer().getPluginManager().registerEvents(auctionGui, this);
        getServer().getPluginManager().registerEvents(crateGui, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        Commands commands = new Commands(this);
        for (String c : new String[]{"balance", "pay", "baltop", "eco",
                "sethome", "home", "delhome", "homes",
                "setwarp", "delwarp", "warp", "warps", "spawn", "setspawn",
                "tpa", "tpahere", "tpaccept", "tpdeny", "back", "rtp",
                "claim", "unclaim", "trust", "untrust", "claiminfo", "claims",
                "ah", "sell", "crate", "key"}) {
            PluginCommand pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(commands);
                pc.setTabCompleter(commands);
            }
        }

        hookVault();
        getLogger().info("[OK] SurvivalCore enabled");
    }

    @Override
    public void onDisable() {
        if (db != null) db.close();
    }

    private void hookVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        try {
            getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class,
                    new VaultBridge(this), this, ServicePriority.Normal);
            getLogger().info("[OK] Economy registered with Vault");
        } catch (Throwable t) {
            getLogger().warning("Vault hook failed: " + t.getMessage());
        }
    }

    public Database db() { return db; }
    public EconomyManager economy() { return economy; }
    public TeleportManager teleports() { return teleports; }
    public TpaManager tpa() { return tpa; }
    public RtpManager rtp() { return rtp; }
    public ClaimManager claims() { return claims; }
    public AuctionManager auctions() { return auctions; }
    public AuctionGui auctionGui() { return auctionGui; }
    public CrateManager crates() { return crates; }
    public CrateGui crateGui() { return crateGui; }
}
