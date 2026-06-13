package com.frontleaves.mods.territory.defense;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 领地操作日志条目。
 * <p>
 * 记录一次与领地相关的操作，包含操作者 UUID、操作类型、时间戳和详情描述。
 * 提供 {@link #toMap()} 和 {@link #fromMap(Map)} 用于序列化与反序列化。
 */
public class TerritoryLogEntry {

    private final String playerUuid;
    private final String action;
    private final Instant timestamp;
    private final String detail;

    public TerritoryLogEntry(String playerUuid, String action, Instant timestamp, String detail) {
        this.playerUuid = playerUuid;
        this.action = action;
        this.timestamp = timestamp;
        this.detail = detail;
    }

    public String playerUuid() {
        return playerUuid;
    }

    public String action() {
        return action;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String detail() {
        return detail;
    }

    /**
     * 将日志条目序列化为 Map。
     *
     * @return 包含所有字段的可序列化映射
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerUuid", playerUuid);
        map.put("action", action);
        map.put("timestamp", timestamp.toString());
        map.put("detail", detail);
        return map;
    }

    /**
     * 从 Map 反序列化为日志条目。
     *
     * @param map 数据源映射
     * @return 日志条目实例
     */
    public static TerritoryLogEntry fromMap(Map<String, Object> map) {
        String playerUuid = (String) map.get("playerUuid");
        String action = (String) map.get("action");
        Instant timestamp = Instant.parse((String) map.get("timestamp"));
        String detail = (String) map.get("detail");
        return new TerritoryLogEntry(playerUuid, action, timestamp, detail);
    }
}
