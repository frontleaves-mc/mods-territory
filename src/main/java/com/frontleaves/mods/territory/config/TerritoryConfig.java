package com.frontleaves.mods.territory.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Territory mod client configuration.
 * <p>Auto-generates {@code config/territory-client.toml} on first run.</p>
 */
public final class TerritoryConfig {

    private TerritoryConfig() {
    }

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue MAX_RENDER_SIZE = BUILDER
            .comment("Maximum side length (blocks) for selection box rendering")
            .defineInRange("maxRenderSize", 128, 16, 512);
    public static final ModConfigSpec.IntValue SYNC_DISTANCE = BUILDER
            .comment("Radius (blocks) for syncing nearby territory boundaries")
            .defineInRange("syncDistance", 64, 16, 256);
    public static final ModConfigSpec SPEC = BUILDER.build();
}
