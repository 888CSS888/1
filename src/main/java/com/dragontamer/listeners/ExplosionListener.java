package com.dragontamer.listeners;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Предотвращает разрушение блоков драконами.
 *
 * Обрабатывает:
 * 1. Взрывы самого дракона (EntityExplodeEvent)
 * 2. Взрывы файерболов дракона (EntityExplodeEvent, стрелок — EnderDragon)
 * 3. Попадание снаряда дракона в блок (ProjectileHitEvent) — не даёт создать взрыв
 */
public class ExplosionListener implements Listener {

    private final DragonTamerPlugin plugin;

    public ExplosionListener(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        // Взрыв самого дракона
        if (entity instanceof EnderDragon) {
            if (isDragonManaged((EnderDragon) entity)) {
                event.setCancelled(true);
                event.blockList().clear();
                return;
            }
        }

        // Взрыв файербола выпущенного драконом
        if (entity instanceof Fireball) {
            Fireball fireball = (Fireball) entity;
            if (fireball.getShooter() instanceof EnderDragon) {
                EnderDragon shooter = (EnderDragon) fireball.getShooter();
                if (isDragonManaged(shooter)) {
                    event.setCancelled(true);
                    event.blockList().clear();
                }
            }
        }
    }

    /**
     * Дополнительная защита: когда снаряд дракона попадает в блок,
     * гасим его без создания взрыва.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball)) return;
        Fireball fireball = (Fireball) event.getEntity();
        if (!(fireball.getShooter() instanceof EnderDragon)) return;
        EnderDragon shooter = (EnderDragon) fireball.getShooter();
        if (!isDragonManaged(shooter)) return;

        // Убираем файербол без взрыва
        fireball.setIsIncendiary(false);
        fireball.remove();
    }

    private boolean isDragonManaged(EnderDragon dragon) {
        for (Dragon d : plugin.getDragonManager().getAllDragons()) {
            if (dragon.equals(d.getEntity())) return true;
        }
        return false;
    }
}
