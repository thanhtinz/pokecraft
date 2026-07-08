package dev.thanhtin.pokecraft;

import dev.thanhtin.pokecraft.activity.DailyManager;
import dev.thanhtin.pokecraft.activity.FishingManager;
import dev.thanhtin.pokecraft.activity.QuestManager;
import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.battle.pvp.PvpBattleManager;
import dev.thanhtin.pokecraft.daycare.DaycareManager;
import dev.thanhtin.pokecraft.dungeon.DungeonManager;
import dev.thanhtin.pokecraft.dungeon.MythicBattleManager;
import dev.thanhtin.pokecraft.economy.EconomyManager;
import dev.thanhtin.pokecraft.minigame.BoardGameManager;
import dev.thanhtin.pokecraft.minigame.BoardPvpManager;
import dev.thanhtin.pokecraft.minigame.CasinoGui;
import dev.thanhtin.pokecraft.minigame.MinigamesGui;
import dev.thanhtin.pokecraft.minigame.TriviaGui;
import dev.thanhtin.pokecraft.minimap.MinimapManager;
import dev.thanhtin.pokecraft.farm.FarmManager;
import dev.thanhtin.pokecraft.bedrock.BedrockSupport;
import dev.thanhtin.pokecraft.capture.CaptureListener;
import dev.thanhtin.pokecraft.capture.PokeballItem;
import dev.thanhtin.pokecraft.command.PokeCommand;
import dev.thanhtin.pokecraft.entity.ModelManager;
import dev.thanhtin.pokecraft.entity.PokemonEntityManager;
import dev.thanhtin.pokecraft.entity.WalkingPokemonManager;
import dev.thanhtin.pokecraft.entity.WildEntityListener;
import dev.thanhtin.pokecraft.item.HeldItems;
import dev.thanhtin.pokecraft.item.UsableItems;
import dev.thanhtin.pokecraft.gym.BadgeService;
import dev.thanhtin.pokecraft.npc.NpcManager;
import dev.thanhtin.pokecraft.party.PartyManager;
import dev.thanhtin.pokecraft.pokemon.EggManager;
import dev.thanhtin.pokecraft.pokemon.EvolutionService;
import dev.thanhtin.pokecraft.ride.RideManager;
import dev.thanhtin.pokecraft.shop.ShopGui;
import dev.thanhtin.pokecraft.rank.RankManager;
import dev.thanhtin.pokecraft.social.GuildManager;
import dev.thanhtin.pokecraft.social.MarriageManager;
import dev.thanhtin.pokecraft.trade.TradeManager;
import dev.thanhtin.pokecraft.spawn.LegendaryManager;
import dev.thanhtin.pokecraft.spawn.SpawnManager;
import dev.thanhtin.pokecraft.species.SpeciesRegistry;
import dev.thanhtin.pokecraft.storage.StorageManager;
import dev.thanhtin.pokecraft.ui.BattleGui;
import dev.thanhtin.pokecraft.ui.PartyGui;
import dev.thanhtin.pokecraft.ui.DaycareGui;
import dev.thanhtin.pokecraft.ui.LeaderboardGui;
import dev.thanhtin.pokecraft.ui.ActivitiesGui;
import dev.thanhtin.pokecraft.ui.AdminGui;
import dev.thanhtin.pokecraft.ui.BadgesGui;
import dev.thanhtin.pokecraft.ui.GymPickerGui;
import dev.thanhtin.pokecraft.ui.GuildGui;
import dev.thanhtin.pokecraft.ui.GuildNameInput;
import dev.thanhtin.pokecraft.ui.RankGui;
import dev.thanhtin.pokecraft.ui.HandheldItems;
import dev.thanhtin.pokecraft.ui.MainMenuGui;
import dev.thanhtin.pokecraft.ui.ModelGui;
import dev.thanhtin.pokecraft.ui.NicknameInput;
import dev.thanhtin.pokecraft.ui.PayAmountGui;
import dev.thanhtin.pokecraft.ui.PcGui;
import dev.thanhtin.pokecraft.ui.PlayerInteractMenu;
import dev.thanhtin.pokecraft.ui.PlayerPickerGui;
import dev.thanhtin.pokecraft.ui.PokedexGui;
import dev.thanhtin.pokecraft.ui.SummaryGui;
import dev.thanhtin.pokecraft.ui.TradeGui;
import dev.thanhtin.pokecraft.ui.RidePickerGui;
import dev.thanhtin.pokecraft.ui.PvpGui;
import org.bukkit.plugin.java.JavaPlugin;

