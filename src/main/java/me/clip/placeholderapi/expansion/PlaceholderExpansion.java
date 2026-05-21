package me.clip.placeholderapi.expansion;

import org.bukkit.entity.Player;

/**
 * Compile-time stub for PlaceholderAPI.
 * The real class is provided at runtime by the PlaceholderAPI plugin (softdepend).
 */
public abstract class PlaceholderExpansion {
    public abstract String getIdentifier();
    public abstract String getAuthor();
    public abstract String getVersion();
    public boolean persist() { return true; }
    public boolean canRegister() { return true; }
    public boolean register() { return true; }
    public abstract String onPlaceholderRequest(Player player, String identifier);
}
