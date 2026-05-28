package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S 网络包：客户端使用领地杖设置传送点
 *
 * @param clickedPos  点击的位置坐标
 * @param isAdminWand 是否为管理员领地杖
 */
public record TerritoryWandSpawnSetPayload(BlockPos clickedPos, boolean isAdminWand) implements CustomPacketPayload {

    public static final Type<TerritoryWandSpawnSetPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "wand_spawn_set")
    );

    public static final StreamCodec<ByteBuf, TerritoryWandSpawnSetPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, TerritoryWandSpawnSetPayload::clickedPos,
        ByteBufCodecs.BOOL, TerritoryWandSpawnSetPayload::isAdminWand,
        TerritoryWandSpawnSetPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
