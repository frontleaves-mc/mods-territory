package com.frontleaves.mods.territory.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Territory mod 配置管理。
 *
 * <p>将配置拆分为 CLIENT（客户端渲染）和 COMMON（服务端逻辑）两份独立 Spec，
 * 确保 SERVER 端网络包处理器不会因 CLIENT 配置未加载而崩溃。</p>
 *
 * <ul>
 *   <li>{@code territory-client.toml} — {@link #CLIENT_SPEC}（仅客户端）</li>
 *   <li>{@code territory-common.toml} — {@link #COMMON_SPEC}（双端共享）</li>
 * </ul>
 */
public final class TerritoryConfig {

    private TerritoryConfig() {
    }

    // -- Client-side config (rendering) --

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_RENDER_SIZE = CLIENT_BUILDER
            .comment("Maximum side length (blocks) for selection box rendering")
            .defineInRange("maxRenderSize", 128, 16, 512);

    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    // -- Common config (server-side logic) --

    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SYNC_DISTANCE = COMMON_BUILDER
            .comment("Radius (blocks) for syncing nearby territory boundaries")
            .defineInRange("syncDistance", 64, 16, 256);

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();
}
