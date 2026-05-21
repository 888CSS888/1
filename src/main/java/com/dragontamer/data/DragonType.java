package com.dragontamer.data;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum DragonType {
    FIRE("Огненный Дракон", Particle.FLAME, Particle.EXPLOSION_LARGE,
         Sound.ENTITY_BLAZE_AMBIENT, Color.RED, "&c"),
    ICE("Ледяной Дракон", Particle.SNOWBALL, Particle.CLOUD,
        Sound.BLOCK_GLASS_BREAK, Color.AQUA, "&b"),
    SHADOW("Теневой Дракон", Particle.SMOKE_LARGE, Particle.SPELL_MOB,
           Sound.ENTITY_WITHER_AMBIENT, Color.PURPLE, "&8"),
    EMERALD("Изумрудный Дракон", Particle.VILLAGER_HAPPY, Particle.SPELL_WITCH,
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP, Color.GREEN, "&a");

    private final String displayName;
    private final Particle trailParticle;
    private final Particle ambientParticle;
    private final Sound roarSound;
    private final Color particleColor;
    private final String colorCode;

    DragonType(String displayName, Particle trailParticle, Particle ambientParticle,
               Sound roarSound, Color particleColor, String colorCode) {
        this.displayName = displayName;
        this.trailParticle = trailParticle;
        this.ambientParticle = ambientParticle;
        this.roarSound = roarSound;
        this.particleColor = particleColor;
        this.colorCode = colorCode;
    }

    public String getDisplayName() { return displayName; }
    public Particle getTrailParticle() { return trailParticle; }
    public Particle getAmbientParticle() { return ambientParticle; }
    public Sound getRoarSound() { return roarSound; }
    public Color getParticleColor() { return particleColor; }
    public String getColorCode() { return colorCode; }

    public static DragonType fromString(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
