package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S 网络包：客户端请求传送到指定领地
 *
 * @param territoryUuid 目标领地 UUID
 */
public record TerritoryTeleportRequestPayload(String territoryUuid) implements CustomPacketPayload {

    public static final Type<TerritoryTeleportRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "teleport_request")
    );

    public static final StreamCodec<ByteBuf, TerritoryTeleportRequestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TerritoryTeleportRequestPayload::territoryUuid,
        TerritoryTeleportRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
