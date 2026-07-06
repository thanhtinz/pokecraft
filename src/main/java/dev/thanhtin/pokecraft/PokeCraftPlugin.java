package dev.thanhtin.pokecraft;

import dev.thanhtin.pokecraft.battle.BattleManager;
import dev.thanhtin.pokecraft.bedrock.BedrockSupport;
import dev.thanhtin.pokecraft.capture.CaptureListener;
import dev.thanhtin.pokecraft.capture.PokeballItem;
import dev.thanhtin.pokecraft.command.PokeCommand;
import dev.thanhtin.pokecraft.entity.PokemonEntityManager;
import dev.thanhtin.pokecraft.party.PartyManager;
import dev.thanhtin.pokecraft.spawn.SpawnManager;
import dev.thanhtin.pokecraft.species.SpeciesRegistry;
import dev.thanhtin.pokecraft.storage.StorageManager;
import dev.thanhtin.pokecraft.ui.BattleGui;
import dev.thanhtin.pokecraft.ui.PartyGui;
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
        spawnManager = new SpawnManager(this);

        getServer().getPluginManager().registerEvents(partyManager, this);
        getServer().getPluginManager().registerEvents(new CaptureListener(this), this);
        getServer().getPluginManager().registerEvents(partyGui, this);
        getServer().getPluginManager().registerEvents(battleGui, this);

        PokeCommand command = new PokeCommand(this);
        getCommand("poke").setExecutor(command);
        getCommand("poke").setTabCompleter(command);

        spawnManager.start();
        getLogger().info("[OK] PokeCraft enabled");
    }

    @Override
    public void onDisable() {
        if (spawnManager != null) spawnManager.stop();
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
    public BattleGui battleUi() { return battleGui; }
}
