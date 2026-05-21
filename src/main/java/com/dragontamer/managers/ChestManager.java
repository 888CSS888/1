package com.dragontamer.managers;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChestManager implements Listener {
    private final DragonTamerPlugin plugin;
    private final Map<String, UUID> openChests = new HashMap<>();
    private final Map<UUID, ItemStack[]> chestContents = new HashMap<>();
    private File chestFile;
    private FileConfiguration chestConfig;
    private static final int CHEST_SIZE = 54;

    public ChestManager(DragonTamerPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        chestFile = new File(plugin.getDataFolder(), "chests.yml");
        if (!chestFile.exists()) {
            try { chestFile.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("Failed to create chests.yml: " + e.getMessage());
            }
        }
        chestConfig = YamlConfiguration.loadConfiguration(chestFile);
        if (chestConfig.contains("chests")) {
            for (String key : chestConfig.getConfigurationSection("chests").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<?> rawList = chestConfig.getList("chests." + key);
                    if (rawList == null) continue;
                    ItemStack[] contents = new ItemStack[CHEST_SIZE];
                    for (int i = 0; i < rawList.size() && i < CHEST_SIZE; i++) {
                        Object obj = rawList.get(i);
                        if (obj instanceof ItemStack) contents[i] = (ItemStack) obj;
                    }
                    chestContents.put(uuid, contents);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error loading chest: " + e.getMessage());
                }
            }
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, ItemStack[]> entry : chestContents.entrySet()) saveChest(entry.getKey());
    }

    private void saveChest(UUID uuid) {
        ItemStack[] contents = chestContents.get(uuid);
        if (contents == null) return;
        List<ItemStack> list = new ArrayList<>(Arrays.asList(contents));
        chestConfig.set("chests." + uuid.toString(), list);
        try { chestConfig.save(chestFile); } catch (IOException e) {
            plugin.getLogger().severe("Error saving chest: " + e.getMessage());
        }
    }

    public ItemStack[] getContents(UUID uuid) {
        return chestContents.computeIfAbsent(uuid, k -> new ItemStack[CHEST_SIZE]);
    }

    public void openChest(Player player) {
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        String title = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.chest-title", "&8[&6Dragon Chest&8]"));
        Inventory inv = Bukkit.createInventory(null, CHEST_SIZE, title);
        ItemStack[] stored = getContents(player.getUniqueId());
        inv.setContents(stored.clone());
        openChests.put(player.getUniqueId().toString(), player.getUniqueId());
        player.openInventory(inv);
        if (plugin.getConfig().getBoolean("features.chest-particles", true)) {
            spawnChestParticles(player);
        }
        plugin.getMessageUtils().send(player, "chest-opened");
    }

    private void spawnChestParticles(Player player) {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 10 || !openChests.containsKey(player.getUniqueId().toString())) return;
                try {
                    Location loc = player.getLocation().clone().add(0, 1, 0);
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        double r = 1.5;
                        player.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                            loc.clone().add(r * Math.cos(angle), 0, r * Math.sin(angle)),
                            2, 0.1, 0.1, 0.1, 0.02);
                    }
                } catch (Exception ignored) {}
            }
        }, 0L, 4L);
    }

    public Map<Integer, ItemStack> addItems(UUID uuid, ItemStack... items) {
        ItemStack[] contents = getContents(uuid);
        Inventory tmp = Bukkit.createInventory(null, CHEST_SIZE);
        tmp.setContents(contents.clone());
        Map<Integer, ItemStack> overflow = tmp.addItem(items);
        chestContents.put(uuid, tmp.getContents());
        saveChest(uuid);
        return overflow;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!openChests.containsKey(player.getUniqueId().toString())) return;
        openChests.remove(player.getUniqueId().toString());
        chestContents.put(player.getUniqueId(), event.getInventory().getContents().clone());
        saveChest(player.getUniqueId());
    }
}
