package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S→C 网络包：服务端向客户端发送选区验证结果
 *
 * @param valid   选区是否通过验证
 * @param message 验证结果翻译键
 * @param args    翻译参数（用于格式化占位符）
 */
public record SelectionResponsePayload(boolean valid, String message, List<String> args) implements CustomPacketPayload {

    public static final Type<SelectionResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "selection_response")
    );

    public static final StreamCodec<ByteBuf, SelectionResponsePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SelectionResponsePayload::valid,
        ByteBufCodecs.STRING_UTF8, SelectionResponsePayload::message,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SelectionResponsePayload::args,
        SelectionResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
