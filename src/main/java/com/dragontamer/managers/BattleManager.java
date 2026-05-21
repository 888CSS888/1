package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import com.dragontamer.VoidChunkGenerator;
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

    // Константы арены
    private static final int ARENA_SIZE = 51;      // Нечётное число для симметрии
    private static final int ARENA_HALF = 25;
    private static final int PLATFORM_Y = 100;     // Высота платформы
    private static final int WALL_HEIGHT = 20;
    private static final double ORBIT_RADIUS = 22.0;
    private static final double ORBIT_HEIGHT = 18.0;

    public BattleManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
        this.ai = new BattleAI(plugin);
    }

    // =========================================================================
    //  Публичные методы
    // =========================================================================

    public void sendBattleRequest(UUID challengerUUID, UUID targetUUID) {
        pendingRequests.put(challengerUUID, targetUUID);
        new BukkitRunnable() {
            @Override public void run() {
                pendingRequests.remove(challengerUUID, targetUUID);
            }
        }.runTaskLater(plugin, 30 * 20L);
    }

    public UUID getChallengerFor(UUID targetUUID) {
        for (Map.Entry<UUID, UUID> e : pendingRequests.entrySet())
            if (e.getValue().equals(targetUUID)) return e.getKey();
        return null;
    }

    public void rejectRequest(UUID targetUUID) {
        UUID challenger = getChallengerFor(targetUUID);
        if (challenger != null) pendingRequests.remove(challenger);
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
            battle.watcherReturns.remove(watcherUUID);
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

    // =========================================================================
    //  Запуск битвы
    // =========================================================================

    public void startBattle(UUID challengerUUID, UUID targetUUID) {
        pendingRequests.remove(challengerUUID);

        Player challenger = Bukkit.getPlayer(challengerUUID);
        Player target = Bukkit.getPlayer(targetUUID);
        Dragon cDragon = plugin.getDragonManager().getDragon(challengerUUID);
        Dragon tDragon = plugin.getDragonManager().getDragon(targetUUID);

        if (challenger == null || target == null || cDragon == null || tDragon == null) return;

        // Проверяем максимальное количество битв
        int maxBattles = plugin.getConfig().getInt("battle.max-concurrent-battles", 5);
        if (activeBattles.size() >= maxBattles) {
            plugin.getMessageUtils().sendRaw(challenger, "&cМаксимальное число битв достигнуто");
            plugin.getMessageUtils().sendRaw(target, "&cМаксимальное число битв достигнуто");
            return;
        }

        // Получаем или создаём мир арены
        World arenaWorld = Bukkit.getWorld("dragon_arena");
        if (arenaWorld == null) {
            plugin.getLogger().warning("Мир dragon_arena не найден! Создаём...");
            WorldCreator wc = new WorldCreator("dragon_arena");
            wc.generator(new VoidChunkGenerator());
            wc.generateStructures(false);
            arenaWorld = wc.createWorld();
        }

        // Находим свободный слот
        int slot = 0;
        while (usedArenaSlots.contains(slot)) slot++;
        usedArenaSlots.add(slot);

        double spacing = 150.0;
        Location arenaCenter = new Location(arenaWorld, slot * spacing, PLATFORM_Y, 0);

        // Строим арену
        plugin.getMessageUtils().send(challenger, "battle-arena-building");
        plugin.getMessageUtils().send(target, "battle-arena-building");

        Battle battle = new Battle(challengerUUID, targetUUID);
        battle.arenaIndex = slot;
        battle.arenaCenter = arenaCenter.clone();
        buildArena(arenaCenter, battle);

        // Сохраняем позиции для возврата
        battle.challengerReturn = challenger.getLocation().clone();
        battle.targetReturn = target.getLocation().clone();

        // Деспауним обычных драконов
        plugin.getDragonManager().despawnDragon(cDragon);
        plugin.getDragonManager().despawnDragon(tDragon);

        // Телепортируем игроков на безопасные позиции
        Location cStand = arenaCenter.clone().add(0, 1, -(ARENA_HALF - 3));
        Location tStand = arenaCenter.clone().add(0, 1, (ARENA_HALF - 3));
        challenger.teleport(cStand);
        target.teleport(tStand);

        // Спавним боевых драконов
        Location cDragonLoc = arenaCenter.clone().add(-ORBIT_RADIUS, ORBIT_HEIGHT, 0);
        Location tDragonLoc = arenaCenter.clone().add(ORBIT_RADIUS, ORBIT_HEIGHT, 0);

        EnderDragon cEnt = (EnderDragon) arenaWorld.spawnEntity(cDragonLoc, EntityType.ENDER_DRAGON);
        EnderDragon tEnt = (EnderDragon) arenaWorld.spawnEntity(tDragonLoc, EntityType.ENDER_DRAGON);

        setupBattleDragon(cEnt, cDragon);
        setupBattleDragon(tEnt, tDragon);
        cDragon.setEntity(cEnt);
        tDragon.setEntity(tEnt);

        battle.challengerDragon = cEnt;
        battle.targetDragon = tEnt;

        // BossBar
        String cName = plugin.getDragonManager().getDragonDisplayName(cDragon);
        String tName = plugin.getDragonManager().getDragonDisplayName(tDragon);
        battle.challengerOwnBar = createBar("§a⚔ " + stripColor(cName), BarColor.GREEN, BarStyle.SEGMENTED_10);
        battle.challengerEnemyBar = createBar("§c☠ " + stripColor(tName), BarColor.RED, BarStyle.SEGMENTED_10);
        battle.targetOwnBar = createBar("§a⚔ " + stripColor(tName), BarColor.GREEN, BarStyle.SEGMENTED_10);
        battle.targetEnemyBar = createBar("§c☠ " + stripColor(cName), BarColor.RED, BarStyle.SEGMENTED_10);

        battle.challengerOwnBar.addPlayer(challenger);
        battle.challengerEnemyBar.addPlayer(challenger);
        battle.targetOwnBar.addPlayer(target);
        battle.targetEnemyBar.addPlayer(target);

        activeBattles.put(challengerUUID, battle);

        // Запускаем круговой полёт
        startFlightTask(battle, arenaCenter);

        // Запускаем 10-секундное интро
        startIntro(battle, challenger, target, cDragon, tDragon, cEnt, tEnt);
    }

    // =========================================================================
    //  Построение арены
    // =========================================================================

    private void buildArena(Location center, Battle battle) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        ArenaData data = battle.arenaData;

        // === 1. ОСНОВНАЯ ПЛАТФОРМА (пол) ===
        for (int x = -ARENA_HALF; x <= ARENA_HALF; x++) {
            for (int z = -ARENA_HALF; z <= ARENA_HALF; z++) {
                // Круглая арена
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= ARENA_HALF) {
                    placeBlock(world, cx + x, PLATFORM_Y, cz + z, Material.STONE, data);
                }
            }
        }

        // === 2. БОРТИК (по краю платформы) ===
        for (int x = -ARENA_HALF; x <= ARENA_HALF; x++) {
            for (int z = -ARENA_HALF; z <= ARENA_HALF; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > ARENA_HALF - 1 && dist <= ARENA_HALF + 1) {
                    for (int y = 1; y <= 2; y++) {
                        placeBlock(world, cx + x, PLATFORM_Y + y, cz + z, Material.SMOOTH_BRICK, data);
                    }
                }
            }
        }

        // === 3. СТЕНЫ (квадратные, за пределами круглой арены) ===
        int wallYStart = PLATFORM_Y + 1;
        for (int y = 0; y <= WALL_HEIGHT; y++) {
            // Северная стена
            for (int x = -ARENA_HALF - 2; x <= ARENA_HALF + 2; x++) {
                placeBlock(world, cx + x, wallYStart + y, cz - ARENA_HALF - 2, Material.BARRIER, data);
            }
            // Южная стена
            for (int x = -ARENA_HALF - 2; x <= ARENA_HALF + 2; x++) {
                placeBlock(world, cx + x, wallYStart + y, cz + ARENA_HALF + 2, Material.BARRIER, data);
            }
            // Западная стена
            for (int z = -ARENA_HALF - 2; z <= ARENA_HALF + 2; z++) {
                placeBlock(world, cx - ARENA_HALF - 2, wallYStart + y, cz + z, Material.BARRIER, data);
            }
            // Восточная стена
            for (int z = -ARENA_HALF - 2; z <= ARENA_HALF + 2; z++) {
                placeBlock(world, cx + ARENA_HALF + 2, wallYStart + y, cz + z, Material.BARRIER, data);
            }
        }

        // === 4. ТРИБУНЫ ДЛЯ ЗРИТЕЛЕЙ ===
        for (int row = 0; row < 4; row++) {
            int yOffset = row;
            int zNorth = cz - ARENA_HALF + row;
            int zSouth = cz + ARENA_HALF - row;
            for (int x = -ARENA_HALF + 2; x <= ARENA_HALF - 2; x++) {
                placeBlock(world, cx + x, PLATFORM_Y + yOffset, zNorth, Material.QUARTZ_BLOCK, data);
                placeBlock(world, cx + x, PLATFORM_Y + yOffset, zSouth, Material.QUARTZ_BLOCK, data);
            }
        }

        // === 5. УГЛОВЫЕ КОЛОННЫ ===
        for (int cxOff : new int[]{-ARENA_HALF - 2, ARENA_HALF + 2}) {
            for (int czOff : new int[]{-ARENA_HALF - 2, ARENA_HALF + 2}) {
                for (int y = 0; y <= WALL_HEIGHT + 2; y++) {
                    placeBlock(world, cx + cxOff, PLATFORM_Y + y, cz + czOff, Material.OBSIDIAN, data);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void placeBlock(World world, int x, int y, int z, Material mat, ArenaData data) {
        try {
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() == mat && b.getData() == 0) return;
            data.record(world, x, y, z, b.getType(), b.getData());
            b.setType(mat);
        } catch (Exception ignored) {}
    }

    // =========================================================================
    //  Плавный полёт по кругу
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
                if (angle > Math.PI * 2) angle -= Math.PI * 2;
                
                moveDragonInCircle(battle.challengerDragon, center, angle);
                moveDragonInCircle(battle.targetDragon, center, angle + Math.PI);
                
                spawnTrailParticles(battle.challengerDragon);
                spawnTrailParticles(battle.targetDragon);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void moveDragonInCircle(EnderDragon dragon, Location center, double angle) {
        if (dragon == null || dragon.isDead()) return;
        
        double x = center.getX() + ORBIT_RADIUS * Math.cos(angle);
        double z = center.getZ() + ORBIT_RADIUS * Math.sin(angle);
        double y = center.getY() + ORBIT_HEIGHT;
        
        // Направление взгляда по касательной
        float yaw = (float) (Math.toDegrees(Math.atan2(-Math.sin(angle), Math.cos(angle))) + 90);
        Location target = new Location(center.getWorld(), x, y, z, yaw, 5f);
        
        dragon.teleport(target);
        dragon.setVelocity(new Vector(0, 0, 0));
    }

    private void spawnTrailParticles(EnderDragon dragon) {
        if (dragon == null || dragon.isDead()) return;
        Location loc = dragon.getLocation();
        loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc.getX(), loc.getY() + 1, loc.getZ(), 5, 1, 0.5, 1, 0.02);
    }

    // =========================================================================
    //  Интро и обратный отсчёт
    // =========================================================================

    private void startIntro(Battle battle, Player challenger, Player target,
                            Dragon cDragon, Dragon tDragon,
                            EnderDragon cEnt, EnderDragon tEnt) {
        
        plugin.getMessageUtils().sendRaw(challenger, "&6Драконы готовятся к бою! 10 секунд...");
        plugin.getMessageUtils().sendRaw(target, "&6Драконы готовятся к бою! 10 секунд...");
        
        // Рёв драконов
        cEnt.getWorld().playSound(cEnt.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 2f, 0.8f);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                startCountdown(battle, challenger, target, cDragon, tDragon, cEnt, tEnt);
            }
        }.runTaskLater(plugin, 200L); // 10 секунд
    }

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
                    String msg = plugin.getMessageUtils().get("battle-countdown", "{count}", String.valueOf(count));
                    challenger.sendMessage(msg);
                    target.sendMessage(msg);
                    cEnt.getWorld().playSound(cEnt.getLocation(), Sound.BLOCK_NOTE_BASS, 1f, 1f);
                    count--;
                } else {
                    cancel();
                    launchBattle(battle, challenger, target, cDragon, tDragon, cEnt, tEnt);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // =========================================================================
    //  Запуск боевой фазы
    // =========================================================================

    private void launchBattle(Battle battle, Player challenger, Player target,
                              Dragon cDragon, Dragon tDragon,
                              EnderDragon cEnt, EnderDragon tEnt) {
        
        if (!activeBattles.containsKey(battle.challenger)) return;
        
        plugin.getMessageUtils().send(challenger, "battle-start");
        plugin.getMessageUtils().send(target, "battle-start");
        cEnt.getWorld().playSound(cEnt.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 2f, 1f);
        
        // Запускаем ИИ каждые 15 тиков
        battle.aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBattles.containsKey(battle.challenger)) {
                    cancel();
                    return;
                }
                if (cEnt.isDead() || tEnt.isDead()) {
                    boolean challengerWon = tEnt.isDead() && !cEnt.isDead();
                    endBattle(battle, cDragon, tDragon, challengerWon);
                    cancel();
                    return;
                }
                ai.tick(battle);
            }
        }.runTaskTimer(plugin, 20L, 15L);
        
        // Обновление BossBar
        battle.bossBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBattles.containsKey(battle.challenger)) {
                    cancel();
                    return;
                }
                updateBossBar(battle.challengerOwnBar, cEnt);
                updateBossBar(battle.challengerEnemyBar, tEnt);
                updateBossBar(battle.targetOwnBar, tEnt);
                updateBossBar(battle.targetEnemyBar, cEnt);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // =========================================================================
    //  Завершение битвы
    // =========================================================================

    private void endBattle(Battle battle, Dragon cDragon, Dragon tDragon, boolean challengerWon) {
        activeBattles.remove(battle.challenger);
        
        Player challenger = Bukkit.getPlayer(battle.challenger);
        Player target = Bukkit.getPlayer(battle.target);
        
        Dragon winner = challengerWon ? cDragon : tDragon;
        Dragon loser = challengerWon ? tDragon : cDragon;
        Player winnerPl = challengerWon ? challenger : target;
        Player loserPl = challengerWon ? target : challenger;
        
        // Останавливаем задачи
        if (battle.aiTask != null) battle.aiTask.cancel();
        if (battle.bossBarTask != null) battle.bossBarTask.cancel();
        if (battle.flightTask != null) battle.flightTask.cancel();
        
        // Убираем BossBar
        removeBars(battle);
        
        // Удаляем драконов
        if (battle.challengerDragon != null) battle.challengerDragon.remove();
        if (battle.targetDragon != null) battle.targetDragon.remove();
        cDragon.setEntity(null);
        tDragon.setEntity(null);
        
        // Награда победителю
        long winExp = plugin.getConfig().getLong("battle-win-exp", 50);
        plugin.getDragonManager().addExperience(winner, winExp);
        if (winnerPl != null && winnerPl.isOnline()) {
            plugin.getMessageUtils().send(winnerPl, "battle-won", "{exp}", String.valueOf(winExp));
            winnerPl.getWorld().playSound(winnerPl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1f);
            winnerPl.teleport(challengerWon ? battle.challengerReturn : battle.targetReturn);
            
            // Респавн дракона
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (winnerPl.isOnline() && !winner.isRecovering())
                    plugin.getDragonManager().spawnDragonForPlayer(winnerPl, winner);
            }, 10L);
        }
        
        // Наказание проигравшему
        long recoveryMin = plugin.getConfig().getLong("recovery-time", 5);
        long recoveryEnd = System.currentTimeMillis() + recoveryMin * 60_000L;
        loser.setRecoveryEndTime(recoveryEnd);
        plugin.getDataManager().saveDragon(loser);
        
        if (loserPl != null && loserPl.isOnline()) {
            plugin.getMessageUtils().send(loserPl, "battle-lost", "{time}", String.valueOf(recoveryMin));
            loserPl.getWorld().playSound(loserPl.getLocation(), Sound.ENTITY_ENDERDRAGON_DEATH, 1.5f, 0.8f);
            loserPl.teleport(challengerWon ? battle.targetReturn : battle.challengerReturn);
        }
        
        // Возвращаем зрителей
        returnWatchers(battle);
        
        // Очищаем арену
        final int slot = battle.arenaIndex;
        final ArenaData data = battle.arenaData;
        World world = battle.arenaCenter.getWorld();
        new BukkitRunnable() {
            @Override
            public void run() {
                clearArena(data, world);
                usedArenaSlots.remove(slot);
            }
        }.runTaskLater(plugin, 60L);
        
        // Таймер восстановления проигравшего
        new BukkitRunnable() {
            @Override
            public void run() {
                loser.setRecoveryEndTime(0);
                plugin.getDataManager().saveDragon(loser);
                Player loserOnline = Bukkit.getPlayer(loser.getOwnerUUID());
                if (loserOnline != null && loserOnline.isOnline()) {
                    plugin.getDragonManager().spawnDragonForPlayer(loserOnline, loser);
                    plugin.getMessageUtils().sendRaw(loserOnline, "&aВаш дракон восстановился!");
                }
            }
        }.runTaskLater(plugin, recoveryMin * 60 * 20L);
    }

    // =========================================================================
    //  Утилиты
    // =========================================================================

    private void setupBattleDragon(EnderDragon entity, Dragon dragon) {
        double maxHp = plugin.getDragonManager().getMaxHealth(dragon);
        entity.setMaxHealth(maxHp);
        entity.setHealth(Math.min(dragon.getCurrentHealth(), maxHp));
        entity.setCustomName(ChatColor.translateAlternateColorCodes('&',
            plugin.getDragonManager().getDragonDisplayName(dragon)));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
    }

    private BossBar createBar(String title, BarColor color, BarStyle style) {
        BossBar bar = Bukkit.createBossBar(title, color, style);
        bar.setProgress(1.0);
        bar.setVisible(true);
        return bar;
    }

    private void updateBossBar(BossBar bar, EnderDragon dragon) {
        if (bar == null || dragon == null) return;
        bar.setProgress(Math.max(0, Math.min(1, dragon.getHealth() / dragon.getMaxHealth())));
    }

    private void removeBars(Battle battle) {
        if (battle.challengerOwnBar != null) battle.challengerOwnBar.removeAll();
        if (battle.challengerEnemyBar != null) battle.challengerEnemyBar.removeAll();
        if (battle.targetOwnBar != null) battle.targetOwnBar.removeAll();
        if (battle.targetEnemyBar != null) battle.targetEnemyBar.removeAll();
    }

    private void clearArena(ArenaData data, World world) {
        for (ArenaData.BlockRecord rec : data.placed) {
            try {
                Block b = world.getBlockAt(rec.x, rec.y, rec.z);
                b.setType(rec.oldType);
                b.setData(rec.oldData);
            } catch (Exception ignored) {}
        }
    }

    private void returnWatchers(Battle battle) {
        for (UUID watcherId : new HashSet<>(battle.watchers)) {
            Player watcher = Bukkit.getPlayer(watcherId);
            Location ret = battle.watcherReturns.get(watcherId);
            if (watcher != null && watcher.isOnline() && ret != null) {
                watcher.teleport(ret);
            }
            watcherBattleMap.remove(watcherId);
        }
        battle.watchers.clear();
        battle.watcherReturns.clear();
    }

    private static String stripColor(String s) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', s));
    }

    public boolean doDodge(Player player, String direction) {
        // Аналогично твоей реализации
        return true;
    }

    public void watchBattle(Player watcher, UUID challengerUUID) {
        // Аналогично твоей реализации
    }

    // =========================================================================
    //  Внутренние классы
    // =========================================================================

    public static class Battle {
        public final UUID challenger;
        public final UUID target;
        public EnderDragon challengerDragon;
        public EnderDragon targetDragon;
        public Location challengerReturn;
        public Location targetReturn;
        public Location arenaCenter;
        public BossBar challengerOwnBar;
        public BossBar challengerEnemyBar;
        public BossBar targetOwnBar;
        public BossBar targetEnemyBar;
        public BukkitTask aiTask;
        public BukkitTask bossBarTask;
        public BukkitTask flightTask;
        public int arenaIndex;
        public ArenaData arenaData = new ArenaData();
        public Set<UUID> watchers = new HashSet<>();
        public Map<UUID, Location> watcherReturns = new HashMap<>();

        public Battle(UUID challenger, UUID target) {
            this.challenger = challenger;
            this.target = target;
        }
    }

    public static class ArenaData {
        public final List<BlockRecord> placed = new ArrayList<>();
        
        public void record(World w, int x, int y, int z, Material oldType, byte oldData) {
            placed.add(new BlockRecord(x, y, z, oldType, oldData));
        }
        
        public static class BlockRecord {
            public final int x, y, z;
            public final Material oldType;
            public final byte oldData;
            
            public BlockRecord(int x, int y, int z, Material oldType, byte oldData) {
                this.x = x; this.y = y; this.z = z;
                this.oldType = oldType; this.oldData = oldData;
            }
        }
    }
}