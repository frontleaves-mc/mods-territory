package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S→C 网络包：领地操作日志同步。
 * <p>替代原 {@link TerritoryGuiSyncPayload} 中无法序列化的 {@code List<Map>} 日志数据。
 * 服务端在切换到 LOGS 页时发送，客户端 Screen 缓存后渲染。
 *
 * @param territoryUuid 领地 UUID
 * @param logs          操作日志列表（含已解析的操作者玩家名）
 * @author 筱锋
 */
public record TerritoryLogsSyncPayload(
    String territoryUuid,
    List<LogInfo> logs
) implements CustomPacketPayload {

    public static final Type<TerritoryLogsSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "logs_sync")
    );

    public static final StreamCodec<ByteBuf, LogInfo> LOG_CODEC = StreamCodec.of(
        (buf, l) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, l.playerName());
            ByteBufCodecs.STRING_UTF8.encode(buf, l.action());
            ByteBufCodecs.STRING_UTF8.encode(buf, l.timestamp());
            ByteBufCodecs.STRING_UTF8.encode(buf, l.detail());
        },
        buf -> new LogInfo(
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf)
        )
    );

    public static final StreamCodec<ByteBuf, TerritoryLogsSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TerritoryLogsSyncPayload::territoryUuid,
        LOG_CODEC.apply(ByteBufCodecs.list()), TerritoryLogsSyncPayload::logs,
        TerritoryLogsSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /**
     * 单条操作日志。
     *
     * @param playerName 操作者玩家名（已解析）
     * @param action     动作类型（如 SET_FLAG / ADD_MEMBER）
     * @param timestamp  ISO-8601 时间戳字符串
     * @param detail     操作详情
     */
    public record LogInfo(String playerName, String action, String timestamp, String detail) {}
}
