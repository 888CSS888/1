package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.SmallFireball;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BattleAI {

    private final DragonTamerPlugin plugin;
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();

    public BattleAI(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick(BattleManager.Battle battle, Map<EnderDragon, Double> dragonHealth) {
        EnderDragon attacker = battle.challengerDragon;
        EnderDragon victim = battle.targetDragon;
        UUID battleId = battle.challenger;
        
        // Атака от первого дракона
        performAttack(attacker, victim, battleId, dragonHealth);
        
        // Атака от второго дракона
        performAttack(victim, attacker, battleId, dragonHealth);
    }

    private void performAttack(EnderDragon attacker, EnderDragon victim, UUID battleId, Map<EnderDragon, Double> dragonHealth) {
        if (attacker == null || victim == null || attacker.isDead() || victim.isDead()) return;
        
        long now = System.currentTimeMillis();
        long last = lastAttackTime.getOrDefault(battleId + attacker.getUniqueId().toString(), 0L);
        
        // Атака раз в 2 секунды
        if (now - last < 2000) return;
        
        lastAttackTime.put(battleId + attacker.getUniqueId().toString(), now);
        
        // Выбираем тип атаки
        int type = ThreadLocalRandom.current().nextInt(3);
        
        switch (type) {
            case 0:
                fireCannon(attacker, victim, dragonHealth);
                break;
            case 1:
                fireballFan(attacker, victim, dragonHealth);
                break;
            case 2:
                meleeAttack(attacker, victim, dragonHealth);
                break;
        }
    }

    private void fireCannon(EnderDragon attacker, EnderDragon victim, Map<EnderDragon, Double> dragonHealth) {
        Location from = attacker.getLocation().clone().add(0, 2, 0);
        Location to = victim.getLocation().clone().add(0, 2, 0);
        World world = from.getWorld();
        
        Vector dir = to.toVector().subtract(from.toVector()).normalize();
        SmallFireball fb = world.spawn(from, SmallFireball.class);
        fb.setShooter(attacker);
        fb.setDirection(dir.multiply(1.8));
        
        world.playSound(from, Sound.ENTITY_GHAST_SHOOT, 1.5f, 1f);
        world.spawnParticle(Particle.FLAME, from, 20, 1, 1, 1, 0.1);
        
        // Задержанный урон
        applyDamage(victim, dragonHealth, 8.0, 10L);
    }

    private void fireballFan(EnderDragon attacker, EnderDragon victim, Map<EnderDragon, Double> dragonHealth) {
        Location from = attacker.getLocation().clone().add(0, 2, 0);
        Location to = victim.getLocation().clone().add(0, 2, 0);
        World world = from.getWorld();
        
        Vector baseDir = to.toVector().subtract(from.toVector()).normalize();
        
        for (int i = -2; i <= 2; i++) {
            double angle = i * 0.3;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vector dir = new Vector(
                baseDir.getX() * cos - baseDir.getZ() * sin,
                baseDir.getY(),
                baseDir.getX() * sin + baseDir.getZ() * cos
            ).normalize();
            
            SmallFireball fb = world.spawn(from, SmallFireball.class);
            fb.setShooter(attacker);
            fb.setDirection(dir.multiply(1.5));
        }
        
        world.playSound(from, Sound.ENTITY_GHAST_SHOOT, 1.5f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION_NORMAL, from, 20, 2, 1, 2, 0.1);
        
        applyDamage(victim, dragonHealth, 5.0, 12L);
    }

    private void meleeAttack(EnderDragon attacker, EnderDragon victim, Map<EnderDragon, Double> dragonHealth) {
        Location victimLoc = victim.getLocation();
        World world = victimLoc.getWorld();
        
        attacker.teleport(victimLoc.clone().add(0, 3, 0));
        
        world.spawnParticle(Particle.EXPLOSION_LARGE, victimLoc, 5, 1, 1, 1, 0);
        world.playSound(victimLoc, Sound.ENTITY_IRONGOLEM_DEATH, 1.5f, 0.8f);
        
        applyDamage(victim, dragonHealth, 12.0, 0L);
    }

    private void applyDamage(EnderDragon victim, Map<EnderDragon, Double> dragonHealth, double damage, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            double current = dragonHealth.getOrDefault(victim, victim.getHealth());
            double newHealth = Math.max(0, current - damage);
            dragonHealth.put(victim, newHealth);
            
            // Визуальный эффект попадания
            Location hitLoc = victim.getLocation();
            hitLoc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, hitLoc, 3, 0.5, 0.5, 0.5, 0);
            hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        }, delayTicks);
    }
}
