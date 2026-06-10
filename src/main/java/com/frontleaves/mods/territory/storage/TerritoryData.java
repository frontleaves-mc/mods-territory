package com.frontleaves.mods.territory.storage;

import java.time.Instant;
import java.util.*;

public class TerritoryData {
    private String uuid;
    private String name;
    private String ownerUuid;
    private String worldKey;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private String createdAt;
    private List<MemberEntry> members;
    private Map<String, String> flags;
    private boolean admin;
    private Double spawnX;
    private Double spawnY;
    private Double spawnZ;

    public TerritoryData() {
    }

    public static TerritoryData create(String ownerUuid, String name, String worldKey,
                                       int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                       boolean admin) {
        TerritoryData data = new TerritoryData();
        data.uuid = UUID.randomUUID().toString();
        data.name = name;
        data.ownerUuid = ownerUuid;
        data.worldKey = worldKey;
        data.minX = minX;
        data.minY = minY;
        data.minZ = minZ;
        data.maxX = maxX;
        data.maxY = maxY;
        data.maxZ = maxZ;
        data.createdAt = Instant.now().toString();
        data.members = new ArrayList<>();
        data.flags = new HashMap<>();
        data.flags.put("block.break", "deny");
        data.flags.put("block.place", "deny");
        data.flags.put("block.interact", "deny");
        data.flags.put("entity.damage", "deny");
        data.flags.put("entity.interact", "allow");
        data.admin = admin;
        return data;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uuid", uuid);
        map.put("name", name);
        map.put("ownerUuid", ownerUuid);
        map.put("worldKey", worldKey);
        map.put("minX", minX);
        map.put("minY", minY);
        map.put("minZ", minZ);
        map.put("maxX", maxX);
        map.put("maxY", maxY);
        map.put("maxZ", maxZ);
        map.put("createdAt", createdAt);

        List<Map<String, String>> membersList = new ArrayList<>();
        for (MemberEntry member : members) {
            Map<String, String> memberMap = new HashMap<>();
            memberMap.put("playerUuid", member.playerUuid());
            memberMap.put("role", member.role());
            membersList.add(memberMap);
        }
        map.put("members", membersList);
        if (this.hasSpawn()) {
            map.put("spawnX", spawnX);
            map.put("spawnY", spawnY);
            map.put("spawnZ", spawnZ);
        }
        map.put("admin", admin);
        map.put("flags", flags);

        return map;
    }

    public static TerritoryData fromMap(Map<String, Object> map) {
        TerritoryData data = new TerritoryData();
        data.uuid = getString(map, "uuid");
        data.name = getString(map, "name");
        data.ownerUuid = getString(map, "ownerUuid");
        data.worldKey = getString(map, "worldKey");
        data.minX = getInt(map, "minX");
        data.minY = getInt(map, "minY");
        data.minZ = getInt(map, "minZ");
        data.maxX = getInt(map, "maxX");
        data.maxY = getInt(map, "maxY");
        data.maxZ = getInt(map, "maxZ");
        data.createdAt = getString(map, "createdAt");
        data.spawnX = getDoubleOrNull(map, "spawnX");
        data.spawnY = getDoubleOrNull(map, "spawnY");
        data.spawnZ = getDoubleOrNull(map, "spawnZ");

        data.members = new ArrayList<>();
        Object membersObj = map.get("members");
        if (membersObj instanceof List) {
            List<?> membersList = (List<?>) membersObj;
            for (Object memberObj : membersList) {
                if (memberObj instanceof Map) {
                    Map<?, ?> memberMap = (Map<?, ?>) memberObj;
                    String playerUuid = memberMap.get("playerUuid") != null ? memberMap.get("playerUuid").toString() : null;
                    String role = memberMap.get("role") != null ? memberMap.get("role").toString() : null;
                    data.members.add(new MemberEntry(playerUuid, role));
                }
            }
        }

        data.admin = getBool(map, "admin", false);

        data.flags = new HashMap<>();
        Object flagsObj = map.get("flags");
        if (flagsObj instanceof Map) {
            Map<?, ?> flagsMap = (Map<?, ?>) flagsObj;
            for (Map.Entry<?, ?> entry : flagsMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    data.flags.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
        }

        return data;
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static Double getDoubleOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    public static class MemberEntry {
        private final String playerUuid;
        private final String role;

        public MemberEntry(String playerUuid, String role) {
            this.playerUuid = playerUuid;
            this.role = role;
        }

        public String playerUuid() {
            return playerUuid;
        }

        public String role() {
            return role;
        }
    }

    public String uuid() { return uuid; }
    public void uuid(String uuid) { this.uuid = uuid; }

    public String name() { return name; }
    public void name(String name) { this.name = name; }

    public String ownerUuid() { return ownerUuid; }
    public void ownerUuid(String ownerUuid) { this.ownerUuid = ownerUuid; }

    public String worldKey() { return worldKey; }
    public void worldKey(String worldKey) { this.worldKey = worldKey; }

    public int minX() { return minX; }
    public void minX(int minX) { this.minX = minX; }

    public int minY() { return minY; }
    public void minY(int minY) { this.minY = minY; }

    public int minZ() { return minZ; }
    public void minZ(int minZ) { this.minZ = minZ; }

    public int maxX() { return maxX; }
    public void maxX(int maxX) { this.maxX = maxX; }

    public int maxY() { return maxY; }
    public void maxY(int maxY) { this.maxY = maxY; }

    public int maxZ() { return maxZ; }
    public void maxZ(int maxZ) { this.maxZ = maxZ; }

    public String createdAt() { return createdAt; }
    public void createdAt(String createdAt) { this.createdAt = createdAt; }

    public List<MemberEntry> members() { return members; }
    public void members(List<MemberEntry> members) { this.members = members; }

    public Map<String, String> flags() { return flags; }
    public void flags(Map<String, String> flags) { this.flags = flags; }

    public boolean hasSpawn() {
        return spawnX != null && spawnY != null && spawnZ != null;
    }

    public Double spawnX() { return spawnX; }
    public void spawnX(Double spawnX) { this.spawnX = spawnX; }

    public Double spawnY() { return spawnY; }
    public void spawnY(Double spawnY) { this.spawnY = spawnY; }

    public Double spawnZ() { return spawnZ; }
    public void spawnZ(Double spawnZ) { this.spawnZ = spawnZ; }

    public boolean admin() { return admin; }
    public void admin(boolean admin) { this.admin = admin; }
}