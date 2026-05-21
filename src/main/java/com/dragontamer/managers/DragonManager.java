package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import com.dragontamer.data.DragonType;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DragonManager {
    private final DragonTamerPlugin plugin;

    private final Map<UUID, List<Dragon>> playerDragons = new HashMap<>();
    private final Map<UUID, UUID> activeDragonIds = new HashMap<>();

    private BukkitRunnable followTask;
    private BukkitTask particleTask;

    public DragonManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  Load / Save
    // =========================================================================

    public void loadAll() {
        playerDragons.clear();
        activeDragonIds.clear();
        Map<UUID, List<Dragon>> all = plugin.getDataManager().loadAllPlayerCollections();
        playerDragons.putAll(all);
        for (UUID ownerUUID : playerDragons.keySet()) {
            UUID activeId = plugin.getDataManager().loadActiveDragonId(ownerUUID);
            if (activeId != null) activeDragonIds.put(ownerUUID, activeId);
        }
        int total = playerDragons.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("Загружено " + total + " дракон(ов) для " + playerDragons.size() + " игроков.");
    }

    public void saveAll() {
        plugin.getDataManager().saveAll(playerDragons);
    }

    // =========================================================================
    //  Getters
    // =========================================================================

    public Dragon getDragon(UUID playerUUID) {
        UUID activeId = activeDragonIds.get(playerUUID);
        if (activeId == null) return null;
        List<Dragon> list = playerDragons.get(playerUUID);
        if (list == null) return null;
        for (Dragon d : list)
            if (d.getDragonId().equals(activeId)) return d;
        return null;
    }

    public Collection<Dragon> getAllDragons() {
        List<Dragon> active = new ArrayList<>();
        for (UUID uuid : activeDragonIds.keySet()) {
            Dragon d = getDragon(uuid);
            if (d != null) active.add(d);
        }
        return active;
    }

    public List<Dragon> getCollection(UUID playerUUID) {
        return playerDragons.getOrDefault(playerUUID, Collections.emptyList());
    }

    public boolean hasDragon(UUID playerUUID) {
        return getDragon(playerUUID) != null;
    }

    public int getCollectionSize(UUID playerUUID) {
        List<Dragon> list = playerDragons.get(playerUUID);
        return list == null ? 0 : list.size();
    }

    public Dragon getDragonByName(String playerName) {
        for (List<Dragon> list : playerDragons.values())
            for (Dragon d : list)
                if (d.getOwnerName().equalsIgnoreCase(playerName))
                    return d;
        return null;
    }

    public int getMaxDragons() { return plugin.getConfig().getInt("max-dragons-per-player", 1); }

    // =========================================================================
    //  Create / Remove
    // =========================================================================

    public Dragon createDragon(UUID ownerUUID, String ownerName, DragonType type) {
        Dragon newDragon = new Dragon(ownerUUID, ownerName, type);
        double baseHealth = plugin.getConfig().getDouble("dragon-types." + type.name() + ".health", 100.0);
        newDragon.setCurrentHealth(baseHealth);

        Dragon currentActive = getDragon(ownerUUID);
        if (currentActive != null) {
            if (currentActive.getEntity() != null && !currentActive.getEntity().isDead())
                currentActive.setCurrentHealth(currentActive.getEntity().getHealth());
            despawnDragon(currentActive);
            plugin.getDataManager().saveDragon(currentActive);
        }

        playerDragons.computeIfAbsent(ownerUUID, k -> new ArrayList<>()).add(newDragon);
        activeDragonIds.put(ownerUUID, newDragon.getDragonId());
        plugin.getDataManager().saveDragon(newDragon);
        plugin.getDataManager().saveActiveDragon(ownerUUID, newDragon.getDragonId());
        return newDragon;
    }

    public void addToCollection(Dragon dragon) {
        playerDragons.computeIfAbsent(dragon.getOwnerUUID(), k -> new ArrayList<>()).add(dragon);
        plugin.getDataManager().saveDragon(dragon);
    }

    public void removeDragon(UUID ownerUUID) {
        Dragon active = getDragon(ownerUUID);
        if (active != null) {
            despawnDragon(active);
            List<Dragon> list = playerDragons.get(ownerUUID);
            if (list != null) list.remove(active);
            plugin.getDataManager().removeDragonFromCollection(ownerUUID, active.getDragonId());
        }
        activeDragonIds.remove(ownerUUID);
        List<Dragon> remaining = playerDragons.get(ownerUUID);
        if (remaining != null && !remaining.isEmpty()) {
            activeDragonIds.put(ownerUUID, remaining.get(0).getDragonId());
            plugin.getDataManager().saveActiveDragon(ownerUUID, remaining.get(0).getDragonId());
        } else {
            plugin.getDataManager().saveActiveDragon(ownerUUID, null);
        }
    }

    public void removeDragonById(UUID ownerUUID, UUID dragonId) {
        List<Dragon> list = playerDragons.get(ownerUUID);
        if (list == null) return;
        Dragon toRemove = null;
        for (Dragon d : list)
            if (d.getDragonId().equals(dragonId)) { toRemove = d; break; }
        if (toRemove == null) return;
        despawnDragon(toRemove);
        list.remove(toRemove);
        plugin.getDataManager().removeDragonFromCollection(ownerUUID, dragonId);
        UUID currentActive = activeDragonIds.get(ownerUUID);
        if (dragonId.equals(currentActive)) {
            if (!list.isEmpty()) {
                activeDragonIds.put(ownerUUID, list.get(0).getDragonId());
                plugin.getDataManager().saveActiveDragon(ownerUUID, list.get(0).getDragonId());
            } else {
                activeDragonIds.remove(ownerUUID);
                plugin.getDataManager().saveActiveDragon(ownerUUID, null);
            }
        }
    }

    // =========================================================================
    //  Switch active dragon
    // =========================================================================

    public boolean switchActiveDragon(Player player, UUID dragonId) {
        UUID ownerUUID = player.getUniqueId();
        List<Dragon> list = playerDragons.get(ownerUUID);
        if (list == null) return false;

        Dragon toActivate = null;
        for (Dragon d : list)
            if (d.getDragonId().equals(dragonId)) { toActivate = d; break; }
        if (toActivate == null) return false;

        UUID currentActiveId = activeDragonIds.get(ownerUUID);
        if (dragonId.equals(currentActiveId)) return true;

        Dragon currentActive = getDragon(ownerUUID);
        if (currentActive != null) {
            if (currentActive.getEntity() != null && !currentActive.getEntity().isDead())
                currentActive.setCurrentHealth(currentActive.getEntity().getHealth());
            despawnDragon(currentActive);
            plugin.getDataManager().saveDragon(currentActive);
        }

        activeDragonIds.put(ownerUUID, dragonId);
        plugin.getDataManager().saveActiveDragon(ownerUUID, dragonId);
        plugin.getDataManager().saveDragon(toActivate);

        if (!toActivate.isRecovering() && !isBlockedWorld(player.getWorld().getName()))
            spawnDragonForPlayer(player, toActivate);
        return true;
    }

    // =========================================================================
    //  Spawn / Despawn
    // =========================================================================

    public boolean isBlockedWorld(String worldName) {
        return plugin.getConfig().getStringList("blocked-worlds").contains(worldName);
    }

    /**
     * FIX: dragon spawns 12 blocks above the player to avoid hitting the owner.
     */
    public void spawnDragonForPlayer(Player player, Dragon dragon) {
        if (dragon.isRecovering()) return;
        if (isBlockedWorld(player.getWorld().getName())) return;
        despawnDragon(dragon);
        // +12 Y so dragon flies above owner and does not collide with them
        Location loc = player.getLocation().clone().add(5, 12, 0);
        EnderDragon entity = (EnderDragon) player.getWorld().spawnEntity(loc, EntityType.ENDER_DRAGON);
        entity.setMaxHealth(getMaxHealth(dragon));
        entity.setHealth(Math.min(dragon.getCurrentHealth(), getMaxHealth(dragon)));
        entity.setCustomName(ChatColor.translateAlternateColorCodes('&', getDragonDisplayName(dragon)));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        dragon.setEntity(entity);
    }

    public void despawnDragon(Dragon dragon) {
        if (dragon.getEntity() != null && !dragon.getEntity().isDead())
            dragon.getEntity().remove();
        dragon.setEntity(null);
    }

    public void despawnAllDragons() {
        for (List<Dragon> list : playerDragons.values())
            for (Dragon d : list) despawnDragon(d);
    }

    // =========================================================================
    //  Stats
    // =========================================================================

    public double getMaxHealth(Dragon dragon) {
        double base   = plugin.getConfig().getDouble("dragon-types." + dragon.getType().name() + ".health", 100.0);
        double perLvl = plugin.getConfig().getDouble("health-per-level", 5.0);
        return base + perLvl * (dragon.getLevel() - 1);
    }

    public double getDamage(Dragon dragon) {
        double base   = plugin.getConfig().getDouble("dragon-types." + dragon.getType().name() + ".damage", 10.0);
        double perLvl = plugin.getConfig().getDouble("damage-per-level", 1.0);
        return base + perLvl * (dragon.getLevel() - 1);
    }

    public long getExpForNextLevel(Dragon dragon) {
        return plugin.getConfig().getLong("exp-per-level-base", 100) * dragon.getLevel();
    }

    public void addExperience(Dragon dragon, long amount) {
        dragon.setExperience(dragon.getExperience() + amount);
        while (dragon.getExperience() >= getExpForNextLevel(dragon)) {
            dragon.setExperience(dragon.getExperience() - getExpForNextLevel(dragon));
            dragon.setLevel(dragon.getLevel() + 1);
            if (dragon.getEntity() != null && !dragon.getEntity().isDead()) {
                double maxHp = getMaxHealth(dragon);
                dragon.getEntity().setMaxHealth(maxHp);
                dragon.getEntity().setHealth(maxHp);
            }
        }
        plugin.getDataManager().saveDragon(dragon);
    }

    public String getEvolutionSuffix(Dragon dragon) {
        ConfigurationSection evo = plugin.getConfig().getConfigurationSection("evolution-levels");
        if (evo == null) return "";
        String suffix = "";
        for (String key : evo.getKeys(false)) {
            try {
                if (dragon.getLevel() >= Integer.parseInt(key))
                    suffix = evo.getString(key, "");
            } catch (NumberFormatException ignored) {}
        }
        return suffix;
    }

    public String getDragonDisplayName(Dragon dragon) {
        DragonType type = dragon.getType();
        String typeName = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("dragon-types." + type.name() + ".display-name", type.getDisplayName()));
        String evo   = getEvolutionSuffix(dragon);
        String level = " &eLv." + dragon.getLevel();

        String base;
        if (dragon.hasNickname()) {
            base = type.getColorCode() + dragon.getNickname()
                + " &7[" + typeName + "]";
        } else {
            base = type.getColorCode() + typeName;
        }
        if (evo != null && !evo.isEmpty())
            return base + " &7(" + evo + ")" + level;
        return base + level;
    }

    // =========================================================================
    //  Background tasks
    // =========================================================================

    public void startFollowTask() {
        if (followTask != null) followTask.cancel();
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Dragon dragon : getAllDragons()) {
                    if (dragon.isOrbitMode()) continue;
                    if (!dragon.isFollowing()) continue;
                    if (dragon.isRecovering()) continue;
                    EnderDragon entity = dragon.getEntity();
                    if (entity == null || entity.isDead()) continue;
                    Player owner = Bukkit.getPlayer(dragon.getOwnerUUID());
                    if (owner == null || !owner.isOnline()) continue;
                    if (isBlockedWorld(owner.getWorld().getName())) continue;
                    if (!entity.getWorld().equals(owner.getWorld())) {
                        // FIX: use +12 height when teleporting to new world
                        entity.teleport(owner.getLocation().clone().add(5, 12, 0));
                        continue;
                    }
                    if (entity.getLocation().distance(owner.getLocation()) > 20)
                        // FIX: 12 blocks above player so dragon doesn't hit owner
                        entity.teleport(owner.getLocation().clone().add(5, 12, 0));
                }
            }
        };
        followTask.runTaskTimer(plugin, 20L, 40L);
    }

    public void stopFollowTask() {
        if (followTask != null) { followTask.cancel(); followTask = null; }
    }

    public void startParticleEffects() {
        if (particleTask != null) particleTask.cancel();
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Dragon dragon : getAllDragons()) {
                EnderDragon entity = dragon.getEntity();
                if (entity == null || entity.isDead()) continue;
                if (dragon.isRecovering()) continue;
                DragonType type = dragon.getType();
                Location loc = entity.getLocation();
                loc.getWorld().spawnParticle(type.getTrailParticle(),
                    loc.getX(), loc.getY() + 1, loc.getZ(),
                    6, 1.2, 0.8, 1.2, 0.02);
                if (ThreadLocalRandom.current().nextInt(400) == 0)
                    loc.getWorld().playSound(loc, type.getRoarSound(), 1.0f, 0.8f);
            }
        }, 20L, 10L);
    }

    public void stopParticleEffects() {
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
    }

    // =========================================================================
    //  Top
    // =========================================================================

    public List<Dragon> getTopDragons(int limit) {
        List<Dragon> sorted = new ArrayList<>(getAllDragons());
        sorted.sort((a, b) -> {
            int lvl = Integer.compare(b.getLevel(), a.getLevel());
            return lvl != 0 ? lvl : Long.compare(b.getExperience(), a.getExperience());
        });
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}
