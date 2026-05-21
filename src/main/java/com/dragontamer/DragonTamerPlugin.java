package com.dragontamer;

import com.dragontamer.commands.DragonCommand;
import com.dragontamer.data.DataManager;
import com.dragontamer.listeners.BattleListener;
import com.dragontamer.listeners.CollectionListener;
import com.dragontamer.listeners.EntityListener;
import com.dragontamer.listeners.ExplosionListener;
import com.dragontamer.listeners.PlayerListener;
import com.dragontamer.managers.*;
import com.dragontamer.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;

public class DragonTamerPlugin extends JavaPlugin {
    private static DragonTamerPlugin instance;
    private DataManager dataManager;
    private DragonManager dragonManager;
    private FarmManager farmManager;
    private BattleManager battleManager;
    private OrbitManager orbitManager;
    private GuardManager guardManager;
    private ChestManager chestManager;
    private DragonCollectionMenu collectionMenu;
    private CollectionListener collectionListener;
    private MessageUtils messageUtils;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        dataManager        = new DataManager(this);
        messageUtils       = new MessageUtils(this);
        dragonManager      = new DragonManager(this);
        farmManager        = new FarmManager(this);
        battleManager      = new BattleManager(this);
        orbitManager       = new OrbitManager(this);
        guardManager       = new GuardManager(this);
        chestManager       = new ChestManager(this);
        collectionListener = new CollectionListener(this);
        collectionMenu     = new DragonCollectionMenu(this);

        dragonManager.loadAll();

        // FIX: create the arena world at plugin startup, not lazily on first battle
        initArenaWorld();

        getCommand("dr").setExecutor(new DragonCommand(this));

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this),    this);
        Bukkit.getPluginManager().registerEvents(new EntityListener(this),    this);
        Bukkit.getPluginManager().registerEvents(new ExplosionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BattleListener(this),    this);
        Bukkit.getPluginManager().registerEvents(chestManager,                this);
        Bukkit.getPluginManager().registerEvents(collectionListener,          this);

        dragonManager.startFollowTask();
        dragonManager.startParticleEffects();

        if (getConfig().getBoolean("features.orbit",     true)) orbitManager.start();
        if (getConfig().getBoolean("features.guard",     true)) guardManager.start();
        if (getConfig().getBoolean("features.auto-farm", true)) farmManager.startAutoFarmTask();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DragonTamerPlaceholders(this).register();
            getLogger().info("PlaceholderAPI hook зарегистрирован.");
        }

        getLogger().info("=========================================");
        getLogger().info("DragonTamer v" + getDescription().getVersion() + " ВКЛЮЧЁН");
        getLogger().info("Автор: Alexey Sipachyov");
        getLogger().info("Функции: Орбита, Охрана, АвтоФерма, Сундук, Частицы");
        getLogger().info("Коллекция: Несколько драконов на игрока, GUI-меню");
        getLogger().info("Битва: Арена dragon_arena, Плавный полёт, Круг 10с, BossBar");
        getLogger().info("Мульти-арена: одновременные битвы, смена мира авто-респавн");
        getLogger().info("=========================================");
    }

    /**
     * FIX: Creates (or loads) the arena world so it is ready immediately when
     * a battle starts, avoiding the "world not loaded" error.
     */
    private void initArenaWorld() {
        String worldName = getConfig().getString("arena.world", "dragon_arena");
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            getLogger().info("Мир арены '" + worldName + "' уже загружен.");
            return;
        }
        try {
            WorldCreator creator = new WorldCreator(worldName);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            World arenaWorld = creator.createWorld();
            if (arenaWorld != null) {
                arenaWorld.setSpawnFlags(false, false);
                arenaWorld.setGameRuleValue("doMobSpawning",   "false");
                arenaWorld.setGameRuleValue("doDaylightCycle", "false");
                arenaWorld.setGameRuleValue("doWeatherCycle",  "false");
                arenaWorld.setGameRuleValue("keepInventory",   "true");
                getLogger().info("Мир арены '" + worldName + "' создан.");
            } else {
                getLogger().warning("Не удалось создать мир арены '" + worldName + "'!");
            }
        } catch (Exception e) {
            getLogger().severe("Ошибка создания мира арены: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        orbitManager.stop();
        guardManager.stop();
        farmManager.stopAutoFarmTask();
        dragonManager.stopFollowTask();
        dragonManager.stopParticleEffects();
        dragonManager.saveAll();
        dragonManager.despawnAllDragons();
        chestManager.saveAll();
        getLogger().info("DragonTamer v" + getDescription().getVersion() + " выключен.");
    }

    public void reloadPlugin() {
        reloadConfig();
        messageUtils.reload();
        getLogger().info("DragonTamer конфиг перезагружен.");
    }

    public static DragonTamerPlugin getInstance() { return instance; }
    public DataManager getDataManager()           { return dataManager; }
    public DragonManager getDragonManager()       { return dragonManager; }
    public FarmManager getFarmManager()           { return farmManager; }
    public BattleManager getBattleManager()       { return battleManager; }
    public OrbitManager getOrbitManager()         { return orbitManager; }
    public GuardManager getGuardManager()         { return guardManager; }
    public ChestManager getChestManager()         { return chestManager; }
    public DragonCollectionMenu getCollectionMenu()        { return collectionMenu; }
    public CollectionListener getCollectionListener()      { return collectionListener; }
    public MessageUtils getMessageUtils()                  { return messageUtils; }
}
