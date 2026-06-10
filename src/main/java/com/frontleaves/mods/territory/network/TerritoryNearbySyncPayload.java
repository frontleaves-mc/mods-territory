package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S→C 网络包：服务端向客户端同步附近领地边界数据
 *
 * <p>当玩家手持领地法杖并更新选区时，服务端计算附近的领地边界，
 * 将其以轻量级形式推送至客户端用于渲染半透明线框。</p>
 *
 * @param boundaries 附近领地边界列表
 */
public record TerritoryNearbySyncPayload(List<TerritoryBoundary> boundaries) implements CustomPacketPayload {

    public static final Type<TerritoryNearbySyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "nearby_sync")
    );

    public static final StreamCodec<ByteBuf, TerritoryBoundary> BOUNDARY_CODEC = new StreamCodec<>() {
        @Override
        public TerritoryBoundary decode(ByteBuf buf) {
            int minX = ByteBufCodecs.VAR_INT.decode(buf);
            int minY = ByteBufCodecs.VAR_INT.decode(buf);
            int minZ = ByteBufCodecs.VAR_INT.decode(buf);
            int maxX = ByteBufCodecs.VAR_INT.decode(buf);
            int maxY = ByteBufCodecs.VAR_INT.decode(buf);
            int maxZ = ByteBufCodecs.VAR_INT.decode(buf);
            byte colorType = ByteBufCodecs.BYTE.decode(buf);
            String ownerName = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new TerritoryBoundary(minX, minY, minZ, maxX, maxY, maxZ, colorType, ownerName);
        }

        @Override
        public void encode(ByteBuf buf, TerritoryBoundary value) {
            ByteBufCodecs.VAR_INT.encode(buf, value.minX());
            ByteBufCodecs.VAR_INT.encode(buf, value.minY());
            ByteBufCodecs.VAR_INT.encode(buf, value.minZ());
            ByteBufCodecs.VAR_INT.encode(buf, value.maxX());
            ByteBufCodecs.VAR_INT.encode(buf, value.maxY());
            ByteBufCodecs.VAR_INT.encode(buf, value.maxZ());
            ByteBufCodecs.BYTE.encode(buf, value.colorType());
            ByteBufCodecs.STRING_UTF8.encode(buf, value.ownerName());
        }
    };

    public static final StreamCodec<ByteBuf, TerritoryNearbySyncPayload> STREAM_CODEC = StreamCodec.composite(
        BOUNDARY_CODEC.apply(ByteBufCodecs.list()), TerritoryNearbySyncPayload::boundaries,
        TerritoryNearbySyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 领地边界轻量级数据
     *
     * @param minX      最小 X 坐标
     * @param minY      最小 Y 坐标
     * @param minZ      最小 Z 坐标
     * @param maxX      最大 X 坐标
     * @param maxY      最大 Y 坐标
     * @param maxZ      最大 Z 坐标
     * @param colorType 颜色类型：0=普通他人, 1=自己的领地, 2=他人管理员
     * @param ownerName 领地所有者名称
     */
    public record TerritoryBoundary(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, byte colorType, String ownerName) {
    }
}
