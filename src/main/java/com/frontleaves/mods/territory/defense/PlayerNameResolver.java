package com.frontleaves.mods.territory.defense;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 玩家名解析工具：将玩家 UUID 解析为可读名称。
 * <p>优先返回在线玩家名；离线时查询 profile 缓存；均失败则回退 UUID 字符串。
 * 被领地台 GUI 同步、附近边界渲染、成员列表等多处共用。
 *
 * @author 筱锋
 */
public final class PlayerNameResolver {

    private PlayerNameResolver() {}

    /**
     * 解析 UUID 字符串为玩家名。
     * <p>解析优先级：在线玩家名 {@code >} profile 缓存 {@code >} 原始 UUID 字符串。
     *
     * @param server  Minecraft 服务端实例
     * @param uuidStr UUID 字符串；若非法则原样返回
     * @return 玩家名（在线/缓存），解析失败返回入参原文
     */
    public static String resolveName(MinecraftServer server, String uuidStr) {
        if (server == null || uuidStr == null) return uuidStr;
        try {
            UUID uuid = UUID.fromString(uuidStr);
            var online = server.getPlayerList().getPlayer(uuid);
            if (online != null) return online.getName().getString();
            return server.getProfileCache()
                .get(uuid)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(uuidStr);
        } catch (IllegalArgumentException e) {
            // 非 UUID 字符串 — 原样返回
            return uuidStr;
        }
    }
}
