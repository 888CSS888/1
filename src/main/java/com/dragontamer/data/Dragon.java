package com.dragontamer.data;

import org.bukkit.entity.EnderDragon;
import java.util.UUID;

public class Dragon {
    private UUID dragonId;
    private final UUID ownerUUID;
    private String ownerName;
    private DragonType type;
    private int level;
    private long experience;
    private double currentHealth;
    private boolean following;
    private long lastFarmTime;
    private long recoveryEndTime;
    private String location;
    private boolean orbitMode;
    private boolean autoFarmMode;
    private String nickname;
    private transient EnderDragon entity;

    public Dragon(UUID ownerUUID, String ownerName, DragonType type) {
        this.dragonId = UUID.randomUUID();
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.type = type;
        this.level = 1;
        this.experience = 0;
        this.following = true;
        this.lastFarmTime = 0;
        this.recoveryEndTime = 0;
        this.location = null;
        this.orbitMode = false;
        this.autoFarmMode = false;
    }

    public UUID getDragonId() { return dragonId; }
    public void setDragonId(UUID id) { this.dragonId = id; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String n) { this.ownerName = n; }
    public DragonType getType() { return type; }
    public void setType(DragonType t) { this.type = t; }
    public int getLevel() { return level; }
    public void setLevel(int l) { this.level = l; }
    public long getExperience() { return experience; }
    public void setExperience(long e) { this.experience = e; }
    public double getCurrentHealth() { return currentHealth; }
    public void setCurrentHealth(double h) { this.currentHealth = h; }
    public boolean isFollowing() { return following; }
    public void setFollowing(boolean f) { this.following = f; }
    public long getLastFarmTime() { return lastFarmTime; }
    public void setLastFarmTime(long t) { this.lastFarmTime = t; }
    public long getRecoveryEndTime() { return recoveryEndTime; }
    public void setRecoveryEndTime(long t) { this.recoveryEndTime = t; }
    public String getLocation() { return location; }
    public void setLocation(String l) { this.location = l; }
    public boolean isOrbitMode() { return orbitMode; }
    public void setOrbitMode(boolean o) { this.orbitMode = o; }
    public boolean isAutoFarmMode() { return autoFarmMode; }
    public void setAutoFarmMode(boolean a) { this.autoFarmMode = a; }
    public String getNickname() { return nickname; }
    public void setNickname(String n) { this.nickname = (n != null && n.isEmpty()) ? null : n; }
    public boolean hasNickname() { return nickname != null && !nickname.isEmpty(); }
    public EnderDragon getEntity() { return entity; }
    public void setEntity(EnderDragon e) { this.entity = e; }

    public boolean isRecovering() {
        return recoveryEndTime > 0 && System.currentTimeMillis() < recoveryEndTime;
    }

    public long getRecoveryRemainingMinutes() {
        if (!isRecovering()) return 0;
        return (recoveryEndTime - System.currentTimeMillis()) / 60000L;
    }

    public String getName(String evolutionSuffix, String typeDisplayName) {
        if (evolutionSuffix != null && !evolutionSuffix.isEmpty())
            return typeDisplayName + " (" + evolutionSuffix + ")";
        return typeDisplayName;
    }
}
