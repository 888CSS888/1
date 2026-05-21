package com.dragontamer.listeners;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import com.dragontamer.managers.DragonCollectionMenu;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import java.util.*;

public class CollectionListener implements Listener {
    private final DragonTamerPlugin plugin;
    private final Set<UUID> openMenus = new HashSet<>();

    public CollectionListener(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    public void trackOpen(UUID playerUUID) {
        openMenus.add(playerUUID);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!openMenus.contains(player.getUniqueId())) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot < 0 || slot >= 45) return;

        List<Dragon> collection = plugin.getDragonManager().getCollection(player.getUniqueId());
        if (slot >= collection.size()) return;

        Dragon selected = collection.get(slot);
        Dragon activeDragon = plugin.getDragonManager().getDragon(player.getUniqueId());

        if (activeDragon != null && activeDragon.getDragonId().equals(selected.getDragonId())) {
            player.sendMessage(ChatColor.YELLOW + "✦ Этот дракон уже активен!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BASS, 0.8f, 0.5f);
            return;
        }

        if (selected.isRecovering()) {
            player.sendMessage(ChatColor.RED + "✘ Дракон ещё восстанавливается! (" +
                selected.getRecoveryRemainingMinutes() + " мин.)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        boolean switched = plugin.getDragonManager().switchActiveDragon(player, selected.getDragonId());
        if (switched) {
            String typeName = plugin.getConfig().getString(
                "dragon-types." + selected.getType().name() + ".display-name",
                selected.getType().getDisplayName());
            player.sendMessage(ChatColor.GREEN + "✔ Вы выбрали дракона: " +
                ChatColor.translateAlternateColorCodes('&', selected.getType().getColorCode() + typeName) +
                ChatColor.GREEN + " (Ур. " + selected.getLevel() + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERDRAGON_GROWL, 0.5f, 1.3f);

            plugin.getCollectionMenu().open(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!openMenus.contains(player.getUniqueId())) return;
        openMenus.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ENDERCHEST_CLOSE, 0.6f, 1.1f);
    }
}
