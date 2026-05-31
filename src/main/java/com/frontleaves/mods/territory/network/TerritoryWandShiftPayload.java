package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S 网络包：客户端 Shift+右键领地杖时发送，由服务端判断领地内外行为
 *
 * @param clickedPos  点击的位置坐标
 * @param isAdminWand 是否为管理员领地杖
 */
public record TerritoryWandShiftPayload(BlockPos clickedPos, boolean isAdminWand) implements CustomPacketPayload {

    public static final Type<TerritoryWandShiftPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "wand_shift")
    );

    public static final StreamCodec<ByteBuf, TerritoryWandShiftPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, TerritoryWandShiftPayload::clickedPos,
        ByteBufCodecs.BOOL, TerritoryWandShiftPayload::isAdminWand,
        TerritoryWandShiftPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
