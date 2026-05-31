package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C 网络包：通知客户端清除选区状态
 */
public record SelectionClearPayload() implements CustomPacketPayload {

    public static final Type<SelectionClearPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "selection_clear")
    );

    public static final StreamCodec<ByteBuf, SelectionClearPayload> STREAM_CODEC =
        StreamCodec.unit(new SelectionClearPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
