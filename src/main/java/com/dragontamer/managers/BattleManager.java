package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.VoidChunkGenerator;
import com.dragontamer.data.Dragon;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class BattleManager {

    private final DragonTamerPlugin plugin;
    private final BattleAI ai;

    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, Battle> activeBattles = new HashMap<>();
    private final Map<UUID, Long> dodgeCooldowns = new HashMap<>();
    private final Set<Integer> usedArenaSlots = new HashSet<>();
    private final Map<UUID, UUID> watcherBattleMap = new HashMap<>();

    // Храним ЗДОРОВЬЕ отдельно от сущности дракона
    private final Map<EnderDragon, Double> dragonHealth = new HashMap<>();

    private static final int ARENA_HALF = 25;
    private static final int PLATFORM_Y = 100;
    private static final double ORBIT_RADIUS = 22.0;
    private static final double ORBIT_HEIGHT = 18.0;

    public BattleManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
        this.ai = new BattleAI(plugin);
    }

    // =========================================================================
    //  ПУБЛИЧНЫЕ МЕТОДЫ
    // =========================================================================

    public void sendChallenge(Player challenger, Player target) {
        pendingRequests.put(challenger.getUniqueId(), target.getUniqueId());
        new BukkitRunnable() {
            @Override public void run() {
                pendingRequests.remove(challenger.getUniqueId(), target.getUniqueId());
            }
        }.runTaskLater(plugin, 30 * 20L);
    }

    public void acceptChallenge(Player player) {
        UUID challengerUUID = getChallengerFor(player.getUniqueId());
        if (challengerUUID == null) {
            plugin.getMessageUtils().send(player, "battle-no-pending");
            return;
        }
        startBattle(challengerUUID, player.getUniqueId());
    }

    public void rejectChallenge(Player player) {
        UUID challengerUUID = getChallengerFor(player.getUniqueId());
        if (challengerUUID == null) {
            plugin.getMessageUtils().send(player, "battle-no-pending");
            return;
        }
        pendingRequests.remove(challengerUUID);
        Player challenger = Bukkit.getPlayer(challengerUUID);
        if (challenger != null) {
            plugin.getMessageUtils().send(challenger, "battle-rejected", "{target}", player.getName());
        }
        plugin.getMessageUtils().send(player, "battle-rejected-self");
    }

    public boolean isInBattle(UUID uuid) {
        for (Battle b : activeBattles.values())
            if (b.challenger.equals(uuid) || b.target.equals(uuid)) return true;
        return false;
    }

    public boolean isWatcher(UUID playerUUID) {
        return watcherBattleMap.containsKey(playerUUID);
    }

    public void removeWatcher(UUID watcherUUID) {
        UUID challengerUUID = watcherBattleMap.remove(watcherUUID);
        if (challengerUUID == null) return;
        Battle battle = activeBattles.get(challengerUUID);
        if (battle != null) {
            battle.watchers.remove(watcherUUID);
        }
    }

    public UUID getBattleKey(UUID participantUUID) {
        for (Map.Entry<UUID, Battle> e : activeBattles.entrySet()) {
            Battle b = e.getValue();
            if (b.challenger.equals(participantUUID) || b.target.equals(participantUUID))
                return e.getKey();
        }
        return null;
    }

    public void watchBattle(Player watcher, UUID challengerUUID) {
        Battle battle = activeBattles.get(challengerUUID);
        if (battle == null) {
            plugin.getMessageUtils().sendRaw(watcher, "&cБитва уже завершилась");
            return;
        }
        battle.watchers.add(watcher.getUniqueId());
        watcher.teleport(battle.arenaCenter.clone().add(0, 2, -23));
        if (battle.bossBar1 != null) battle.bossBar1.addPlayer(watcher);
        if (battle.bossBar2 != null) battle.bossBar2.addPlayer(watcher);
    }

    public boolean doDodge(Player player, String direction) {
        // Упрощённая версия
        return true;
    }

    private UUID getChallengerFor(UUID targetUUID) {
        for (Map.Entry<UUID, UUID> e : pendingRequests.entrySet())
            if (e.getValue().equals(targetUUID)) return e.getKey();
        return null;
    }

    // =========================================================================
    //  ЗАПУСК БИТВЫ
    // =========================================================================

    private void startBattle(UUID challengerUUID, UUID targetUUID) {
        Player challenger = Bukkit.getPlayer(challengerUUID);
        Player target = Bukkit.getPlayer(targetUUID);
        Dragon cDragon = plugin.getDragonManager().getDragon(challengerUUID);
        Dragon tDragon = plugin.getDragonManager().getDragon(targetUUID);

        if (challenger == null || target == null || cDragon == null || tDragon == null) return;

        // Мир арены
        World arenaWorld = Bukkit.getWorld("dragon_arena");
        if (arenaWorld == null) {
            WorldCreator wc = new WorldCreator("dragon_arena");
            wc.generator(new VoidChunkGenerator());
            wc.generateStructures(false);
            arenaWorld = wc.createWorld();
        }

        // Слот арены
        int slot = 0;
        while (usedArenaSlots.contains(slot)) slot++;
        usedArenaSlots.add(slot);

        Location arenaCenter = new Location(arenaWorld, slot * 200.0, PLATFORM_Y, 0);
        buildArena(arenaCenter);

        // Сохраняем позиции
        Location cReturn = challenger.getLocation().clone();
        Location tReturn = target.getLocation().clone();

        // Деспауним обычных драконов
        plugin.getDragonManager().despawnDragon(cDragon);
        plugin.getDragonManager().despawnDragon(tDragon);

        // Телепортируем игроков
        challenger.teleport(arenaCenter.clone().add(0, 2, -23));
        target.teleport(arenaCenter.clone().add(0, 2, 23));

        // Спавним драконов (неуязвимых)
        Location cSpawn = arenaCenter.clone().add(-ORBIT_RADIUS, ORBIT_HEIGHT, 0);
        Location tSpawn = arenaCenter.clone().add(ORBIT_RADIUS, ORBIT_HEIGHT, 0);

        double cMaxHp = plugin.getDragonManager().getMaxHealth(cDragon);
        double tMaxHp = plugin.getDragonManager().getMaxHealth(tDragon);

        EnderDragon cEnt = spawnBattleDragon(arenaWorld, cSpawn, cDragon, cMaxHp);
        EnderDragon tEnt = spawnBattleDragon(arenaWorld, tSpawn, tDragon, tMaxHp);

        // Сохраняем здоровье отдельно
        dragonHealth.put(cEnt, cMaxHp);
        dragonHealth.put(tEnt, tMaxHp);

        // BossBar
        BossBar bar1 = Bukkit.createBossBar("§c⚔ " + cDragon.getOwnerName() + "'s Dragon", BarColor.RED, BarStyle.SEGMENTED_10);
        BossBar bar2 = Bukkit.createBossBar("§a⚔ " + tDragon.getOwnerName() + "'s Dragon", BarColor.GREEN, BarStyle.SEGMENTED_10);
        bar1.addPlayer(challenger);
        bar1.addPlayer(target);
        bar2.addPlayer(challenger);
        bar2.addPlayer(target);
        bar1.setProgress(1.0);
        bar2.setProgress(1.0);

        Battle battle = new Battle(challengerUUID, targetUUID);
        battle.arenaCenter = arenaCenter;
        battle.challengerDragon = cEnt;
        battle.targetDragon = tEnt;
        battle.challengerReturn = cReturn;
        battle.targetReturn = tReturn;
        battle.bossBar1 = bar1;
        battle.bossBar2 = bar2;
        battle.arenaIndex = slot;

        activeBattles.put(challengerUUID, battle);

        // Запускаем полёт
        startFlightTask(battle, arenaCenter);

        // Запускаем защиту драконов от смерти (КРИТИЧЕСКИ ВАЖНО!)
        startDragonProtection(battle);

        // Интро и обратный отсчёт
        startCountdown(battle, challenger, target, cDragon, tDragon, cEnt, tEnt);
    }

    // =========================================================================
    //  СПАВН ДРАКОНА С НЕУЯЗВИМОСТЬЮ
    // =========================================================================

    private EnderDragon spawnBattleDragon(World world, Location loc, Dragon dragon, double maxHp) {
        EnderDragon entity = (EnderDragon) world.spawnEntity(loc, EntityType.ENDER_DRAGON);
        
        // Отключаем ванильную логику дракона
        entity.setCustomName(ChatColor.translateAlternateColorCodes('&', 
            plugin.getDragonManager().getDragonDisplayName(dragon)));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setMaxHealth(maxHp);
        entity.setHealth(maxHp);
        
        // КЛЮЧЕВОЕ: делаем дракона неуязвимым к ванильному урону
        entity.setInvulnerable(true);
        
        return entity;
    }

    // =========================================================================
    //  ЗАЩИТА ОТ СМЕРТИ (критический фикс для 1.12.2)
    // =========================================================================

    private void startDragonProtection(Battle battle) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBattles.containsKey(battle.challenger)) {
                    cancel();
                    return;
                }
                
                // Защищаем драконов от ванильной смерти
                if (battle.challengerDragon != null && battle.challengerDragon.isDead()) {
                    // Если дракон "умер" от ванилы — возрождаем
                    respawnDragonIfNeeded(battle, battle.challengerDragon, true);
                }
                if (battle.targetDragon != null && battle.targetDragon.isDead()) {
                    respawnDragonIfNeeded(battle, battle.targetDragon, false);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void respawnDragonIfNeeded(Battle battle, EnderDragon deadDragon, boolean isChallenger) {
        // Удаляем мёртвую сущность
        deadDragon.remove();
        
        // Создаём новую на том же месте
        Location spawnLoc = battle.arenaCenter.clone().add(
            isChallenger ? -ORBIT_RADIUS : ORBIT_RADIUS, 
            ORBIT_HEIGHT, 0);
        
        Dragon dragonData = isChallenger ? 
            plugin.getDragonManager().getDragon(battle.challenger) : 
            plugin.getDragonManager().getDragon(battle.target);
        
        double maxHp = plugin.getDragonManager().getMaxHealth(dragonData);
        double currentHp = dragonHealth.getOrDefault(deadDragon, maxHp);
        
        EnderDragon newDragon = spawnBattleDragon(spawnLoc.getWorld(), spawnLoc, dragonData, maxHp);
        newDragon.setHealth(Math.min(currentHp, maxHp));
        dragonHealth.put(newDragon, currentHp);
        
        if (isChallenger) {
            battle.challengerDragon = newDragon;
        } else {
            battle.targetDragon = newDragon;
        }
    }

    // =========================================================================
    //  ПОСТРОЕНИЕ АРЕНЫ
    // =========================================================================

    private void buildArena(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        
        // Платформа
        for (int x = -ARENA_HALF; x <= ARENA_HALF; x++) {
            for (int z = -ARENA_HALF; z <= ARENA_HALF; z++) {
                double dist = Math.sqrt(x*x + z*z);
                if (dist <= ARENA_HALF) {
                    world.getBlockAt(cx + x, PLATFORM_Y, cz + z).setType(Material.STONE);
                }
            }
        }
        
        // Барьерные стены
        for (int y = 0; y <= 25; y++) {
            for (int x = -ARENA_HALF-2; x <= ARENA_HALF+2; x++) {
                world.getBlockAt(cx + x, PLATFORM_Y + y, cz - ARENA_HALF-2).setType(Material.BARRIER);
                world.getBlockAt(cx + x, PLATFORM_Y + y, cz + ARENA_HALF+2).setType(Material.BARRIER);
            }
            for (int z = -ARENA_HALF-2; z <= ARENA_HALF+2; z++) {
                world.getBlockAt(cx - ARENA_HALF-2, PLATFORM_Y + y, cz + z).setType(Material.BARRIER);
                world.getBlockAt(cx + ARENA_HALF+2, PLATFORM_Y + y, cz + z).setType(Material.BARRIER);
            }
        }
    }

    // =========================================================================
    //  ПОЛЁТ ПО КРУГУ
    // =========================================================================

    private void startFlightTask(Battle battle, Location center) {
        battle.flightTask = new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                if (!activeBattles.containsKey(battle.challenger)) {
                    cancel();
                    return;
                }
                angle += 0.07;
                moveDragon(battle.challengerDragon, center, angle);
                moveDragon(battle.targetDragon, center, angle + Math.PI);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void moveDragon(EnderDragon dragon, Location center, double angle) {
        if (dragon == null || dragon.isDead()) return;
        double x = center.getX() + ORBIT_RADIUS * Math.cos(angle);
        double z = center.getZ() + ORBIT_RADIUS * Math.sin(angle);
        float yaw = (float) (Math.toDegrees(Math.atan2(-Math.sin(angle), Math.cos(angle))) + 90);
        dragon.teleport(new Location(center.getWorld(), x, center.getY() + ORBIT_HEIGHT, z, yaw, 5f));
        dragon.setVelocity(new Vector(0, 0, 0));
    }

    // =========================================================================
    //  ОБРАТНЫЙ ОТСЧЁТ И БИТВА
    // =========================================================================

    private void startCountdown(Battle battle, Player challenger, Player target,
                                 Dragon cDragon, Dragon tDragon,
                                 EnderDragon cEnt, EnderDragon tEnt) {
        new BukkitRunnable() {
            int count = 3;
            @Override
            public void run() {
                if (!activeBattles.containsKey(battle.challenger)) {
                    cancel();
                    return;
                }
                if (count > 0) {
                    challenger.sendMessage("§c" + count + "...");
                    target.sendMessage("§c" + count + "...");
                    count--;
                } else {
                    cancel();
                    plugin.getMessageUtils().send(challenger, "battle-start");
                    plugin.getMessageUtils().send(target, "battle-start");
                    startBattleAI(battle, cDragon, tDragon, cEnt, tEnt);
                    startBossBarUpdater(battle);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startBattleAI(Battle battle, Dragon cDragon, Dragon tDragon,
                                EnderDragon cEnt, EnderDragon tEnt) {
        battle.aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBattles.containsKey(battle.challenger)) {
                    cancel();
                    return;
                }
                // Атакуем, используя хранимое здоровье
                ai.tick(battle, dragonHealth);
            }
        }.runTaskTimer(plugin, 20L, 30L);
    }

    private void startBossBarUpdater(Battle battle) {
        battle.bossBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBattles.containsKey(battle.challenger)) {
                    cancel();
                    return;
                }
                double hp1 = dragonHealth.getOrDefault(battle.challengerDragon, 1.0);
                double hp2 = dragonHealth.getOrDefault(battle.targetDragon, 1.0);
                double max1 = battle.challengerDragon.getMaxHealth();
                double max2 = battle.targetDragon.getMaxHealth();
                battle.bossBar1.setProgress(Math.max(0, Math.min(1, hp1 / max1)));
                battle.bossBar2.setProgress(Math.max(0, Math.min(1, hp2 / max2)));
                
                // Проверка окончания битвы
                if (hp1 <= 0 || hp2 <= 0) {
                    boolean challengerWon = hp2 <= 0;
                    endBattle(battle, cDragon, tDragon, challengerWon);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // =========================================================================
    //  ЗАВЕРШЕНИЕ БИТВЫ
    // =========================================================================

    private void endBattle(Battle battle, Dragon cDragon, Dragon tDragon, boolean challengerWon) {
        activeBattles.remove(battle.challenger);
        
        if (battle.aiTask != null) battle.aiTask.cancel();
        if (battle.bossBarTask != null) battle.bossBarTask.cancel();
        if (battle.flightTask != null) battle.flightTask.cancel();
        
        if (battle.challengerDragon != null) battle.challengerDragon.remove();
        if (battle.targetDragon != null) battle.targetDragon.remove();
        
        dragonHealth.remove(battle.challengerDragon);
        dragonHealth.remove(battle.targetDragon);
        
        if (battle.bossBar1 != null) battle.bossBar1.removeAll();
        if (battle.bossBar2 != null) battle.bossBar2.removeAll();
        
        Player challenger = Bukkit.getPlayer(battle.challenger);
        Player target = Bukkit.getPlayer(battle.target);
        
        Dragon winner = challengerWon ? cDragon : tDragon;
        Dragon loser = challengerWon ? tDragon : cDragon;
        
        if (challenger != null) {
            challenger.teleport(battle.challengerReturn);
            plugin.getDragonManager().spawnDragonForPlayer(challenger, winner);
            plugin.getMessageUtils().send(challenger, "battle-won", "{exp}", "50");
        }
        if (target != null) {
            target.teleport(battle.targetReturn);
            if (!challengerWon) {
                plugin.getDragonManager().spawnDragonForPlayer(target, winner);
            }
            plugin.getMessageUtils().send(target, challengerWon ? "battle-lost" : "battle-won", "{time}", "5");
        }
        
        usedArenaSlots.remove(battle.arenaIndex);
    }

    // =========================================================================
    //  ВНУТРЕННИЙ КЛАСС BATTLE
    // =========================================================================

    public static class Battle {
        public final UUID challenger;
        public final UUID target;
        public EnderDragon challengerDragon;
        public EnderDragon targetDragon;
        public Location challengerReturn;
        public Location targetReturn;
        public Location arenaCenter;
        public BossBar bossBar1;
        public BossBar bossBar2;
        public BukkitTask aiTask;
        public BukkitTask bossBarTask;
        public BukkitTask flightTask;
        public Set<UUID> watchers = new HashSet<>();
        public int arenaIndex;

        public Battle(UUID challenger, UUID target) {
            this.challenger = challenger;
            this.target = target;
        }
    }
}
