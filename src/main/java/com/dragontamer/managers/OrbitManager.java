package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import com.dragontamer.data.DragonType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OrbitManager {
    private final DragonTamerPlugin plugin;
    private BukkitTask orbitTask;
    private final Map<UUID, Double> angles = new HashMap<>();

    public OrbitManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (orbitTask != null) orbitTask.cancel();

        double radius      = plugin.getConfig().getDouble("orbit.radius", 8.0);
        double heightAbove = plugin.getConfig().getDouble("orbit.height-above-player", 6.0);
        double speed       = plugin.getConfig().getDouble("orbit.speed", 0.05);
        boolean particles  = plugin.getConfig().getBoolean("orbit.particles", true);

        orbitTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Dragon dragon : plugin.getDragonManager().getAllDragons()) {
                    if (!dragon.isOrbitMode()) continue;
                    if (dragon.isRecovering()) continue;
                    EnderDragon entity = dragon.getEntity();
                    if (entity == null || entity.isDead()) continue;
                    Player owner = Bukkit.getPlayer(dragon.getOwnerUUID());
                    if (owner == null || !owner.isOnline()) continue;
                    if (plugin.getDragonManager().isBlockedWorld(owner.getWorld().getName())) continue;
                    if (plugin.getBattleManager().isInBattle(owner.getUniqueId())) continue;

                    double angle = angles.getOrDefault(dragon.getOwnerUUID(), 0.0);
                    angle = (angle + speed) % (2 * Math.PI);
                    angles.put(dragon.getOwnerUUID(), angle);

                    Location center = owner.getLocation();
                    double tx = center.getX() + radius * Math.cos(angle);
                    double ty = center.getY() + heightAbove;
                    double tz = center.getZ() + radius * Math.sin(angle);

                    double tangentX = -Math.sin(angle);
                    double tangentZ =  Math.cos(angle);
                    float yaw = (float) (Math.toDegrees(Math.atan2(-tangentX, tangentZ)) + 180f);

                    Location target = new Location(owner.getWorld(), tx, ty, tz, yaw, 0f);

                    if (!entity.getWorld().equals(owner.getWorld())) continue;

                    try {
                        entity.teleport(target);
                        entity.setVelocity(new Vector(0, 0, 0));
                        entity.setFallDistance(0);
                    } catch (Throwable ignored) {}

                    if (particles) {
                        DragonType type = dragon.getType();
                        owner.getWorld().spawnParticle(type.getTrailParticle(),
                            entity.getLocation(), 4, 0.5, 0.5, 0.5, 0.03);
                    }
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void stop() {
        if (orbitTask != null) { orbitTask.cancel(); orbitTask = null; }
        angles.clear();
    }

    public void resetAngle(UUID playerUUID) {
        angles.remove(playerUUID);
    }

    public void clearAngle(UUID playerUUID) {
        angles.remove(playerUUID);
    }
}
