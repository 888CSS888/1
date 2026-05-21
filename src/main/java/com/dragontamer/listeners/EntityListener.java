package com.dragontamer.listeners;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Слушатель событий сущностей.
 *
 * ИСПРАВЛЕНИЯ:
 * 1. Дракон НЕ атакует своего владельца (отменяем урон, если жертва — хозяин).
 * 2. Дракон вне битвы не принимает урон от других игроков.
 * 3. При смерти дракон не дропает лут и опыт.
 */
public class EntityListener implements Listener {

    private final DragonTamerPlugin plugin;

    public EntityListener(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    //  Смерть дракона — убираем дроп и опыт
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof EnderDragon)) return;

        EnderDragon dragonEntity = (EnderDragon) entity;
        Dragon dragon = getDragonByEntity(dragonEntity);
        if (dragon == null) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    // -------------------------------------------------------------------------
    //  Урон дракону — разрешаем только в битве
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();

        // --- ИСПРАВЛЕНИЕ 1: Дракон не бьёт своего владельца ---
        if (event.getDamager() instanceof EnderDragon && victim instanceof Player) {
            EnderDragon dragonEntity = (EnderDragon) event.getDamager();
            Player hitPlayer = (Player) victim;
            Dragon dragon = getDragonByEntity(dragonEntity);

            if (dragon != null && hitPlayer.getUniqueId().equals(dragon.getOwnerUUID())) {
                // Владелец — отменяем урон
                event.setCancelled(true);
                return;
            }
        }

        // --- ИСПРАВЛЕНИЕ 2: Урон дракону разрешён только в битве ---
        if (victim instanceof EnderDragon) {
            EnderDragon dragonEntity = (EnderDragon) victim;
            Dragon dragon = getDragonByEntity(dragonEntity);
            if (dragon == null) return;

            boolean inBattle = plugin.getBattleManager().isInBattle(dragon.getOwnerUUID());
            if (!inBattle) {
                event.setCancelled(true);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  Вспомогательный метод
    // -------------------------------------------------------------------------

    private Dragon getDragonByEntity(EnderDragon entity) {
        for (Dragon dragon : plugin.getDragonManager().getAllDragons()) {
            if (entity.equals(dragon.getEntity())) return dragon;
        }
        return null;
    }
}
