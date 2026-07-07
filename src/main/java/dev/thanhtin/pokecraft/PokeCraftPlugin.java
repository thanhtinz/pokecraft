package dev.thanhtin.pokecraft;

import dev.thanhtin.pokecraft.activity.DailyManager;
import dev.thanhtin.pokecraft.activity.FishingManager;
import dev.thanhtin.pokecraft.activity.QuestManager;
import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.battle.pvp.PvpBattleManager;
import dev.thanhtin.pokecraft.daycare.DaycareManager;
import dev.thanhtin.pokecraft.dungeon.DungeonManager;
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
import dev.thanhtin.pokecraft.entity.PokemonEntityManager;
import dev.thanhtin.pokecraft.entity.WildEntityListener;
import dev.thanhtin.pokecraft.item.HeldItems;
import dev.thanhtin.pokecraft.item.UsableItems;
import dev.thanhtin.pokecraft.npc.NpcManager;
import dev.thanhtin.pokecraft.party.PartyManager;
import dev.thanhtin.pokecraft.pokemon.EvolutionService;
import dev.thanhtin.pokecraft.ride.RideManager;
import dev.thanhtin.pokecraft.shop.ShopGui;
import dev.thanhtin.pokecraft.rank.RankManager;
import dev.thanhtin.pokecraft.social.GuildManager;
import dev.thanhtin.pokecraft.social.MarriageManager;
import dev.thanhtin.pokecraft.trade.TradeManager;
import dev.thanhtin.pokecraft.spawn.SpawnManager;
import dev.thanhtin.pokecraft.species.SpeciesRegistry;
import dev.thanhtin.pokecraft.storage.StorageManager;
import dev.thanhtin.pokecraft.ui.BattleGui;
import dev.thanhtin.pokecraft.ui.PartyGui;
import dev.thanhtin.pokecraft.ui.DaycareGui;
import dev.thanhtin.pokecraft.ui.LeaderboardGui;
import dev.thanhtin.pokecraft.ui.ActivitiesGui;
import dev.thanhtin.pokecraft.ui.AdminGui;
import dev.thanhtin.pokecraft.ui.GuildGui;
import dev.thanhtin.pokecraft.ui.GuildNameInput;
import dev.thanhtin.pokecraft.ui.RankGui;
import dev.thanhtin.pokecraft.ui.MainMenuGui;
import dev.thanhtin.pokecraft.ui.NicknameInput;
import dev.thanhtin.pokecraft.ui.PayAmountGui;
import dev.thanhtin.pokecraft.ui.PcGui;
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
    private SpawnManager spawnManager;
    private BattleManager battleManager;
    private PokeballItem pokeballItem;
    private BedrockSupport bedrockSupport;
    private PartyGui partyGui;
    private BattleGui battleGui;
    private PcGui pcGui;
    private PvpGui pvpGui;
    private MainMenuGui mainMenuGui;
    private PlayerPickerGui playerPickerGui;
    private RidePickerGui ridePickerGui;
    private DaycareGui daycareGui;
    private LeaderboardGui leaderboardGui;
    private PokedexGui pokedexGui;
    private SummaryGui summaryGui;
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
    private FarmManager farmManager;
    private AdminGui adminGui;
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
        partyManager = new PartyManager(this);
        battleManager = new BattleManager(this);
        pokeballItem = new PokeballItem(this);
        bedrockSupport = new BedrockSupport(this);
        partyGui = new PartyGui(this);
        battleGui = new BattleGui(this);
        pcGui = new PcGui(this);
        pvpGui = new PvpGui(this);
        mainMenuGui = new MainMenuGui(this);
        playerPickerGui = new PlayerPickerGui(this);
        ridePickerGui = new RidePickerGui(this);
        daycareGui = new DaycareGui(this);
        leaderboardGui = new LeaderboardGui(this);
        pokedexGui = new PokedexGui(this);
        summaryGui = new SummaryGui(this);
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
        farmManager = new FarmManager(this);
        adminGui = new AdminGui(this);
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
        rideManager = new RideManager(this);
        npcManager = new NpcManager(this);
        spawnManager = new SpawnManager(this);

        getServer().getPluginManager().registerEvents(partyManager, this);
        getServer().getPluginManager().registerEvents(battleManager, this);
        getServer().getPluginManager().registerEvents(new CaptureListener(this), this);
        getServer().getPluginManager().registerEvents(new WildEntityListener(this), this);
        getServer().getPluginManager().registerEvents(partyGui, this);
        getServer().getPluginManager().registerEvents(battleGui, this);
        getServer().getPluginManager().registerEvents(pcGui, this);
        getServer().getPluginManager().registerEvents(pvpGui, this);
        getServer().getPluginManager().registerEvents(mainMenuGui, this);
        getServer().getPluginManager().registerEvents(playerPickerGui, this);
        getServer().getPluginManager().registerEvents(ridePickerGui, this);
        getServer().getPluginManager().registerEvents(daycareGui, this);
        getServer().getPluginManager().registerEvents(leaderboardGui, this);
        getServer().getPluginManager().registerEvents(pokedexGui, this);
        getServer().getPluginManager().registerEvents(summaryGui, this);
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
        getServer().getPluginManager().registerEvents(farmManager, this);
        getServer().getPluginManager().registerEvents(adminGui, this);
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

        PokeCommand command = new PokeCommand(this);
        getCommand("poke").setExecutor(command);
        getCommand("poke").setTabCompleter(command);

        spawnManager.start();
        farmManager.start();
        minimapManager.start();
        daycareManager.start();
        rideManager.start();
        getLogger().info("[OK] PokeCraft enabled");
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) spawnManager.stop();
        if (farmManager != null) farmManager.stop();
        if (minimapManager != null) minimapManager.stop();
        if (daycareManager != null) daycareManager.stop();
        if (rideManager != null) rideManager.stop();
        if (partyManager != null) partyManager.saveAll();
        if (storageManager != null) storageManager.shutdown();
        getLogger().info("[OK] PokeCraft disabled");
    }

    public SpeciesRegistry species() { return speciesRegistry; }
    public StorageManager storage() { return storageManager; }
    public PartyManager parties() { return partyManager; }
    public PokemonEntityManager entities() { return entityManager; }
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
    public FarmManager farms() { return farmManager; }
    public AdminGui adminUi() { return adminGui; }
    public SpawnManager spawns() { return spawnManager; }
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
    public RideManager rides() { return rideManager; }
    public NpcManager npcs() { return npcManager; }
}
