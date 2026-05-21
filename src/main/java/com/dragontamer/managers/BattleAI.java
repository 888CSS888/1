package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
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
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public BattleAI(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick(BattleManager.Battle battle) {
        if (battle.challengerDragon == null || battle.targetDragon == null) return;
        if (battle.challengerDragon.isDead() || battle.targetDragon.isDead()) return;

        // Атакуем каждые 2 секунды (40 тиков)
        tickDragon(battle.challenger, battle.challengerDragon, battle.targetDragon);
        tickDragon(battle.target, battle.targetDragon, battle.challengerDragon);
    }

    private void tickDragon(UUID owner, EnderDragon attacker, EnderDragon victim) {
        if (attacker == null || victim == null || attacker.isDead() || victim.isDead()) return;

        long now = System.currentTimeMillis();
        Map<String, Long> cd = cooldowns.computeIfAbsent(owner, k -> new HashMap<>());

        long cooldownTime = 2000; // 2 секунды между атаками
        Long lastAttack = cd.get("last_attack");
        
        if (lastAttack != null && now - lastAttack < cooldownTime) return;

        // Выбираем случайную атаку
        Random rand = ThreadLocalRandom.current();
        int attackType = rand.nextInt(3);
        
        cd.put("last_attack", now);
        
        switch (attackType) {
            case 0:
                fireCannon(attacker, victim);
                break;
            case 1:
                fireballFan(attacker, victim);
                break;
            case 2:
                meleeAttack(attacker, victim);
                break;
        }
    }

    private void fireCannon(EnderDragon attacker, EnderDragon victim) {
        Location from = attacker.getLocation().clone().add(0, 2, 0);
        Location to = victim.getLocation().clone().add(0, 2, 0);
        World world = from.getWorld();

        Vector dir = to.toVector().subtract(from.toVector()).normalize();
        
        SmallFireball fb = world.spawn(from, SmallFireball.class);
        fb.setShooter(attacker);
        fb.setDirection(dir.multiply(1.8));
        fb.setYield(0);
        
        world.playSound(from, Sound.ENTITY_GHAST_SHOOT, 1.5f, 1f);
        world.spawnParticle(Particle.FLAME, from, 20, 1, 1, 1, 0.1);
        
        // Урон через задержку
        applyDamage(victim, 8.0, 10L);
    }

    private void fireballFan(EnderDragon attacker, EnderDragon victim) {
        Location from = attacker.getLocation().clone().add(0, 2, 0);
        Location to = victim.getLocation().clone().add(0, 2, 0);
        World world = from.getWorld();

        Vector baseDir = to.toVector().subtract(from.toVector()).normalize();
        
        for (int i = -2; i <= 2; i++) {
            double angle = i * 0.3;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double nx = baseDir.getX() * cos - baseDir.getZ() * sin;
            double nz = baseDir.getX() * sin + baseDir.getZ() * cos;
            Vector dir = new Vector(nx, baseDir.getY(), nz).normalize();
            
            SmallFireball fb = world.spawn(from, SmallFireball.class);
            fb.setShooter(attacker);
            fb.setDirection(dir.multiply(1.5));
            fb.setYield(0);
        }
        
        world.playSound(from, Sound.ENTITY_GHAST_SHOOT, 1.5f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION_NORMAL, from, 20, 2, 1, 2, 0.1);
        
        applyDamage(victim, 6.0, 12L);
    }

    private void meleeAttack(EnderDragon attacker, EnderDragon victim) {
        Location victimLoc = victim.getLocation();
        World world = victimLoc.getWorld();
        
        // Телепорт к жертве
        attacker.teleport(victimLoc.clone().add(0, 3, 0));
        
        world.spawnParticle(Particle.EXPLOSION_LARGE, victimLoc, 5, 1, 1, 1, 0);
        world.spawnParticle(Particle.SWEEP_ATTACK, victimLoc, 10, 1, 1, 1, 0);
        world.playSound(victimLoc, Sound.ENTITY_GENERIC_BIG_FALL, 1.5f, 0.8f);
        
        applyDamage(victim, 12.0, 0L);
    }

    private void applyDamage(EnderDragon victim, double damage, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (victim == null || victim.isDead()) return;
            double newHp = Math.max(1, victim.getHealth() - damage);
            victim.setHealth(newHp);
            
            Location hitLoc = victim.getLocation();
            hitLoc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, hitLoc, 3, 0.5, 0.5, 0.5, 0);
            hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        }, delayTicks);
    }
}