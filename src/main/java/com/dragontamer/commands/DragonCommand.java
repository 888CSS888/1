package com.dragontamer.commands;

import com.dragontamer.DragonTamerPlugin;
import com.dragontamer.data.Dragon;
import com.dragontamer.data.DragonType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class DragonCommand implements CommandExecutor, TabCompleter {
    private final DragonTamerPlugin plugin;

    public DragonCommand(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            // Консольные команды
            if (args.length >= 1) {
                switch (args[0].toLowerCase()) {
                    case "reload":
                        handleAdminReload(sender);
                        return true;
                    case "give":
                        handleAdminGive(sender, args);
                        return true;
                    case "remove":
                        handleAdminRemove(sender, args);
                        return true;
                }
            }
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            // Основные команды
            case "info":
                handleInfo(player);
                break;
            case "follow":
                handleFollow(player);
                break;
            case "stay":
                handleStay(player);
                break;
            case "farm":
                handleFarm(player);
                break;
            case "top":
                handleTop(player);
                break;
            case "collection":
                handleCollection(player);
                break;
            case "name":
                handleName(player, args);
                break;
            
            // Боевые команды
            case "battle":
                handleBattle(player, args);
                break;
            case "accept":
                handleAccept(player);
                break;
            case "reject":
                handleReject(player);
                break;
            case "dodge":
                handleDodge(player, args);
                break;
            case "watch":
                handleWatch(player, args);
                break;
            
            // Команды орбиты и автофермы
            case "orbit":
                handleOrbit(player);
                break;
            case "autofarm":
                handleAutoFarm(player);
                break;
            case "chest":
                handleChest(player);
                break;
            
            // Админские команды
            case "give":
                handleAdminGive(sender, args);
                break;
            case "remove":
                handleAdminRemove(sender, args);
                break;
            case "setlevel":
                handleAdminSetLevel(sender, args);
                break;
            case "reload":
                handleAdminReload(sender);
                break;
            
            default:
                showHelp(player);
                break;
        }
        return true;
    }

    // =========================================================================
    //  ОСНОВНЫЕ КОМАНДЫ
    // =========================================================================

    private void handleInfo(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        String typeName = plugin.getConfig().getString(
            "dragon-types." + dragon.getType().name() + ".display-name", 
            dragon.getType().getDisplayName());
        String evo = plugin.getDragonManager().getEvolutionSuffix(dragon);
        String name = dragon.getName(evo, ChatColor.translateAlternateColorCodes('&', typeName));
        double maxHp = plugin.getDragonManager().getMaxHealth(dragon);
        double curHp = dragon.getEntity() != null && !dragon.getEntity().isDead()
            ? dragon.getEntity().getHealth() : dragon.getCurrentHealth();
        long nextExp = plugin.getDragonManager().getExpForNextLevel(dragon);
        int total = plugin.getDragonManager().getCollectionSize(player.getUniqueId());
        
        plugin.getMessageUtils().send(player, "dragon-info",
            "{name}", name,
            "{type}", dragon.getType().name(),
            "{level}", String.valueOf(dragon.getLevel()),
            "{exp}", String.valueOf(dragon.getExperience()),
            "{maxexp}", String.valueOf(nextExp),
            "{hp}", String.format("%.1f", curHp),
            "{maxhp}", String.format("%.1f", maxHp),
            "{mode}", dragon.isOrbitMode() ? "Орбита" : (dragon.isFollowing() ? "Следует" : "Стоит"));
        
        player.sendMessage(ChatColor.GRAY + "Драконов в коллекции: " +
            ChatColor.YELLOW + total + ChatColor.GRAY + " (используйте /dr collection)");
        
        if (dragon.isRecovering()) {
            plugin.getMessageUtils().send(player, "dragon-recovering",
                "{time}", String.valueOf(dragon.getRecoveryRemainingMinutes()));
        }
    }

    private void handleFollow(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        if (dragon.isRecovering()) {
            plugin.getMessageUtils().send(player, "dragon-recovering",
                "{time}", String.valueOf(dragon.getRecoveryRemainingMinutes()));
            return;
        }
        
        if (plugin.getDragonManager().isBlockedWorld(player.getWorld().getName())) {
            plugin.getMessageUtils().send(player, "invalid-world");
            return;
        }
        
        dragon.setFollowing(true);
        dragon.setOrbitMode(false);
        
        if (dragon.getEntity() == null || dragon.getEntity().isDead()) {
            plugin.getDragonManager().spawnDragonForPlayer(player, dragon);
        }
        
        plugin.getDataManager().saveDragon(dragon);
        plugin.getMessageUtils().send(player, "dragon-follow");
    }

    private void handleStay(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        dragon.setFollowing(false);
        dragon.setOrbitMode(false);
        plugin.getDataManager().saveDragon(dragon);
        plugin.getMessageUtils().send(player, "dragon-stay");
    }

    private void handleFarm(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        if (dragon.isRecovering()) {
            plugin.getMessageUtils().send(player, "dragon-recovering",
                "{time}", String.valueOf(dragon.getRecoveryRemainingMinutes()));
            return;
        }
        
        if (!plugin.getFarmManager().canFarm(dragon)) {
            plugin.getMessageUtils().send(player, "farm-cooldown",
                "{time}", String.valueOf(plugin.getFarmManager().getRemainingFarmMinutes(dragon)));
            return;
        }
        
        plugin.getFarmManager().doFarm(player, dragon);
        plugin.getMessageUtils().send(player, "dragon-farm");
    }

    private void handleTop(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        List<Dragon> top = plugin.getDragonManager().getTopDragons(10);
        player.sendMessage(plugin.getMessageUtils().colorize(
            plugin.getConfig().getString("messages.top-header", "&6=== Топ Драконов ===")));
        
        if (top.isEmpty()) {
            player.sendMessage(plugin.getMessageUtils().colorize(
                plugin.getConfig().getString("messages.top-empty", "&7Пока нет драконов.")));
            return;
        }
        
        for (int i = 0; i < top.size(); i++) {
            Dragon d = top.get(i);
            String tn = plugin.getConfig().getString(
                "dragon-types." + d.getType().name() + ".display-name", d.getType().getDisplayName());
            player.sendMessage(plugin.getMessageUtils().get("top-entry",
                "{rank}", String.valueOf(i + 1),
                "{name}", d.getOwnerName(),
                "{type}", tn,
                "{level}", String.valueOf(d.getLevel())));
        }
    }

    private void handleCollection(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        if (plugin.getDragonManager().getCollectionSize(player.getUniqueId()) == 0) {
            plugin.getMessageUtils().sendRaw(player, "&cУ вас нет ни одного дракона!");
            return;
        }
        
        plugin.getCollectionMenu().open(player);
    }

    private void handleName(Player player, String[] args) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        if (args.length < 2) {
            plugin.getMessageUtils().sendRaw(player, "&cИспользование: /dr name <имя>");
            if (dragon.hasNickname()) {
                plugin.getMessageUtils().sendRaw(player, "&7Текущее имя: &e" + dragon.getNickname());
            }
            return;
        }
        
        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // Сброс имени
        if (newName.equalsIgnoreCase("сброс") || newName.equalsIgnoreCase("reset")
                || newName.equalsIgnoreCase("clear") || newName.equalsIgnoreCase("убрать")) {
            String old = dragon.getNickname();
            dragon.setNickname(null);
            refreshDragonNameTag(player, dragon);
            plugin.getDataManager().saveDragon(dragon);
            plugin.getMessageUtils().send(player, "dragon-name-cleared",
                "{old}", old != null ? old : "—");
            return;
        }
        
        // Проверка длины
        int maxLen = plugin.getConfig().getInt("dragon-name-max-length", 20);
        if (newName.length() > maxLen) {
            plugin.getMessageUtils().sendRaw(player,
                "&cИмя слишком длинное! Максимум &e" + maxLen + " &cсимволов.");
            return;
        }
        
        // Удаление цветов если нет прав
        if (newName.contains("&") && !player.hasPermission("dragontamer.colorname")) {
            newName = newName.replace("&", "");
        }
        
        String finalName = player.hasPermission("dragontamer.colorname")
            ? ChatColor.translateAlternateColorCodes('&', newName) : newName;
        
        dragon.setNickname(finalName);
        refreshDragonNameTag(player, dragon);
        plugin.getDataManager().saveDragon(dragon);
        
        plugin.getMessageUtils().send(player, "dragon-named",
            "{name}", finalName,
            "{type}", plugin.getConfig().getString(
                "dragon-types." + dragon.getType().name() + ".display-name",
                dragon.getType().getDisplayName()));
    }

    private void refreshDragonNameTag(Player player, Dragon dragon) {
        if (dragon.getEntity() != null && !dragon.getEntity().isDead()) {
            dragon.getEntity().setCustomName(ChatColor.translateAlternateColorCodes('&',
                plugin.getDragonManager().getDragonDisplayName(dragon)));
        }
    }

    // =========================================================================
    //  БОЕВЫЕ КОМАНДЫ
    // =========================================================================

    private void handleBattle(Player player, String[] args) {
        if (!player.hasPermission("dragontamer.battle")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        if (args.length < 2) {
            plugin.getMessageUtils().sendRaw(player, "&cИспользование: /dr battle <игрок>");
            return;
        }
        
        Dragon myDragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (myDragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        if (myDragon.isRecovering()) {
            plugin.getMessageUtils().send(player, "dragon-recovering",
                "{time}", String.valueOf(myDragon.getRecoveryRemainingMinutes()));
            return;
        }
        
        if (plugin.getBattleManager().isInBattle(player.getUniqueId())) {
            plugin.getMessageUtils().sendRaw(player, "&cВы уже участвуете в битве!");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.getMessageUtils().sendRaw(player, "&cИгрок не найден: " + args[1]);
            return;
        }
        
        if (target.equals(player)) {
            plugin.getMessageUtils().sendRaw(player, "&cВы не можете сражаться сами с собой!");
            return;
        }
        
        Dragon targetDragon = plugin.getDragonManager().getDragon(target.getUniqueId());
        if (targetDragon == null) {
            plugin.getMessageUtils().send(player, "target-no-dragon", "{target}", target.getName());
            return;
        }
        
        if (targetDragon.isRecovering()) {
            plugin.getMessageUtils().sendRaw(player, "&cДракон " + target.getName() + " восстанавливается!");
            return;
        }
        
        if (plugin.getBattleManager().isInBattle(target.getUniqueId())) {
            plugin.getMessageUtils().sendRaw(player, "&c" + target.getName() + " уже в битве!");
            return;
        }
        
        // Отправляем запрос на битву
        plugin.getBattleManager().sendChallenge(player, target);
        plugin.getMessageUtils().send(player, "battle-request-sent", "{target}", target.getName());
        plugin.getMessageUtils().send(target, "battle-request-received", "{challenger}", player.getName());
    }

    private void handleAccept(Player player) {
        if (!player.hasPermission("dragontamer.battle")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        plugin.getBattleManager().acceptChallenge(player);
    }

    private void handleReject(Player player) {
        if (!player.hasPermission("dragontamer.battle")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        plugin.getBattleManager().rejectChallenge(player);
    }

    private void handleDodge(Player player, String[] args) {
        if (!player.hasPermission("dragontamer.battle")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        if (!plugin.getBattleManager().isInBattle(player.getUniqueId())) {
            plugin.getMessageUtils().send(player, "dodge-not-in-battle");
            return;
        }
        
        if (args.length < 2) {
            plugin.getMessageUtils().sendRaw(player, "&cИспользование: /dr dodge <left|right|up>");
            return;
        }
        
        plugin.getBattleManager().doDodge(player, args[1]);
    }

    private void handleWatch(Player player, String[] args) {
        if (!player.hasPermission("dragontamer.battle")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        if (args.length < 2) {
            plugin.getMessageUtils().send(player, "battle-watch-usage");
            return;
        }
        
        if (plugin.getBattleManager().isInBattle(player.getUniqueId())) {
            plugin.getMessageUtils().send(player, "battle-watch-in-battle");
            return;
        }
        
        if (plugin.getBattleManager().isWatcher(player.getUniqueId())) {
            plugin.getMessageUtils().send(player, "battle-watch-already");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.getMessageUtils().sendRaw(player, "&cИгрок не найден: " + args[1]);
            return;
        }
        
        UUID battleKey = plugin.getBattleManager().getBattleKey(target.getUniqueId());
        if (battleKey == null) {
            plugin.getMessageUtils().send(player, "battle-watch-not-found",
                "{target}", target.getName());
            return;
        }
        
        plugin.getBattleManager().watchBattle(player, battleKey);
    }

    // =========================================================================
    //  ОРБИТА И АВТОФЕРМА
    // =========================================================================

    private void handleOrbit(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        if (!plugin.getConfig().getBoolean("features.orbit", true)) {
            plugin.getMessageUtils().sendRaw(player, "&cРежим орбиты отключён в конфигурации!");
            return;
        }
        
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        if (dragon.isRecovering()) {
            plugin.getMessageUtils().send(player, "dragon-recovering",
                "{time}", String.valueOf(dragon.getRecoveryRemainingMinutes()));
            return;
        }
        
        if (plugin.getDragonManager().isBlockedWorld(player.getWorld().getName())) {
            plugin.getMessageUtils().send(player, "invalid-world");
            return;
        }
        
        boolean newMode = !dragon.isOrbitMode();
        dragon.setOrbitMode(newMode);
        dragon.setFollowing(newMode);
        
        if (newMode) {
            plugin.getOrbitManager().resetAngle(player.getUniqueId());
            if (dragon.getEntity() == null || dragon.getEntity().isDead()) {
                plugin.getDragonManager().spawnDragonForPlayer(player, dragon);
            }
            plugin.getMessageUtils().send(player, "orbit-enabled");
        } else {
            plugin.getMessageUtils().send(player, "orbit-disabled");
        }
        
        plugin.getDataManager().saveDragon(dragon);
    }

    private void handleAutoFarm(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        if (!plugin.getConfig().getBoolean("features.auto-farm", true)) {
            plugin.getMessageUtils().sendRaw(player, "&cАвто-ферма отключена в конфигурации!");
            return;
        }
        
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) {
            plugin.getMessageUtils().send(player, "no-dragon");
            return;
        }
        
        boolean newMode = !dragon.isAutoFarmMode();
        dragon.setAutoFarmMode(newMode);
        plugin.getDataManager().saveDragon(dragon);
        
        plugin.getMessageUtils().send(player, newMode ? "autofarm-enabled" : "autofarm-disabled",
            "{interval}", String.valueOf(plugin.getConfig().getLong("farm-interval", 60)));
    }

    private void handleChest(Player player) {
        if (!player.hasPermission("dragontamer.use")) {
            plugin.getMessageUtils().send(player, "no-permission");
            return;
        }
        
        if (!plugin.getConfig().getBoolean("features.chest", true)) {
            plugin.getMessageUtils().sendRaw(player, "&cСундук дракона отключён в конфигурации!");
            return;
        }
        
        plugin.getChestManager().openChest(player);
    }

    // =========================================================================
    //  АДМИНСКИЕ КОМАНДЫ
    // =========================================================================

    private void handleAdminGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dragontamer.admin")) {
            plugin.getMessageUtils().send((Player) sender, "no-permission");
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /dr give <игрок> <тип>");
            sender.sendMessage(ChatColor.RED + "Доступные типы: FIRE, ICE, SHADOW, EMERALD");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден: " + args[1]);
            return;
        }
        
        DragonType type = DragonType.fromString(args[2]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "Неверный тип: " + args[2]);
            return;
        }
        
        target.getInventory().addItem(createDragonEgg(type));
        String tn = plugin.getConfig().getString(
            "dragon-types." + type.name() + ".display-name", type.getDisplayName());
        sender.sendMessage(ChatColor.GREEN + "Выдано яйцо " + tn + " игроку " + target.getName());
    }

    private void handleAdminRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dragontamer.admin")) {
            plugin.getMessageUtils().send((Player) sender, "no-permission");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /dr remove <игрок>");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target != null) {
            plugin.getDragonManager().removeDragon(target.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "Удалён активный дракон игрока " + target.getName());
        } else {
            Dragon d = plugin.getDragonManager().getDragonByName(args[1]);
            if (d != null) {
                plugin.getDragonManager().removeDragon(d.getOwnerUUID());
                sender.sendMessage(ChatColor.GREEN + "Удалён активный дракон игрока " + args[1]);
            } else {
                sender.sendMessage(ChatColor.RED + "Дракон не найден для " + args[1]);
            }
        }
    }

    private void handleAdminSetLevel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dragontamer.admin")) {
            plugin.getMessageUtils().send((Player) sender, "no-permission");
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /dr setlevel <игрок> <уровень>");
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[2]);
            if (level < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Неверный уровень: " + args[2]);
            return;
        }
        
        Dragon dragon = null;
        Player target = Bukkit.getPlayer(args[1]);
        if (target != null) {
            dragon = plugin.getDragonManager().getDragon(target.getUniqueId());
        }
        if (dragon == null) {
            dragon = plugin.getDragonManager().getDragonByName(args[1]);
        }
        if (dragon == null) {
            sender.sendMessage(ChatColor.RED + "Дракон не найден для " + args[1]);
            return;
        }
        
        dragon.setLevel(level);
        dragon.setExperience(0);
        
        if (dragon.getEntity() != null && !dragon.getEntity().isDead()) {
            dragon.getEntity().setMaxHealth(plugin.getDragonManager().getMaxHealth(dragon));
            dragon.getEntity().setHealth(plugin.getDragonManager().getMaxHealth(dragon));
        }
        
        plugin.getDataManager().saveDragon(dragon);
        sender.sendMessage(ChatColor.GREEN + "Уровень дракона " + args[1] + " установлен: " + level);
    }

    private void handleAdminReload(CommandSender sender) {
        if (!sender.hasPermission("dragontamer.admin")) {
            plugin.getMessageUtils().send((Player) sender, "no-permission");
            return;
        }
        
        plugin.reloadConfig();
        plugin.getDataManager().reload();
        sender.sendMessage(ChatColor.GREEN + "[DragonTamer] Конфигурация перезагружена.");
    }

    // =========================================================================
    //  HELP
    // =========================================================================

    private void showHelp(Player player) {
        player.sendMessage(plugin.getMessageUtils().colorize("&6=== DragonTamer v1.3.2 ==="));
        player.sendMessage(plugin.getMessageUtils().colorize("&b/dr info &7— Информация о драконе"));
        player.sendMessage(plugin.getMessageUtils().colorize("&b/dr follow &7— Следовать за вами"));
        player.sendMessage(plugin.getMessageUtils().colorize("&b/dr stay &7— Стоять на месте"));
        player.sendMessage(plugin.getMessageUtils().colorize("&b/dr farm &7— Собрать ресурсы"));
        player.sendMessage(plugin.getMessageUtils().colorize("&b/dr top &7— Топ драконов"));
        player.sendMessage(plugin.getMessageUtils().colorize("&b/dr collection &7— Открыть коллекцию"));
        player.sendMessage(plugin.getMessageUtils().colorize("&b/dr name <имя|сброс> &7— Переименовать"));
        
        if (plugin.getConfig().getBoolean("features.orbit", true)) {
            player.sendMessage(plugin.getMessageUtils().colorize("&b/dr orbit &7— Режим орбиты"));
        }
        
        if (plugin.getConfig().getBoolean("features.auto-farm", true)) {
            player.sendMessage(plugin.getMessageUtils().colorize("&b/dr autofarm &7— Авто-ферма"));
        }
        
        if (plugin.getConfig().getBoolean("features.chest", true)) {
            player.sendMessage(plugin.getMessageUtils().colorize("&b/dr chest &7— Сундук дракона"));
        }
        
        if (player.hasPermission("dragontamer.battle")) {
            player.sendMessage(plugin.getMessageUtils().colorize("&a/dr battle <игрок> &7— Вызвать на битву"));
            player.sendMessage(plugin.getMessageUtils().colorize("&a/dr accept/reject &7— Принять/отклонить вызов"));
            player.sendMessage(plugin.getMessageUtils().colorize("&a/dr dodge <left|right|up> &7— Уклонение"));
            player.sendMessage(plugin.getMessageUtils().colorize("&a/dr watch <игрок> &7— Наблюдать за битвой"));
        }
        
        if (player.hasPermission("dragontamer.admin")) {
            player.sendMessage(plugin.getMessageUtils().colorize("&c/dr give <игрок> <тип> &7— Выдать яйцо"));
            player.sendMessage(plugin.getMessageUtils().colorize("&c/dr remove <игрок> &7— Удалить дракона"));
            player.sendMessage(plugin.getMessageUtils().colorize("&c/dr setlevel <игрок> <уровень>"));
            player.sendMessage(plugin.getMessageUtils().colorize("&c/dr reload &7— Перезагрузить конфиг"));
        }
    }

    // =========================================================================
    //  TAB COMPLETION
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        
        Player player = (Player) sender;
        
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                "info", "follow", "stay", "farm", "top", "collection", "name",
                "battle", "accept", "reject", "dodge", "watch"));
            
            if (plugin.getConfig().getBoolean("features.orbit", true)) {
                subs.add("orbit");
            }
            if (plugin.getConfig().getBoolean("features.auto-farm", true)) {
                subs.add("autofarm");
            }
            if (plugin.getConfig().getBoolean("features.chest", true)) {
                subs.add("chest");
            }
            if (player.hasPermission("dragontamer.admin")) {
                subs.addAll(Arrays.asList("give", "remove", "setlevel", "reload"));
            }
            
            String q = args[0].toLowerCase();
            subs.removeIf(s -> !s.startsWith(q));
            return subs;
        }
        
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("battle") || sub.equals("remove") || sub.equals("watch")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player)) names.add(p.getName());
                }
                return names;
            }
            if (sub.equals("setlevel") || sub.equals("give")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return names;
            }
            if (sub.equals("dodge")) {
                return Arrays.asList("left", "right", "up");
            }
            if (sub.equals("name")) {
                return Arrays.asList("сброс", "reset", "clear");
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> types = new ArrayList<>();
            for (DragonType t : DragonType.values()) {
                types.add(t.name().toLowerCase());
            }
            return types;
        }
        
        return Collections.emptyList();
    }

    // =========================================================================
    //  HELPER
    // =========================================================================

    private ItemStack createDragonEgg(DragonType type) {
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        ItemMeta meta = egg.getItemMeta();
        
        String tn = plugin.getConfig().getString(
            "dragon-types." + type.name() + ".display-name", type.getDisplayName());
        
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getColorCode() + tn));
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Правый клик по блоку, чтобы вылупить!");
        lore.add(ChatColor.GRAY + "Тип: " + ChatColor.translateAlternateColorCodes('&', tn));
        meta.setLore(lore);
        
        egg.setItemMeta(meta);
        return egg;
    }
}