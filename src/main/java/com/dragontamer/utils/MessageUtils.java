package com.dragontamer.utils;

import com.dragontamer.DragonTamerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Level;

public class MessageUtils {

    private final DragonTamerPlugin plugin;
    private FileConfiguration messagesConfig;

    public MessageUtils(DragonTamerPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    public String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String getPrefix() {
        return colorize(messagesConfig.getString("prefix", "&8[&6DragonTamer&8] &r"));
    }

    /**
     * Получить строку сообщения по ключу с подстановкой плейсхолдеров.
     * @param key    ключ в messages.yml (раздел messages.)
     * @param placeholders пары placeholder, value
     */
    public String get(String key, String... placeholders) {
        String raw = messagesConfig.getString("messages." + key, "&cСообщение не найдено: " + key);
        raw = applyPlaceholders(raw, placeholders);
        return colorize(raw);
    }

    /**
     * Отправить сообщение по ключу с плейсхолдерами.
     */
    public void send(CommandSender sender, String key, String... placeholders) {
        String msg = get(key, placeholders);
        if (msg.contains("\n")) {
            for (String line : msg.split("\n")) {
                sender.sendMessage(getPrefix() + line);
            }
        } else {
            sender.sendMessage(getPrefix() + msg);
        }
    }

    /**
     * Отправить сырое сообщение (с подстановкой цветов).
     */
    public void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(getPrefix() + colorize(text));
    }

    private String applyPlaceholders(String text, String[] placeholders) {
        if (placeholders == null || placeholders.length == 0) return text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            if (placeholders[i] != null && placeholders[i + 1] != null) {
                text = text.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return text;
    }
}
