package dev.thanhtin.pokecraft;

import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.battle.pvp.PvpBattleManager;
import dev.thanhtin.pokecraft.daycare.DaycareManager;
import dev.thanhtin.pokecraft.economy.EconomyManager;
import dev.thanhtin.pokecraft.bedrock.BedrockSupport;
import dev.thanhtin.pokecraft.capture.CaptureListener;
import dev.thanhtin.pokecraft.capture.PokeballItem;
import dev.thanhtin.pokecraft.command.PokeCommand;
import dev.thanhtin.pokecraft.entity.PokemonEntityManager;
import dev.thanhtin.pokecraft.entity.WildEntityListener;
import dev.thanhtin.pokecraft.item.UsableItems;
import dev.thanhtin.pokecraft.party.PartyManager;
import dev.thanhtin.pokecraft.pokemon.EvolutionService;
import dev.thanhtin.pokecraft.ride.RideManager;
import dev.thanhtin.pokecraft.shop.ShopGui;
import dev.thanhtin.pokecraft.social.MarriageManager;
import dev.thanhtin.pokecraft.spawn.SpawnManager;
import dev.thanhtin.pokecraft.species.SpeciesRegistry;
import dev.thanhtin.pokecraft.storage.StorageManager;
import dev.thanhtin.pokecraft.ui.BattleGui;
import dev.thanhtin.pokecraft.ui.PartyGui;
import dev.thanhtin.pokecraft.ui.DaycareGui;
import dev.thanhtin.pokecraft.ui.LeaderboardGui;
import dev.thanhtin.pokecraft.ui.MainMenuGui;
import dev.thanhtin.pokecraft.ui.PcGui;
import dev.thanhtin.pokecraft.ui.PlayerPickerGui;
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
    private PvpBattleManager pvpManager;
    private EconomyManager economyManager;
    private MarriageManager marriageManager;
    private EvolutionService evolutionService;
    private UsableItems usableItems;
    private ShopGui shopGui;
    private DaycareManager daycareManager;
    private RideManager rideManager;

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
        pvpManager = new PvpBattleManager(this);
        economyManager = new EconomyManager(this);
        marriageManager = new MarriageManager(this);
        evolutionService = new EvolutionService(this);
        usableItems = new UsableItems(this);
        shopGui = new ShopGui(this);
        daycareManager = new DaycareManager(this);
        rideManager = new RideManager(this);
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
        getServer().getPluginManager().registerEvents(pvpManager, this);
        getServer().getPluginManager().registerEvents(economyManager, this);
        getServer().getPluginManager().registerEvents(usableItems, this);
        getServer().getPluginManager().registerEvents(shopGui, this);
        getServer().getPluginManager().registerEvents(rideManager, this);

        PokeCommand command = new PokeCommand(this);
        getCommand("poke").setExecutor(command);
        getCommand("poke").setTabCompleter(command);

        spawnManager.start();
        daycareManager.start();
        rideManager.start();
        getLogger().info("[OK] PokeCraft enabled");
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) spawnManager.stop();
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
    public PvpBattleManager pvp() { return pvpManager; }
    public EconomyManager economy() { return economyManager; }
    public MarriageManager marriage() { return marriageManager; }
    public EvolutionService evolutions() { return evolutionService; }
    public UsableItems items() { return usableItems; }
    public ShopGui shop() { return shopGui; }
    public DaycareManager daycare() { return daycareManager; }
    public RideManager rides() { return rideManager; }
}
