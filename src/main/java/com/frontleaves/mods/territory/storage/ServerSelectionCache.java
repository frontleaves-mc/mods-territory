package com.frontleaves.mods.territory.storage;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端选区缓存 — 存储玩家通过选区法杖确认的领地范围。
 */
public class ServerSelectionCache {

    private static final Map<UUID, SelectionEntry> CACHE = new ConcurrentHashMap<>();

    public record SelectionEntry(BlockPos pos1, BlockPos pos2, String dimensionKey, boolean validated) {
    }

    public static void put(UUID playerUuid, BlockPos pos1, BlockPos pos2, String dimensionKey, boolean validated) {
        CACHE.put(playerUuid, new SelectionEntry(pos1, pos2, dimensionKey, validated));
    }

    @Nullable
    public static SelectionEntry get(UUID playerUuid) {
        return CACHE.get(playerUuid);
    }

    public static void remove(UUID playerUuid) {
        CACHE.remove(playerUuid);
    }

    public static void clear() {
        CACHE.clear();
    }
}