public class PokeCraftPlugin extends JavaPlugin {
    private SpeciesRegistry speciesRegistry;
    private StorageManager storageManager;
    private PartyManager partyManager;
    private PokemonEntityManager entityManager;
    private ModelManager modelManager;
    private WalkingPokemonManager walkingManager;
    private SpawnManager spawnManager;
    private LegendaryManager legendaryManager;
    private BattleManager battleManager;
    private PokeballItem pokeballItem;
    private BedrockSupport bedrockSupport;
    private PartyGui partyGui;
    private BattleGui battleGui;
    private PcGui pcGui;
    private PvpGui pvpGui;
    private MainMenuGui mainMenuGui;
    private HandheldItems handheldItems;
    private ModelGui modelGui;
    private PlayerPickerGui playerPickerGui;
    private PlayerInteractMenu playerInteractMenu;
    private RidePickerGui ridePickerGui;
    private DaycareGui daycareGui;
    private LeaderboardGui leaderboardGui;
    private PokedexGui pokedexGui;
    private SummaryGui summaryGui;
    private dev.thanhtin.pokecraft.ui.MoveTutorGui moveTutorGui;
    private TradeManager tradeManager;
    private TradeGui tradeGui;
    private NicknameInput nicknameInput;
    private PayAmountGui payAmountGui;
    private DailyManager dailyManager;
    private QuestManager questManager;
    private FishingManager fishingManager;
    private ActivitiesGui activitiesGui;
    private GuildManager guildManager;
    private RankManager rankManager;
    private GuildGui guildGui;
    private GuildNameInput guildNameInput;
    private RankGui rankGui;
    private DungeonManager dungeonManager;
    private MythicBattleManager mythicBattleManager;
    private FarmManager farmManager;
    private dev.thanhtin.pokecraft.pokemon.FossilManager fossilManager;
    private dev.thanhtin.pokecraft.pokemon.ChainManager chainManager;
    private AdminGui adminGui;
    private BadgeService badgeService;
    private BadgesGui badgesGui;
    private GymPickerGui gymPickerGui;
    private MinimapManager minimapManager;
    private CasinoGui casinoGui;
    private TriviaGui triviaGui;
    private BoardGameManager boardGameManager;
    private BoardPvpManager boardPvpManager;
    private MinigamesGui minigamesGui;
    private PvpBattleManager pvpManager;
    private EconomyManager economyManager;
    private MarriageManager marriageManager;
    private EvolutionService evolutionService;
    private UsableItems usableItems;
    private HeldItems heldItems;
    private ShopGui shopGui;
    private DaycareManager daycareManager;
    private EggManager eggManager;
    private RideManager rideManager;
    private NpcManager npcManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        speciesRegistry = new SpeciesRegistry(this);
        speciesRegistry.load();

