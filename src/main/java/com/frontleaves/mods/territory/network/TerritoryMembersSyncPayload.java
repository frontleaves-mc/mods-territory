package com.frontleaves.mods.territory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S→C 网络包：领地成员列表同步。
 * <p>替代原 {@link TerritoryGuiSyncPayload} 中无法序列化的 {@code List<Map>} 成员数据。
 * 服务端在切换到 MEMBERS 页时发送，客户端 Screen 缓存后渲染。
 *
 * @param territoryUuid 领地 UUID
 * @param members       成员信息列表（含已解析的玩家名）
 * @param ownerUuid     领主 UUID（用于客户端标记 owner 行）
 * @param canEdit       当前玩家是否有写权限（ADMIN/OWNER）
 * @author 筱锋
 */
public record TerritoryMembersSyncPayload(
    String territoryUuid,
    List<MemberInfo> members,
    String ownerUuid,
    boolean canEdit
) implements CustomPacketPayload {

    public static final Type<TerritoryMembersSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "members_sync")
    );

    public static final StreamCodec<ByteBuf, MemberInfo> MEMBER_CODEC = StreamCodec.of(
        (buf, m) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, m.playerUuid());
            ByteBufCodecs.STRING_UTF8.encode(buf, m.playerName());
            ByteBufCodecs.STRING_UTF8.encode(buf, m.role());
            ByteBufCodecs.BOOL.encode(buf, m.isOwner());
        },
        buf -> new MemberInfo(
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.BOOL.decode(buf)
        )
    );

    public static final StreamCodec<ByteBuf, TerritoryMembersSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TerritoryMembersSyncPayload::territoryUuid,
        MEMBER_CODEC.apply(ByteBufCodecs.list()), TerritoryMembersSyncPayload::members,
        ByteBufCodecs.STRING_UTF8, TerritoryMembersSyncPayload::ownerUuid,
        ByteBufCodecs.BOOL, TerritoryMembersSyncPayload::canEdit,
        TerritoryMembersSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /**
     * 单个成员信息。
     *
     * @param playerUuid 成员 UUID
     * @param playerName 已解析的玩家名（在线/缓存，回退 UUID）
     * @param role       角色名（visitor/member/admin）
     * @param isOwner    是否为领主（用于客户端高亮标记）
     */
    public record MemberInfo(String playerUuid, String playerName, String role, boolean isOwner) {}
}
