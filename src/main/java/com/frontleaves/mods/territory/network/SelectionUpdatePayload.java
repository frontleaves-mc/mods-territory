package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S 网络包：客户端向服务端发送领地选区的两个角坐标与维度信息
 *
 * @param pos1         第一个角的位置
 * @param pos2         第二个角的位置
 * @param dimensionKey 维度标识（如 "minecraft:overworld"）
 * @param admin        是否为管理员选区（跳过体积限制校验）
 */
public record SelectionUpdatePayload(BlockPos pos1, BlockPos pos2, String dimensionKey, boolean admin) implements CustomPacketPayload {

    public static final Type<SelectionUpdatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "selection_update")
    );

    public static final StreamCodec<ByteBuf, SelectionUpdatePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SelectionUpdatePayload::pos1,
        BlockPos.STREAM_CODEC, SelectionUpdatePayload::pos2,
        ByteBufCodecs.STRING_UTF8, SelectionUpdatePayload::dimensionKey,
        ByteBufCodecs.BOOL, SelectionUpdatePayload::admin,
        SelectionUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
