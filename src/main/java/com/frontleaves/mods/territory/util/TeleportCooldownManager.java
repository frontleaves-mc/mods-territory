package com.frontleaves.mods.territory.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportCooldownManager {

    private static final TeleportCooldownManager INSTANCE = new TeleportCooldownManager();
    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    private TeleportCooldownManager() {
    }

    public static TeleportCooldownManager getInstance() {
        return INSTANCE;
    }

    public boolean canTeleport(UUID playerUuid) {
        Long until = cooldownMap.get(playerUuid);
        if (until == null) return true;
        return System.currentTimeMillis() >= until;
    }

    public void setCooldown(UUID playerUuid, int seconds) {
        cooldownMap.put(playerUuid, System.currentTimeMillis() + seconds * 1000L);
    }

    public int getRemainingSeconds(UUID playerUuid) {
        Long until = cooldownMap.get(playerUuid);
        if (until == null) return 0;
        long remaining = until - System.currentTimeMillis();
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    public void clearCooldown(UUID playerUuid) {
        cooldownMap.remove(playerUuid);
    }
}