        storageManager = new StorageManager(this);
        try {
            storageManager.init();
        } catch (Exception e) {
            getLogger().severe("[ERR] Storage init failed, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        entityManager = new PokemonEntityManager(this);
        modelManager = new ModelManager(this);
        partyManager = new PartyManager(this);
        battleManager = new BattleManager(this);
        pokeballItem = new PokeballItem(this);
        bedrockSupport = new BedrockSupport(this);
        partyGui = new PartyGui(this);
        battleGui = new BattleGui(this);
        pcGui = new PcGui(this);
        pvpGui = new PvpGui(this);
        mainMenuGui = new MainMenuGui(this);
        handheldItems = new HandheldItems(this);
        modelGui = new ModelGui(this);
        playerPickerGui = new PlayerPickerGui(this);
        playerInteractMenu = new PlayerInteractMenu(this);
        ridePickerGui = new RidePickerGui(this);
        daycareGui = new DaycareGui(this);
        leaderboardGui = new LeaderboardGui(this);
        pokedexGui = new PokedexGui(this);
        summaryGui = new SummaryGui(this);
        moveTutorGui = new dev.thanhtin.pokecraft.ui.MoveTutorGui(this);
        tradeManager = new TradeManager(this);
        tradeGui = new TradeGui(this);
        nicknameInput = new NicknameInput(this);
        payAmountGui = new PayAmountGui(this);
        dailyManager = new DailyManager(this);
        questManager = new QuestManager(this);
        fishingManager = new FishingManager(this);
        activitiesGui = new ActivitiesGui(this);
        guildManager = new GuildManager(this);
        rankManager = new RankManager(this);
        guildGui = new GuildGui(this);
        guildNameInput = new GuildNameInput(this);
        rankGui = new RankGui(this);
        dungeonManager = new DungeonManager(this);
        mythicBattleManager = new MythicBattleManager(this);
        farmManager = new FarmManager(this);
        fossilManager = new dev.thanhtin.pokecraft.pokemon.FossilManager(this);
        chainManager = new dev.thanhtin.pokecraft.pokemon.ChainManager(this);
        adminGui = new AdminGui(this);
        badgeService = new BadgeService(this);
        badgesGui = new BadgesGui(this);
        gymPickerGui = new GymPickerGui(this);
        minimapManager = new MinimapManager(this);
        casinoGui = new CasinoGui(this);
        triviaGui = new TriviaGui(this);
        boardGameManager = new BoardGameManager(this);
        boardPvpManager = new BoardPvpManager(this);
        minigamesGui = new MinigamesGui(this);
        pvpManager = new PvpBattleManager(this);
        economyManager = new EconomyManager(this);
        marriageManager = new MarriageManager(this);
        evolutionService = new EvolutionService(this);
        usableItems = new UsableItems(this);
        heldItems = new HeldItems(this);
        shopGui = new ShopGui(this);
        daycareManager = new DaycareManager(this);
        eggManager = new EggManager(this);
        rideManager = new RideManager(this);
        npcManager = new NpcManager(this);
        spawnManager = new SpawnManager(this);
        legendaryManager = new LegendaryManager(this);
        walkingManager = new WalkingPokemonManager(this);

        getServer().getPluginManager().registerEvents(partyManager, this);
        getServer().getPluginManager().registerEvents(battleManager, this);
        getServer().getPluginManager().registerEvents(new CaptureListener(this), this);
        getServer().getPluginManager().registerEvents(new WildEntityListener(this), this);
        getServer().getPluginManager().registerEvents(partyGui, this);
        getServer().getPluginManager().registerEvents(battleGui, this);
        getServer().getPluginManager().registerEvents(pcGui, this);
        getServer().getPluginManager().registerEvents(pvpGui, this);
        getServer().getPluginManager().registerEvents(mainMenuGui, this);
        getServer().getPluginManager().registerEvents(handheldItems, this);
        getServer().getPluginManager().registerEvents(modelGui, this);
        getServer().getPluginManager().registerEvents(playerPickerGui, this);
        getServer().getPluginManager().registerEvents(playerInteractMenu, this);
        getServer().getPluginManager().registerEvents(ridePickerGui, this);
        getServer().getPluginManager().registerEvents(daycareGui, this);
        getServer().getPluginManager().registerEvents(leaderboardGui, this);
        getServer().getPluginManager().registerEvents(pokedexGui, this);
        getServer().getPluginManager().registerEvents(summaryGui, this);
        getServer().getPluginManager().registerEvents(moveTutorGui, this);
        getServer().getPluginManager().registerEvents(tradeManager, this);
        getServer().getPluginManager().registerEvents(tradeGui, this);
        getServer().getPluginManager().registerEvents(nicknameInput, this);
        getServer().getPluginManager().registerEvents(payAmountGui, this);
        getServer().getPluginManager().registerEvents(fishingManager, this);
        getServer().getPluginManager().registerEvents(activitiesGui, this);
        getServer().getPluginManager().registerEvents(guildGui, this);
        getServer().getPluginManager().registerEvents(guildNameInput, this);
        getServer().getPluginManager().registerEvents(rankGui, this);
        getServer().getPluginManager().registerEvents(dungeonManager, this);
        getServer().getPluginManager().registerEvents(mythicBattleManager, this);
        getServer().getPluginManager().registerEvents(farmManager, this);
        getServer().getPluginManager().registerEvents(fossilManager, this);
        getServer().getPluginManager().registerEvents(adminGui, this);
        getServer().getPluginManager().registerEvents(badgesGui, this);
        getServer().getPluginManager().registerEvents(gymPickerGui, this);
        getServer().getPluginManager().registerEvents(minimapManager, this);
        getServer().getPluginManager().registerEvents(casinoGui, this);
        getServer().getPluginManager().registerEvents(triviaGui, this);
        getServer().getPluginManager().registerEvents(boardGameManager, this);
        getServer().getPluginManager().registerEvents(boardPvpManager, this);
        getServer().getPluginManager().registerEvents(minigamesGui, this);
        getServer().getPluginManager().registerEvents(pvpManager, this);
        getServer().getPluginManager().registerEvents(economyManager, this);
        getServer().getPluginManager().registerEvents(usableItems, this);
        getServer().getPluginManager().registerEvents(heldItems, this);
        getServer().getPluginManager().registerEvents(shopGui, this);
        getServer().getPluginManager().registerEvents(rideManager, this);
        getServer().getPluginManager().registerEvents(npcManager, this);
        getServer().getPluginManager().registerEvents(eggManager, this);
        getServer().getPluginManager().registerEvents(walkingManager, this);

        PokeCommand command = new PokeCommand(this);
        getCommand("poke").setExecutor(command);
        getCommand("poke").setTabCompleter(command);

        pokeballItem.registerRecipes();
        new dev.thanhtin.pokecraft.entity.ModelImporter(this).run();
        spawnManager.start();
        legendaryManager.start();
        farmManager.start();
        minimapManager.start();
        daycareManager.start();
        rideManager.start();
        walkingManager.start();
        hookVault();
        hookPlaceholderApi();
        // clean retired feature items (Team bundle, PokeNav compass) from anyone
        // already online, e.g. after a /reload - the join handlers do the rest
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            handheldItems.cleanupAndGive(online);
            minimapManager.cleanupLegacyItems(online);
        }
        getLogger().info("[OK] PokeCraft enabled");
    }

