package com.dragontamer.data;

import com.dragontamer.DragonTamerPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataManager {
    private final DragonTamerPlugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public DataManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "dragons.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("Не удалось создать dragons.yml: " + e.getMessage()); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        migrateLegacyData();
    }

    private void migrateLegacyData() {
        if (!dataConfig.contains("dragons")) return;
        Set<String> keys = dataConfig.getConfigurationSection("dragons").getKeys(false);
        for (String key : keys) {
            try {
                UUID ownerUUID = UUID.fromString(key);
                String oldPath  = "dragons." + key;
                if (!dataConfig.contains(oldPath + ".type")) continue;

                String ownerName = dataConfig.getString(oldPath + ".ownerName", "Unknown");
                String typeStr   = dataConfig.getString(oldPath + ".type", "FIRE");
                DragonType type  = DragonType.fromString(typeStr);
                if (type == null) type = DragonType.FIRE;

                UUID dragonId = UUID.randomUUID();
                Dragon d = new Dragon(ownerUUID, ownerName, type);
                d.setDragonId(dragonId);
                d.setLevel(dataConfig.getInt(oldPath + ".level", 1));
                d.setExperience(dataConfig.getLong(oldPath + ".experience", 0));
                d.setCurrentHealth(dataConfig.getDouble(oldPath + ".currentHealth", 100.0));
                d.setFollowing(dataConfig.getBoolean(oldPath + ".following", true));
                d.setLastFarmTime(dataConfig.getLong(oldPath + ".lastFarmTime", 0));
                d.setRecoveryEndTime(dataConfig.getLong(oldPath + ".recoveryEndTime", 0));
                d.setLocation(dataConfig.getString(oldPath + ".location", null));
                d.setOrbitMode(dataConfig.getBoolean(oldPath + ".orbitMode", false));
                d.setAutoFarmMode(dataConfig.getBoolean(oldPath + ".autoFarmMode", false));

                dataConfig.set("dragons." + key, null);
                writeDragonData(ownerUUID, d);
                dataConfig.set("players." + ownerUUID + ".active-dragon", dragonId.toString());

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный UUID при миграции: " + key);
            }
        }
        if (dataConfig.contains("dragons") &&
            dataConfig.getConfigurationSection("dragons").getKeys(false).isEmpty()) {
            dataConfig.set("dragons", null);
        }
        saveFile();
    }

    public void saveDragon(Dragon dragon) {
        writeDragonData(dragon.getOwnerUUID(), dragon);
        saveFile();
    }

    private void writeDragonData(UUID ownerUUID, Dragon dragon) {
        String path = "players." + ownerUUID + ".dragons." + dragon.getDragonId();
        dataConfig.set(path + ".ownerName",     dragon.getOwnerName());
        dataConfig.set(path + ".type",          dragon.getType().name());
        dataConfig.set(path + ".level",         dragon.getLevel());
        dataConfig.set(path + ".experience",    dragon.getExperience());
        dataConfig.set(path + ".currentHealth", dragon.getCurrentHealth());
        dataConfig.set(path + ".following",     dragon.isFollowing());
        dataConfig.set(path + ".lastFarmTime",  dragon.getLastFarmTime());
        dataConfig.set(path + ".recoveryEndTime", dragon.getRecoveryEndTime());
        dataConfig.set(path + ".location",      dragon.getLocation());
        dataConfig.set(path + ".orbitMode",     dragon.isOrbitMode());
        dataConfig.set(path + ".autoFarmMode",  dragon.isAutoFarmMode());
        dataConfig.set(path + ".nickname",      dragon.getNickname());
    }

    public void saveActiveDragon(UUID ownerUUID, UUID dragonId) {
        if (dragonId == null)
            dataConfig.set("players." + ownerUUID + ".active-dragon", null);
        else
            dataConfig.set("players." + ownerUUID + ".active-dragon", dragonId.toString());
        saveFile();
    }

    public void removeDragonFromCollection(UUID ownerUUID, UUID dragonId) {
        dataConfig.set("players." + ownerUUID + ".dragons." + dragonId, null);
        saveFile();
    }

    public List<Dragon> loadPlayerCollection(UUID ownerUUID) {
        List<Dragon> list = new ArrayList<>();
        String base = "players." + ownerUUID + ".dragons";
        if (!dataConfig.contains(base)) return list;
        for (String dragonKey : dataConfig.getConfigurationSection(base).getKeys(false)) {
            try {
                UUID dragonId = UUID.fromString(dragonKey);
                Dragon d = readDragonData(ownerUUID, dragonId);
                if (d != null) list.add(d);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный dragonId: " + dragonKey);
            }
        }
        return list;
    }

    public UUID loadActiveDragonId(UUID ownerUUID) {
        String val = dataConfig.getString("players." + ownerUUID + ".active-dragon", null);
        if (val == null) return null;
        try { return UUID.fromString(val); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Dragon readDragonData(UUID ownerUUID, UUID dragonId) {
        String path = "players." + ownerUUID + ".dragons." + dragonId;
        if (!dataConfig.contains(path)) return null;
        String ownerName = dataConfig.getString(path + ".ownerName", "Unknown");
        DragonType type  = DragonType.fromString(dataConfig.getString(path + ".type", "FIRE"));
        if (type == null) type = DragonType.FIRE;
        Dragon d = new Dragon(ownerUUID, ownerName, type);
        d.setDragonId(dragonId);
        d.setLevel(dataConfig.getInt(path + ".level", 1));
        d.setExperience(dataConfig.getLong(path + ".experience", 0));
        d.setCurrentHealth(dataConfig.getDouble(path + ".currentHealth", 100.0));
        d.setFollowing(dataConfig.getBoolean(path + ".following", true));
        d.setLastFarmTime(dataConfig.getLong(path + ".lastFarmTime", 0));
        d.setRecoveryEndTime(dataConfig.getLong(path + ".recoveryEndTime", 0));
        d.setLocation(dataConfig.getString(path + ".location", null));
        d.setOrbitMode(dataConfig.getBoolean(path + ".orbitMode", false));
        d.setAutoFarmMode(dataConfig.getBoolean(path + ".autoFarmMode", false));
        d.setNickname(dataConfig.getString(path + ".nickname", null));
        return d;
    }

    public Map<UUID, List<Dragon>> loadAllPlayerCollections() {
        Map<UUID, List<Dragon>> result = new HashMap<>();
        if (!dataConfig.contains("players")) return result;
        for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID ownerUUID = UUID.fromString(key);
                result.put(ownerUUID, loadPlayerCollection(ownerUUID));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный UUID игрока: " + key);
            }
        }
        return result;
    }

    public void saveAll(Map<UUID, List<Dragon>> playerCollections) {
        for (Map.Entry<UUID, List<Dragon>> entry : playerCollections.entrySet())
            for (Dragon d : entry.getValue())
                writeDragonData(entry.getKey(), d);
        saveFile();
    }

    private void saveFile() {
        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("Ошибка сохранения dragons.yml: " + e.getMessage()); }
    }

    public void reload() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
}
