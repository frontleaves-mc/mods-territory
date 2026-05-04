package com.frontleaves.mods.territory.storage;

import com.frontleaves.mods.territory.geometry.AABB;
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