    /** Register PokeCraft's economy with Vault when Vault is installed. */
    private void hookVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        try {
            getServer().getServicesManager().register(
                    net.milkbowl.vault.economy.Economy.class,
                    new dev.thanhtin.pokecraft.economy.VaultEconomyBridge(this),
                    this, org.bukkit.plugin.ServicePriority.Normal);
            getLogger().info("[OK] Economy registered with Vault");
        } catch (Throwable t) {
            getLogger().warning("[WARN] Vault hook failed: " + t.getMessage());
        }
    }

    /** Register PokeCraft placeholders when PlaceholderAPI is installed. */
    private void hookPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            new dev.thanhtin.pokecraft.integration.PokeCraftPlaceholders(this).register();
            getLogger().info("[OK] PlaceholderAPI expansion registered (%pokecraft_...%)");
        } catch (Throwable t) {
            getLogger().warning("[WARN] PlaceholderAPI hook failed: " + t.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) spawnManager.stop();
        if (legendaryManager != null) legendaryManager.stop();
        if (farmManager != null) farmManager.stop();
        if (minimapManager != null) minimapManager.stop();
        if (daycareManager != null) daycareManager.stop();
        if (rideManager != null) rideManager.stop();
        if (walkingManager != null) walkingManager.stop();
        if (partyManager != null) partyManager.saveAll();
        if (storageManager != null) storageManager.shutdown();
        getLogger().info("[OK] PokeCraft disabled");
    }

    public SpeciesRegistry species() { return speciesRegistry; }
    public StorageManager storage() { return storageManager; }
    public PartyManager parties() { return partyManager; }
    public PokemonEntityManager entities() { return entityManager; }
    public ModelManager models() { return modelManager; }
    public ModelGui modelUi() { return modelGui; }
    public WalkingPokemonManager walkers() { return walkingManager; }
    public BattleManager battles() { return battleManager; }
    public PokeballItem pokeballs() { return pokeballItem; }
    public BedrockSupport bedrock() { return bedrockSupport; }
    public PartyGui partyUi() { return partyGui; }
    public PcGui pcUi() { return pcGui; }
    public BattleGui battleUi() { return battleGui; }
    public PvpGui pvpUi() { return pvpGui; }
    public MainMenuGui mainMenu() { return mainMenuGui; }
    public PlayerPickerGui playerPickerUi() { return playerPickerGui; }
    public RidePickerGui ridePickerUi() { return ridePickerGui; }
    public DaycareGui daycareUi() { return daycareGui; }
    public LeaderboardGui leaderboardUi() { return leaderboardGui; }
    public PokedexGui pokedexUi() { return pokedexGui; }
    public SummaryGui summaryUi() { return summaryGui; }
    public dev.thanhtin.pokecraft.ui.MoveTutorGui moveTutorUi() { return moveTutorGui; }
    public TradeManager trades() { return tradeManager; }
    public TradeGui tradeUi() { return tradeGui; }
    public NicknameInput nicknameInput() { return nicknameInput; }
    public PayAmountGui payUi() { return payAmountGui; }
    public DailyManager daily() { return dailyManager; }
    public QuestManager quests() { return questManager; }
    public FishingManager fishing() { return fishingManager; }
    public ActivitiesGui activitiesUi() { return activitiesGui; }
    public GuildManager guilds() { return guildManager; }
    public RankManager ranks() { return rankManager; }
    public GuildGui guildUi() { return guildGui; }
    public GuildNameInput guildNameInput() { return guildNameInput; }
    public RankGui rankUi() { return rankGui; }
    public DungeonManager dungeons() { return dungeonManager; }
    public MythicBattleManager mythicDungeon() { return mythicBattleManager; }
    public FarmManager farms() { return farmManager; }
    public dev.thanhtin.pokecraft.pokemon.ChainManager chains() { return chainManager; }
    public AdminGui adminUi() { return adminGui; }
    public SpawnManager spawns() { return spawnManager; }
    public LegendaryManager legendaries() { return legendaryManager; }
    public MinimapManager minimap() { return minimapManager; }
    public CasinoGui casinoUi() { return casinoGui; }
    public TriviaGui triviaUi() { return triviaGui; }
    public BoardGameManager boardGames() { return boardGameManager; }
    public BoardPvpManager boardPvp() { return boardPvpManager; }
    public MinigamesGui minigamesUi() { return minigamesGui; }
    public PvpBattleManager pvp() { return pvpManager; }
    public EconomyManager economy() { return economyManager; }
    public MarriageManager marriage() { return marriageManager; }
    public EvolutionService evolutions() { return evolutionService; }
    public UsableItems items() { return usableItems; }
    public HeldItems heldItems() { return heldItems; }
    public ShopGui shop() { return shopGui; }
    public DaycareManager daycare() { return daycareManager; }
    public EggManager eggs() { return eggManager; }
    public RideManager rides() { return rideManager; }
    public NpcManager npcs() { return npcManager; }
    public BadgeService badges() { return badgeService; }
    public BadgesGui badgesUi() { return badgesGui; }
    public GymPickerGui gymPickerUi() { return gymPickerGui; }
}
