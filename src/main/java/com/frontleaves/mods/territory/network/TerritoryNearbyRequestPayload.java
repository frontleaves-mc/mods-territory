package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S 网络包：客户端请求查询附近的领地
 *
 * @param dimensionKey 维度标识（如 "minecraft:overworld"）
 * @param isAdminWand  是否手持管理员法杖
 */
public record TerritoryNearbyRequestPayload(String dimensionKey, boolean isAdminWand) implements CustomPacketPayload {

    public static final Type<TerritoryNearbyRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "nearby_request")
    );

    public static final StreamCodec<ByteBuf, TerritoryNearbyRequestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TerritoryNearbyRequestPayload::dimensionKey,
        ByteBufCodecs.BOOL, TerritoryNearbyRequestPayload::isAdminWand,
        TerritoryNearbyRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
