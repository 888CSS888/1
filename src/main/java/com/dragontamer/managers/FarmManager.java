package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class FarmManager {
    private final DragonTamerPlugin plugin;
    private BukkitTask autoFarmTask;

    public FarmManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    public long getFarmIntervalMillis() {
        return plugin.getConfig().getLong("farm-interval", 60) * 60_000L;
    }

    public boolean canFarm(Dragon dragon) {
        if (dragon.getLastFarmTime() == 0) return true;
        return System.currentTimeMillis() - dragon.getLastFarmTime() >= getFarmIntervalMillis();
    }

    public long getRemainingFarmMinutes(Dragon dragon) {
        long remaining = getFarmIntervalMillis() - (System.currentTimeMillis() - dragon.getLastFarmTime());
        return remaining <= 0 ? 0 : remaining / 60_000L;
    }

    public boolean doFarm(Player player, Dragon dragon) {
        if (!canFarm(dragon)) return false;
        List<ItemStack> items = getResources(dragon);
        deliverItems(player, dragon, items, false);
        dragon.setLastFarmTime(System.currentTimeMillis());
        plugin.getDataManager().saveDragon(dragon);
        return true;
    }

    public void startAutoFarmTask() {
        if (autoFarmTask != null) autoFarmTask.cancel();
        long intervalTicks = plugin.getConfig().getLong("farm-interval", 60) * 60 * 20L;
        autoFarmTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Dragon dragon : plugin.getDragonManager().getAllDragons()) {
                if (!dragon.isAutoFarmMode()) continue;
                if (dragon.isRecovering()) continue;
                if (!canFarm(dragon)) continue;
                Player owner = Bukkit.getPlayer(dragon.getOwnerUUID());
                if (owner == null || !owner.isOnline()) continue;
                List<ItemStack> items = getResources(dragon);
                deliverItems(owner, dragon, items, true);
                dragon.setLastFarmTime(System.currentTimeMillis());
                plugin.getDataManager().saveDragon(dragon);
            }
        }, intervalTicks, intervalTicks);
    }

    public void stopAutoFarmTask() {
        if (autoFarmTask != null) { autoFarmTask.cancel(); autoFarmTask = null; }
    }

    private List<ItemStack> getResources(Dragon dragon) {
        List<String> resourceStrings = plugin.getConfig().getStringList(
            "dragon-types." + dragon.getType().name() + ".farm-resources");
        List<ItemStack> items = new ArrayList<>();
        for (String entry : resourceStrings) {
            String[] parts = entry.split(":");
            if (parts.length < 1) continue;
            try {
                Material mat = Material.getMaterial(parts[0]);
                if (mat == null || mat == Material.AIR) continue;
                int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                items.add(new ItemStack(mat, amount));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid resource: " + entry);
            }
        }
        return items;
    }

    private void deliverItems(Player player, Dragon dragon, List<ItemStack> items, boolean isAuto) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (!overflow.isEmpty()) {
            ItemStack[] overflowArr = overflow.values().toArray(new ItemStack[0]);
            Map<Integer, ItemStack> chestOverflow = plugin.getChestManager().addItems(player.getUniqueId(), overflowArr);
            if (!chestOverflow.isEmpty()) {
                for (ItemStack item : chestOverflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                plugin.getMessageUtils().send(player, "chest-overflow");
            } else {
                plugin.getMessageUtils().send(player, "farm-to-chest");
            }
        }
        if (isAuto) plugin.getMessageUtils().send(player, "dragon-autofarm");
    }
}
