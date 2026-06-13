package com.frontleaves.mods.territory.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * C→S 网络包：客户端 GUI 操作请求发送至服务端处理
 * <p>
 * 支持的操作类型 (actionType):
 * SET_FLAG, ADD_MEMBER, REMOVE_MEMBER, SET_ROLE, SET_PERSONAL_FLAG, RENAME, DELETE, TRANSFER
 *
 * @param territoryUuid 目标领地 UUID
 * @param actionType    操作类型标识
 * @param targetData    操作附带的目标数据键值对
 */
public record TerritoryGuiActionPayload(
    String territoryUuid,
    String actionType,
    Map<String, String> targetData
) implements CustomPacketPayload {

    public static final Type<TerritoryGuiActionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "gui_action")
    );

    public static final StreamCodec<FriendlyByteBuf, TerritoryGuiActionPayload> STREAM_CODEC =
        StreamCodec.of(
            (FriendlyByteBuf buf, TerritoryGuiActionPayload payload) -> payload.write(buf),
            TerritoryGuiActionPayload::new
        );

    private TerritoryGuiActionPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(), readTargetData(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(territoryUuid);
        buf.writeUtf(actionType);
        writeTargetData(buf, targetData);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static Map<String, String> readTargetData(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, String> data = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            data.put(buf.readUtf(), buf.readUtf());
        }
        return data;
    }

    private static void writeTargetData(FriendlyByteBuf buf, Map<String, String> data) {
        buf.writeVarInt(data.size());
        for (Map.Entry<String, String> entry : data.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }
}
