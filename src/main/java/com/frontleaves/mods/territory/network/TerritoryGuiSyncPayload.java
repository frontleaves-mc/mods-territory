package com.frontleaves.mods.territory.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * S→C 网络包：服务端向客户端同步领地 GUI 页面数据
 * <p>
 * 支持的页面类型 (pageType):
 * INFO, MEMBERS, FLAGS, SETTINGS, LOGS, ADMIN
 * <p>
 * pageData 值类型: String(0), Integer(1), Boolean(2)
 *
 * @param territoryUuid 领地 UUID
 * @param pageType      页面类型标识
 * @param pageData      页面渲染所需的键值对数据
 */
public record TerritoryGuiSyncPayload(
    String territoryUuid,
    String pageType,
    Map<String, Object> pageData
) implements CustomPacketPayload {

    public static final Type<TerritoryGuiSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("territory", "gui_sync")
    );

    public static final StreamCodec<FriendlyByteBuf, TerritoryGuiSyncPayload> STREAM_CODEC =
        StreamCodec.of(
            (FriendlyByteBuf buf, TerritoryGuiSyncPayload payload) -> payload.write(buf),
            TerritoryGuiSyncPayload::new
        );

    private TerritoryGuiSyncPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(), readPageData(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(territoryUuid);
        buf.writeUtf(pageType);
        writePageData(buf, pageData);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 读取混合类型键值对，值类型标记: 0=String, 1=Integer, 2=Boolean
     */
    private static Map<String, Object> readPageData(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Object> data = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            byte typeTag = buf.readByte();
            Object value = switch (typeTag) {
                case 0 -> buf.readUtf();
                case 1 -> buf.readInt();
                case 2 -> buf.readBoolean();
                default -> buf.readUtf();
            };
            data.put(key, value);
        }
        return data;
    }

    /**
     * 写入混合类型键值对
     */
    private static void writePageData(FriendlyByteBuf buf, Map<String, Object> data) {
        buf.writeVarInt(data.size());
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            buf.writeUtf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Integer intVal) {
                buf.writeByte(1);
                buf.writeInt(intVal);
            } else if (value instanceof Boolean boolVal) {
                buf.writeByte(2);
                buf.writeBoolean(boolVal);
            } else {
                buf.writeByte(0);
                buf.writeUtf(String.valueOf(value));
            }
        }
    }
}
