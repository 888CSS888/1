package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import com.dragontamer.data.DragonType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class GuardManager {
    private final DragonTamerPlugin plugin;
    private BukkitTask guardTask;
    private final Map<UUID, Long> attackCooldowns = new HashMap<>();

    public GuardManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (guardTask != null) guardTask.cancel();
        double radius = plugin.getConfig().getDouble("guard.radius", 16.0);
        double damage = plugin.getConfig().getDouble("guard.damage-per-hit", 8.0);
        long cooldownMs = plugin.getConfig().getLong("guard.attack-cooldown-ticks", 20L) * 50L;
        boolean particles = plugin.getConfig().getBoolean("guard.particles", true);

        guardTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Dragon dragon : plugin.getDragonManager().getAllDragons()) {
                if (dragon.isRecovering()) continue;
                EnderDragon entity = dragon.getEntity();
                if (entity == null || entity.isDead()) continue;
                Player owner = Bukkit.getPlayer(dragon.getOwnerUUID());
                if (owner == null || !owner.isOnline()) continue;
                if (plugin.getDragonManager().isBlockedWorld(owner.getWorld().getName())) continue;

                LivingEntity target = findNearestHostile(owner, radius);
                if (target == null) continue;

                Long lastAttack = attackCooldowns.get(target.getUniqueId());
                if (lastAttack != null && now - lastAttack < cooldownMs) continue;

                attackCooldowns.put(target.getUniqueId(), now);
                Location attackLoc = target.getLocation().clone().add(0, 3, 0);
                entity.teleport(attackLoc);
                target.damage(damage, entity);

                if (particles) {
                    DragonType type = dragon.getType();
                    target.getWorld().spawnParticle(type.getAmbientParticle(),
                        target.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
                    target.getWorld().spawnParticle(type.getTrailParticle(),
                        target.getLocation(), 15, 1, 1, 1, 0.05);
                }
                plugin.getMessageUtils().send(owner, "guard-attacking", "{mob}", getMobName(target));
            }
            attackCooldowns.entrySet().removeIf(e -> now - e.getValue() > 60_000L);
        }, 40L, 20L);
    }

    public void stop() {
        if (guardTask != null) { guardTask.cancel(); guardTask = null; }
        attackCooldowns.clear();
    }

    private LivingEntity findNearestHostile(Player owner, double radius) {
        LivingEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : owner.getWorld().getNearbyEntities(owner.getLocation(), radius, radius, radius)) {
            if (!(e instanceof Monster)) continue;
            LivingEntity mob = (LivingEntity) e;
            double dist = mob.getLocation().distanceSquared(owner.getLocation());
            if (dist < minDist) { minDist = dist; nearest = mob; }
        }
        return nearest;
    }

    private String getMobName(Entity e) {
        if (e.getCustomName() != null && !e.getCustomName().isEmpty()) return e.getCustomName();
        String raw = e.getType().name().replace("_", " ");
        String[] words = raw.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        return sb.toString().trim();
    }
}
