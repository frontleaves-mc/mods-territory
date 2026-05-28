package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S 网络包：客户端请求打开领地手册界面
 */
public record TerritoryBookOpenPayload() implements CustomPacketPayload {

    public static final Type<TerritoryBookOpenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "book_open")
    );

    public static final StreamCodec<ByteBuf, TerritoryBookOpenPayload> STREAM_CODEC =
        StreamCodec.unit(new TerritoryBookOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
