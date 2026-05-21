package com.dragontamer.utils;

import com.dragontamer.DragonTamerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtils {
    private final DragonTamerPlugin plugin;

    public MessageUtils(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    public String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String getPrefix() {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&6DragonTamer&8] ");
        return colorize(prefix);
    }

    public String get(String key) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null) {
            plugin.getLogger().warning("⚠ Message not found: " + key);
            // Возвращаем стандартное сообщение вместо ошибки
            switch (key) {
                case "battle-request-sent": return "&6Вы вызвали {target} на битву!";
                case "battle-request-received": return "&6{challenger} вызывает вас на битву! /dr accept";
                case "battle-accepted": return "&aБитва принята!";
                case "battle-rejected": return "&c{target} отклонил вызов";
                case "battle-start": return "&c⚔ БИТВА НАЧАЛАСЬ!";
                default: return "&cСообщение не найдено: " + key;
            }
        }
        return colorize(raw);
    }

    public String get(String key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String target = replacements[i];
            String value = replacements[i + 1];
            if (msg.contains(target)) {
                msg = msg.replace(target, value);
            }
        }
        return msg;
    }

    public void send(CommandSender sender, String key, String... replacements) {
        if (sender == null) return;
        String msg = get(key, replacements);
        sender.sendMessage(getPrefix() + msg);
    }

    public void sendRaw(CommandSender sender, String message) {
        if (sender == null) return;
        sender.sendMessage(getPrefix() + colorize(message));
    }
}
