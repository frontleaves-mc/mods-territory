package com.frontleaves.mods.territory.storage;

import com.frontleaves.mods.territory.defense.TerritoryLogEntry;
import com.frontleaves.mods.territory.defense.TerritoryRole;
import com.frontleaves.mods.territory.geometry.AABB;
import com.frontleaves.mods.territory.network.TerritoryNearbySyncPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class TerritoryDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerritoryDataManager.class);
    private static TerritoryDataManager instance;

    private final Map<String, List<TerritoryData>> ownerMap = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path dataDir;

    private TerritoryDataManager() {
    }

    public static TerritoryDataManager getInstance() {
        if (instance == null) {
            instance = new TerritoryDataManager();
        }
        return instance;
    }

    public synchronized void initialize(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        ownerMap.clear();

        try (Stream<Path> files = Files.list(dataDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    String ownerUuid = p.getFileName().toString().replace(".json", "");
                    try {
                        String content = Files.readString(p);
                        Map<String, Object> root = gson.fromJson(content, Map.class);
                        if (root != null && root.get("territories") instanceof List<?> rawList) {
                            List<TerritoryData> territories = new ArrayList<>();
                            for (Object item : rawList) {
                                if (item instanceof Map<?, ?> map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> typedMap = (Map<String, Object>) map;
                                    territories.add(TerritoryData.fromMap(typedMap));
                                }
                            }
                            ownerMap.put(ownerUuid, territories);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to load territory data: {}", p, e);
                    }
                });
        }

        LOGGER.info("Loaded {} player territory files", ownerMap.size());
    }

    public synchronized void shutdown() {
        for (Map.Entry<String, List<TerritoryData>> entry : ownerMap.entrySet()) {
            writeOwnerFile(entry.getKey(), entry.getValue());
        }
        LOGGER.info("Territory data saved and shut down");
    }

    public synchronized void createTerritory(TerritoryData data) {
        String ownerUuid = data.ownerUuid();
        ownerMap.computeIfAbsent(ownerUuid, k -> new ArrayList<>()).add(data);
        writeOwnerFile(ownerUuid, ownerMap.get(ownerUuid));
    }

    public synchronized void updateTerritory(TerritoryData data) {
        String ownerUuid = data.ownerUuid();
        List<TerritoryData> territories = ownerMap.get(ownerUuid);
        if (territories != null) {
            for (int i = 0; i < territories.size(); i++) {
                if (territories.get(i).uuid().equals(data.uuid())) {
                    territories.set(i, data);
                    writeOwnerFile(ownerUuid, territories);
                    return;
                }
            }
        }
    }

    public synchronized boolean deleteTerritory(String ownerUuid, String territoryUuid) {
        List<TerritoryData> territories = ownerMap.get(ownerUuid);
        if (territories == null) return false;

        boolean removed = territories.removeIf(t -> t.uuid().equals(territoryUuid));
        if (removed) {
            if (territories.isEmpty()) {
                ownerMap.remove(ownerUuid);
                Path jsonFile = dataDir.resolve(ownerUuid + ".json");
                try {
                    Files.deleteIfExists(jsonFile);
                } catch (IOException e) {
                    LOGGER.error("Failed to delete territory file: {}", jsonFile, e);
                }
            } else {
                writeOwnerFile(ownerUuid, territories);
            }
        }
        return removed;
    }

    public synchronized List<TerritoryData> getTerritoriesByOwner(String ownerUuid) {
        return Collections.unmodifiableList(ownerMap.getOrDefault(ownerUuid, Collections.emptyList()));
    }

    public synchronized List<TerritoryData> getAllTerritories() {
        List<TerritoryData> all = new ArrayList<>();
        for (List<TerritoryData> territories : ownerMap.values()) {
            all.addAll(territories);
        }
        return all;
    }

    public synchronized Optional<TerritoryData> findTerritoryAt(String worldKey, int x, int y, int z) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.worldKey().equals(worldKey)) {
                    AABB box = new AABB(td.minX(), td.minY(), td.minZ(), td.maxX(), td.maxY(), td.maxZ());
                    if (box.contains(x, y, z)) {
                        return Optional.of(td);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public synchronized List<TerritoryData> findTerritoriesByOwner(String ownerUuid) {
        return new ArrayList<>(ownerMap.getOrDefault(ownerUuid, Collections.emptyList()));
    }

    public synchronized List<TerritoryData> findTerritoriesByMember(String playerUuid) {
        List<TerritoryData> result = new ArrayList<>();
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                for (TerritoryData.MemberEntry member : td.members()) {
                    if (member.playerUuid().equals(playerUuid) &&
                        (TerritoryRole.ADMIN.name().toLowerCase().equals(member.role()) ||
                         TerritoryRole.MEMBER.name().toLowerCase().equals(member.role()))) {
                        result.add(td);
                        break;
                    }
                }
            }
        }
        return result;
    }

    public synchronized void addLog(String territoryUuid, TerritoryLogEntry entry) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.uuid().equals(territoryUuid)) {
                    td.addLog(entry);
                    writeOwnerFile(td.ownerUuid(), ownerMap.get(td.ownerUuid()));
                    return;
                }
            }
        }
    }

    public synchronized List<TerritoryLogEntry> getLogs(String territoryUuid, int limit) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.uuid().equals(territoryUuid)) {
                    List<TerritoryLogEntry> logs = td.logs();
                    if (logs == null) return Collections.emptyList();
                    if (logs.size() <= limit) return new ArrayList<>(logs);
                    return new ArrayList<>(logs.subList(logs.size() - limit, logs.size()));
                }
            }
        }
        return Collections.emptyList();
    }

    public synchronized boolean addMember(String territoryUuid, TerritoryData.MemberEntry entry) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.uuid().equals(territoryUuid)) {
                    for (TerritoryData.MemberEntry existing : td.members()) {
                        if (existing.playerUuid().equals(entry.playerUuid())) {
                            return false;
                        }
                    }
                    td.members().add(entry);
                    writeOwnerFile(td.ownerUuid(), ownerMap.get(td.ownerUuid()));
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean removeMember(String territoryUuid, String playerUuid) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.uuid().equals(territoryUuid)) {
                    boolean removed = td.members().removeIf(m -> m.playerUuid().equals(playerUuid));
                    if (removed) {
                        writeOwnerFile(td.ownerUuid(), ownerMap.get(td.ownerUuid()));
                    }
                    return removed;
                }
            }
        }
        return false;
    }

    public synchronized boolean setMemberRole(String territoryUuid, String playerUuid, TerritoryRole role) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.uuid().equals(territoryUuid)) {
                    for (TerritoryData.MemberEntry member : td.members()) {
                        if (member.playerUuid().equals(playerUuid)) {
                            int idx = td.members().indexOf(member);
                            if (idx >= 0) {
                                String roleStr = role.name().toLowerCase();
                                TerritoryData.MemberEntry updated = new TerritoryData.MemberEntry(
                                    member.playerUuid(), roleStr, member.personalFlags()
                                );
                                td.members().set(idx, updated);
                                writeOwnerFile(td.ownerUuid(), ownerMap.get(td.ownerUuid()));
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 设置指定成员的个人权限覆盖。
     * <p>ADMIN/OWNER 可为任意成员设；玩家可为自己设。personalFlags 延迟初始化。
     *
     * @param territoryUuid 领地 UUID
     * @param memberUuid    成员 UUID
     * @param flag          权限标志名
     * @param value         权限值
     * @return 是否成功（领地/成员存在则 true）
     */
    public synchronized boolean setPersonalFlag(String territoryUuid, String memberUuid, String flag, boolean value) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.uuid().equals(territoryUuid)) {
                    for (TerritoryData.MemberEntry member : td.members()) {
                        if (member.playerUuid().equals(memberUuid)) {
                            member.setPersonalFlag(flag, value);
                            writeOwnerFile(td.ownerUuid(), ownerMap.get(td.ownerUuid()));
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public synchronized Optional<TerritoryData> findTerritoryByUuid(String uuid) {
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.uuid().equals(uuid)) {
                    return Optional.of(td);
                }
            }
        }
        return Optional.empty();
    }

    public static net.minecraft.world.phys.Vec3 calculateFallbackSpawn(TerritoryData data, net.minecraft.server.level.ServerLevel level) {
        double centerX = (data.minX() + data.maxX()) / 2.0 + 0.5;
        double centerZ = (data.minZ() + data.maxZ()) / 2.0 + 0.5;
        
        for (int y = data.maxY(); y >= data.minY(); y--) {
            var pos = new net.minecraft.core.BlockPos((int) centerX, y, (int) centerZ);
            var below = pos.below();
            if (level.getBlockState(below).isSolid() && 
                level.getBlockState(pos).isAir() && 
                level.getBlockState(pos.above()).isAir()) {
                return new net.minecraft.world.phys.Vec3(centerX, y, centerZ);
            }
        }
        
        return new net.minecraft.world.phys.Vec3(centerX, level.getSeaLevel(), centerZ);
    }

    public static long calculateArea(TerritoryData data) {
        return (long) (data.maxX() - data.minX() + 1) * (data.maxZ() - data.minZ() + 1);
    }

    public synchronized boolean checkOverlap(String worldKey, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        AABB newBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (td.worldKey().equals(worldKey)) {
                    AABB existing = new AABB(td.minX(), td.minY(), td.minZ(), td.maxX(), td.maxY(), td.maxZ());
                    if (existing.intersects(newBox)) return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取指定世界坐标附近的所有领地边界信息。
     * 使用 AABB 与搜索区域矩形（centerX±radius, centerZ±radius）的重叠检测。
     *
     * @param worldKey   世界维度标识
     * @param centerX    搜索中心 X
     * @param centerZ    搜索中心 Z
     * @param radius     搜索半径
     * @param playerUuid 当前玩家 UUID，用于判断领地是否属于该玩家
     * @return 附近领地边界列表
     */
    public synchronized List<TerritoryNearbySyncPayload.TerritoryBoundary> getTerritoriesNearby(
            String worldKey, int centerX, int centerZ, int radius,
            String playerUuid, boolean isAdminWand, net.minecraft.server.MinecraftServer server) {
        List<TerritoryNearbySyncPayload.TerritoryBoundary> result = new ArrayList<>();
        int searchMinX = centerX - radius;
        int searchMaxX = centerX + radius;
        int searchMinZ = centerZ - radius;
        int searchMaxZ = centerZ + radius;

        for (List<TerritoryData> territories : ownerMap.values()) {
            for (TerritoryData td : territories) {
                if (!td.worldKey().equals(worldKey)) continue;

                // AABB 与搜索区域的重叠检测
                if (td.maxX() < searchMinX || td.minX() > searchMaxX) continue;
                if (td.maxZ() < searchMinZ || td.minZ() > searchMaxZ) continue;

                byte colorType;
                if (td.ownerUuid().equals(playerUuid)) {
                    colorType = 1; // 自己的领地
                } else if (isAdminWand && td.admin()) {
                    colorType = 2; // 他人管理员领地
                } else {
                    colorType = 0; // 普通他人领地
                }

                result.add(new TerritoryNearbySyncPayload.TerritoryBoundary(
                    td.minX(), td.minY(), td.minZ(),
                    td.maxX(), td.maxY(), td.maxZ(),
                    colorType,
                    com.frontleaves.mods.territory.defense.PlayerNameResolver.resolveName(server, td.ownerUuid())
                ));
            }
        }
        return result;
    }

    private void writeOwnerFile(String ownerUuid, List<TerritoryData> territories) {
        Path jsonFile = dataDir.resolve(ownerUuid + ".json");
        List<Map<String, Object>> list = new ArrayList<>();
        for (TerritoryData td : territories) {
            list.add(td.toMap());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("territories", list);

        try {
            Path tmpFile = jsonFile.resolveSibling(jsonFile.getFileName() + ".tmp");
            Files.writeString(tmpFile, gson.toJson(root));
            Files.move(tmpFile, jsonFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to write territory file: {}", jsonFile, e);
        }
    }
}
