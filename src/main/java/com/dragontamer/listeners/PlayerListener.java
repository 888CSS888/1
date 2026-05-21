package com.dragontamer.listeners;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import com.dragontamer.data.DragonType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PlayerListener implements Listener {
    private final DragonTamerPlugin plugin;

    public PlayerListener(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) return;
        dragon.setOwnerName(player.getName());
        if (dragon.isRecovering()) return;
        if (plugin.getDragonManager().isBlockedWorld(player.getWorld().getName())) return;
        plugin.getDragonManager().spawnDragonForPlayer(player, dragon);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getBattleManager().isWatcher(player.getUniqueId()))
            plugin.getBattleManager().removeWatcher(player.getUniqueId());

        for (Dragon dragon : plugin.getDragonManager().getCollection(player.getUniqueId())) {
            if (dragon.getEntity() != null && !dragon.getEntity().isDead())
                dragon.setCurrentHealth(dragon.getEntity().getHealth());
            plugin.getDragonManager().despawnDragon(dragon);
            plugin.getDataManager().saveDragon(dragon);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (plugin.getBattleManager().isInBattle(player.getUniqueId())) return;
        if (plugin.getBattleManager().isWatcher(player.getUniqueId()))  return;

        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) return;

        if (dragon.getEntity() != null && !dragon.getEntity().isDead())
            dragon.setCurrentHealth(dragon.getEntity().getHealth());

        plugin.getDragonManager().despawnDragon(dragon);

        String newWorld = player.getWorld().getName();
        if (!dragon.isRecovering() && !plugin.getDragonManager().isBlockedWorld(newWorld)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getWorld().getName().equals(newWorld))
                    plugin.getDragonManager().spawnDragonForPlayer(player, dragon);
            }, 10L);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return;

        DragonType type = getDragonTypeFromEggName(meta.getDisplayName());
        if (type == null) return;

        event.setCancelled(true);

        if (plugin.getDragonManager().isBlockedWorld(player.getWorld().getName())) {
            plugin.getMessageUtils().send(player, "invalid-world");
            return;
        }

        int maxDragons = plugin.getConfig().getInt("max-dragons-per-player", 10);
        if (plugin.getDragonManager().getCollectionSize(player.getUniqueId()) >= maxDragons) {
            plugin.getMessageUtils().send(player, "collection-full", "{max}", String.valueOf(maxDragons));
            return;
        }

        boolean hadActiveDragon = plugin.getDragonManager().hasDragon(player.getUniqueId());
        Dragon dragon = plugin.getDragonManager().createDragon(player.getUniqueId(), player.getName(), type);
        plugin.getDragonManager().spawnDragonForPlayer(player, dragon);

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        String typeName = plugin.getConfig().getString(
            "dragon-types." + type.name() + ".display-name", type.getDisplayName());
        if (hadActiveDragon)
            plugin.getMessageUtils().send(player, "dragon-moved-to-collection");
        plugin.getMessageUtils().send(player, "egg-used", "{type}", typeName);
        player.sendMessage(ChatColor.GOLD + "→ Используйте " +
            ChatColor.AQUA + "/dr collection " + ChatColor.GOLD +
            "чтобы управлять всеми своими драконами!");
    }

    private DragonType getDragonTypeFromEggName(String displayName) {
        String clean = ChatColor.stripColor(displayName).toLowerCase();
        if (clean.contains("fire")    || clean.contains("огнен"))   return DragonType.FIRE;
        if (clean.contains("ice")     || clean.contains("ледян"))    return DragonType.ICE;
        if (clean.contains("shadow")  || clean.contains("тенев"))   return DragonType.SHADOW;
        if (clean.contains("emerald") || clean.contains("изумруд")) return DragonType.EMERALD;
        return null;
    }
}
