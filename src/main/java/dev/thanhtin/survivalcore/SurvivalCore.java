package dev.thanhtin.survivalcore;

import dev.thanhtin.survivalcore.admin.AdminPanel;
import dev.thanhtin.survivalcore.auction.AuctionGui;
import dev.thanhtin.survivalcore.auction.AuctionManager;
import dev.thanhtin.survivalcore.claim.ClaimListener;
import dev.thanhtin.survivalcore.claim.ClaimManager;
import dev.thanhtin.survivalcore.command.Commands;
import dev.thanhtin.survivalcore.crate.CrateListener;
import dev.thanhtin.survivalcore.crate.CrateManager;
import dev.thanhtin.survivalcore.crate.KeyItem;
import dev.thanhtin.survivalcore.economy.EconomyManager;
import dev.thanhtin.survivalcore.economy.VaultBridge;
import dev.thanhtin.survivalcore.job.JobListener;
import dev.thanhtin.survivalcore.job.JobManager;
import dev.thanhtin.survivalcore.kit.KitManager;
import dev.thanhtin.survivalcore.bounty.BountyListener;
import dev.thanhtin.survivalcore.bounty.BountyManager;
import dev.thanhtin.survivalcore.chat.ChatListener;
import dev.thanhtin.survivalcore.npc.NpcListener;
import dev.thanhtin.survivalcore.npc.NpcManager;
import dev.thanhtin.survivalcore.listener.PlayerListener;
import dev.thanhtin.survivalcore.scoreboard.ScoreboardService;
import dev.thanhtin.survivalcore.rank.RankManager;
import dev.thanhtin.survivalcore.reward.RewardManager;
import dev.thanhtin.survivalcore.reward.VoteManager;
import dev.thanhtin.survivalcore.storage.Database;
import dev.thanhtin.survivalcore.teleport.RtpManager;
import dev.thanhtin.survivalcore.teleport.TeleportManager;
import dev.thanhtin.survivalcore.teleport.TpaManager;
import dev.thanhtin.survivalcore.vault.VaultBlockListener;
import dev.thanhtin.survivalcore.vault.VaultGui;
import dev.thanhtin.survivalcore.vault.VaultManager;
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
    private KeyItem keyItem;
    private VaultManager vaults;
    private VaultGui vaultGui;
    private KitManager kits;
    private RankManager ranks;
    private JobManager jobs;
    private NpcManager npcs;
    private RewardManager rewards;
    private VoteManager votes;
    private BountyManager bounties;
    private ScoreboardService scoreboards;
    private AdminPanel adminPanel;

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
        keyItem = new KeyItem(this);
        vaults = new VaultManager(this);
        vaultGui = new VaultGui(this);
        kits = new KitManager(this);
        kits.load();
        ranks = new RankManager(this);
        ranks.load();
        jobs = new JobManager(this);
        jobs.load();
        npcs = new NpcManager(this);
        rewards = new RewardManager(this);
        votes = new VoteManager(this);
        bounties = new BountyManager(this);
        scoreboards = new ScoreboardService(this);
        adminPanel = new AdminPanel(this);

        getServer().getPluginManager().registerEvents(teleports, this);
        getServer().getPluginManager().registerEvents(tpa, this);
        getServer().getPluginManager().registerEvents(new ClaimListener(this), this);
        getServer().getPluginManager().registerEvents(auctionGui, this);
        getServer().getPluginManager().registerEvents(vaultGui, this);
        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(new VaultBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new JobListener(this), this);
        getServer().getPluginManager().registerEvents(new BountyListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new NpcListener(this), this);
        getServer().getPluginManager().registerEvents(adminPanel, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        scoreboards.start();
        npcs.spawnAll();
        hookPlaceholders();

        Commands commands = new Commands(this);
        for (String c : new String[]{"balance", "pay", "baltop", "eco",
                "sethome", "home", "delhome", "homes",
                "setwarp", "delwarp", "warp", "warps", "spawn", "setspawn",
                "tpa", "tpahere", "tpaccept", "tpdeny", "back", "rtp",
                "claim", "unclaim", "trust", "untrust", "claiminfo", "claims",
                "ah", "sell", "crate", "key",
                "pv", "vault", "pvault", "kit", "kits", "rankup", "rank",
                "jobs", "job", "daily", "vote", "svote", "bounty", "npc", "sc"}) {
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
        if (scoreboards != null) scoreboards.stop();
        if (npcs != null) npcs.despawnAll();
        if (db != null) db.close();
    }

    private void hookPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            new dev.thanhtin.survivalcore.placeholder.SurvivalPlaceholders(this).register();
            getLogger().info("[OK] PlaceholderAPI expansion registered");
        } catch (Throwable t) {
            getLogger().warning("PlaceholderAPI hook failed: " + t.getMessage());
        }
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
    public KeyItem keyItem() { return keyItem; }
    public VaultManager vaults() { return vaults; }
    public VaultGui vaultGui() { return vaultGui; }
    public KitManager kits() { return kits; }
    public RankManager ranks() { return ranks; }
    public JobManager jobs() { return jobs; }
    public NpcManager npcs() { return npcs; }
    public RewardManager rewards() { return rewards; }
    public VoteManager votes() { return votes; }
    public BountyManager bounties() { return bounties; }
    public ScoreboardService scoreboards() { return scoreboards; }
    public AdminPanel adminPanel() { return adminPanel; }
}
