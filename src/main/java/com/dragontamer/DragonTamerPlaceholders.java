package com.dragontamer;

import com.dragontamer.data.Dragon;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class DragonTamerPlaceholders extends PlaceholderExpansion {
    private final DragonTamerPlugin plugin;

    public DragonTamerPlaceholders(DragonTamerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "dragontamer"; }
    @Override public String getAuthor()     { return "Alexey Sipachyov"; }
    @Override public String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()      { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";
        Dragon dragon = plugin.getDragonManager().getDragon(player.getUniqueId());
        if (dragon == null) return "None";
        switch (identifier.toLowerCase()) {
            case "dragon_type":     return dragon.getType().name();
            case "dragon_level":    return String.valueOf(dragon.getLevel());
            case "dragon_exp":      return String.valueOf(dragon.getExperience());
            case "dragon_health":   return dragon.getEntity() != null && !dragon.getEntity().isDead()
                ? String.format("%.1f", dragon.getEntity().getHealth())
                : String.format("%.1f", dragon.getCurrentHealth());
            case "dragon_max_health": return String.format("%.1f",
                plugin.getDragonManager().getMaxHealth(dragon));
            case "dragon_recovering": return dragon.isRecovering() ? "true" : "false";
            case "dragon_evolution":  return plugin.getDragonManager().getEvolutionSuffix(dragon);
            case "dragon_in_battle":  return plugin.getBattleManager()
                .isInBattle(player.getUniqueId()) ? "true" : "false";
            default: return null;
        }
    }
}
