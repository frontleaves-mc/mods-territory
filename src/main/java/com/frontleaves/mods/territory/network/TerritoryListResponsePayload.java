package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S→C 网络包：服务端向客户端发送领地列表数据
 *
 * @param owned  玩家拥有的领地列表
 * @param shared 玩家被共享的领地列表
 */
public record TerritoryListResponsePayload(List<TerritoryEntry> owned, List<TerritoryEntry> shared) implements CustomPacketPayload {

    public static final Type<TerritoryListResponsePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "list_response")
    );

    public static final StreamCodec<ByteBuf, TerritoryEntry> ENTRY_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TerritoryEntry::uuid,
        ByteBufCodecs.STRING_UTF8, TerritoryEntry::name,
        ByteBufCodecs.STRING_UTF8, TerritoryEntry::worldKey,
        ByteBufCodecs.VAR_LONG, TerritoryEntry::area,
        ByteBufCodecs.BOOL, TerritoryEntry::hasSpawn,
        TerritoryEntry::new
    );

    public static final StreamCodec<ByteBuf, TerritoryListResponsePayload> STREAM_CODEC = StreamCodec.composite(
        ENTRY_CODEC.apply(ByteBufCodecs.list()), TerritoryListResponsePayload::owned,
        ENTRY_CODEC.apply(ByteBufCodecs.list()), TerritoryListResponsePayload::shared,
        TerritoryListResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 领地条目数据
     *
     * @param uuid     领地唯一标识
     * @param name     领地名称
     * @param worldKey 世界维度标识
     * @param area     领地面积
     * @param hasSpawn 是否设置了传送点
     */
    public record TerritoryEntry(String uuid, String name, String worldKey, long area, boolean hasSpawn) {
    }
}
