package com.frontleaves.mods.territory.gui;

import com.frontleaves.mods.territory.defense.FlagCategory;
import com.frontleaves.mods.territory.defense.FlagType;
import com.frontleaves.mods.territory.defense.TerritoryLogEntry;
import com.frontleaves.mods.territory.defense.TerritoryPermissionService;
import com.frontleaves.mods.territory.defense.TerritoryRole;
import com.frontleaves.mods.territory.network.TerritoryGuiActionPayload;
import com.frontleaves.mods.territory.network.TerritoryGuiSyncPayload;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 领地管理桌 GUI — 服务端 Container Menu。
 * <p>
 * 管理 6 页面（INFO / MEMBERS / FLAGS / SETTINGS / LOGS / ADMIN）的数据同步与操作处理，
 * 接收客户端 {@link TerritoryGuiActionPayload} 并通过 {@link TerritoryGuiSyncPayload} 向客户端推送页面数据。
 * <p>
 * 角色可见性规则：
 * <ul>
 *   <li>{@code OWNER} — 全部页面可读写</li>
 *   <li>{@code ADMIN} — 除 ADMIN 页外可读写</li>
 *   <li>{@code MEMBER} — 仅 INFO/MEMBERS/FLAGS 可读</li>
 *   <li>{@code VISITOR} — 仅 INFO 可读</li>
 * </ul>
 *
 * @see TerritoryTableScreen 客户端渲染
 */
public class TerritoryTableMenu extends AbstractContainerMenu {

    // ===== 页面常量 =====
    public static final int PAGE_INFO = 0;
    public static final int PAGE_MEMBERS = 1;
    public static final int PAGE_FLAGS = 2;
    public static final int PAGE_SETTINGS = 3;
    public static final int PAGE_LOGS = 4;
    public static final int PAGE_ADMIN = 5;

    /** 最大日志条数（避免网络包过大） */
    private static final int MAX_LOG_ENTRIES = 50;

    // ===== 状态字段 =====
    private final TerritoryData territory;
    private final TerritoryRole playerRole;
    private final ServerPlayer serverPlayer;
    private int currentPage = PAGE_INFO;

    /**
     * MenuType 工厂构造器：由 MenuType 注册时的 lambda 调用。
     * <p>客户端侧占位，实际数据由 S→C sync payload 填充。
     */
    public TerritoryTableMenu(int containerId, Inventory playerInventory) {
        super(getMenuType(), containerId);
        this.territory = null;
        this.serverPlayer = null;
        this.playerRole = TerritoryRole.VISITOR;
    }

    /**
     * 客户端构造器：从网络缓冲区读取领地 UUID。
     * <p>实际数据由 S→C 同步包填充，此处仅占位。
     */
    public TerritoryTableMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        super(getMenuType(), containerId);
        this.territory = null;      // 由 sync payload 填充
        this.serverPlayer = null;   // 客户端无服务端引用
        this.playerRole = TerritoryRole.VISITOR;
        buffer.readUtf(); // territoryUuid，保留读取以维持 buf 位置
    }

    /**
     * 服务端构造器：绑定完整领地数据与玩家角色。
     *
     * @param containerId   容器 ID
     * @param playerInventory 玩家背包
     * @param territory      目标领地数据
     * @param serverPlayer   操作玩家（用于权限判定 & 日志记录）
     */
    public TerritoryTableMenu(int containerId, Inventory playerInventory,
                              TerritoryData territory, ServerPlayer serverPlayer) {
        super(getMenuType(), containerId);
        this.territory = territory;
        this.serverPlayer = serverPlayer;
        this.playerRole = TerritoryPermissionService.getPlayerRole(serverPlayer, territory);
    }

    // ================================================================
    //  MenuType — 由 Territory.registerScreens() 注入
    // ================================================================

    private static MenuType<TerritoryTableMenu> menuType;

    public static void setMenuType(MenuType<TerritoryTableMenu> type) {
        menuType = type;
    }

    public static MenuType<TerritoryTableMenu> getMenuType() {
        return menuType;
    }

    // ================================================================
    //  页面导航
    // ================================================================

    /**
     * 切换到指定页面并立即向客户端同步数据。
     *
     * @param page 目标页面索引（0–5）
     */
    public void setPage(int page) {
        if (page < 0 || page > PAGE_ADMIN) return;
        this.currentPage = page;
        this.syncPageData();
    }

    /** 获取当前页面索引。 */
    public int getCurrentPage() { return currentPage; }

    /** 获取绑定的领地数据（可能为 null — 仅服务端有效）。 */
    public TerritoryData getTerritory() { return territory; }

    /** 获取当前玩家在此领地中的角色。 */
    public TerritoryRole getPlayerRole() { return playerRole; }

    // ================================================================
    //  操作处理入口 — 由网络 handler 调用
    // ================================================================

    /**
     * 处理来自客户端的 GUI 操作请求。
     * <p>所有写操作均会触发日志记录与数据同步。
     *
     * @param payload C→S 操作载荷
     */
    public void handleAction(TerritoryGuiActionPayload payload) {
        if (territory == null || serverPlayer == null) return;

        String action = payload.actionType();
        var manager = TerritoryDataManager.getInstance();

        switch (action) {
            case "SET_FLAG"       -> handleSetFlag(payload, manager);
            case "ADD_MEMBER"     -> handleAddMember(payload, manager);
            case "REMOVE_MEMBER"  -> handleRemoveMember(payload, manager);
            case "SET_ROLE"       -> handleSetRole(payload, manager);
            case "RENAME"         -> handleRename(payload, manager);
            case "DELETE"         -> handleDelete(manager);
            case "TRANSFER"       -> handleTransfer(payload, manager);
            default -> {
                // 未知操作类型 — 忽略
            }
        }
    }

    // ----- SET_FLAG -----
    private void handleSetFlag(TerritoryGuiActionPayload payload, TerritoryDataManager manager) {
        if (!canWrite()) return;
        String flagName = payload.targetData().get("flag");
        String valueStr = payload.targetData().get("value");
        if (flagName == null || valueStr == null) return;

        boolean newValue = Boolean.parseBoolean(valueStr);
        territory.flags().put(flagName, newValue);
        manager.updateTerritory(territory);

        logAction(manager, "SET_FLAG", flagName + "=" + newValue);
        syncPageData();
    }

    // ----- ADD_MEMBER -----
    private void handleAddMember(TerritoryGuiActionPayload payload, TerritoryDataManager manager) {
        if (!canWrite()) return;
        String playerUuid = payload.targetData().get("playerUuid");
        String role = payload.targetData().get("role");
        if (playerUuid == null || role == null) return;

        boolean added = manager.addMember(territory.uuid(),
            new TerritoryData.MemberEntry(playerUuid, role));
        if (added) {
            logAction(manager, "ADD_MEMBER", playerUuid + " as " + role);
            syncPageData();
        }
    }

    // ----- REMOVE_MEMBER -----
    private void handleRemoveMember(TerritoryGuiActionPayload payload, TerritoryDataManager manager) {
        if (!canWrite()) return;
        String playerUuid = payload.targetData().get("playerUuid");
        if (playerUuid == null) return;

        // 不允许移除拥有者
        if (territory.ownerUuid().equals(playerUuid)) return;

        boolean removed = manager.removeMember(territory.uuid(), playerUuid);
        if (removed) {
            logAction(manager, "REMOVE_MEMBER", playerUuid);
            syncPageData();
        }
    }

    // ----- SET_ROLE -----
    private void handleSetRole(TerritoryGuiActionPayload payload, TerritoryDataManager manager) {
        if (!canWrite()) return;
        String playerUuid = payload.targetData().get("playerUuid");
        String roleStr = payload.targetData().get("role");
        if (playerUuid == null || roleStr == null) return;

        // 不允许修改拥有者角色
        if (territory.ownerUuid().equals(playerUuid)) return;

        try {
            TerritoryRole newRole = TerritoryRole.valueOf(roleStr.toUpperCase());
            // 只有更高等级的角色才能设置角色
            if (this.playerRole.isAtLeast(newRole) && !newRole.equals(TerritoryRole.OWNER)) {
                boolean updated = manager.setMemberRole(territory.uuid(), playerUuid, newRole);
                if (updated) {
                    logAction(manager, "SET_ROLE", playerUuid + " -> " + newRole.name());
                    syncPageData();
                }
            }
        } catch (IllegalArgumentException ignored) {
            // 无效角色名
        }
    }

    // ----- RENAME -----
    private void handleRename(TerritoryGuiActionPayload payload, TerritoryDataManager manager) {
        if (!isOwner()) return;
        String newName = payload.targetData().get("name");
        if (newName == null || newName.isBlank()) return;

        territory.name(newName.trim());
        manager.updateTerritory(territory);
        logAction(manager, "RENAME", newName.trim());
        syncPageData();
    }

    // ----- DELETE -----
    private void handleDelete(TerritoryDataManager manager) {
        if (!isOwner()) return;

        manager.deleteTerritory(territory.ownerUuid(), territory.uuid());
        // 删除后关闭玩家 GUI
        serverPlayer.closeContainer();
    }

    // ----- TRANSFER -----
    private void handleTransfer(TerritoryGuiActionPayload payload, TerritoryDataManager manager) {
        if (!isOwner()) return;
        String targetUuid = payload.targetData().get("targetUuid");
        if (targetUuid == null) return;

        // 更新拥有者
        String oldOwner = territory.ownerUuid();
        territory.ownerUuid(targetUuid);
        // 将旧拥有者添加为成员（如果不在成员列表中）
        boolean alreadyMember = false;
        for (TerritoryData.MemberEntry m : territory.members()) {
            if (m.playerUuid().equals(oldOwner)) { alreadyMember = true; break; }
        }
        if (!alreadyMember) {
            manager.addMember(territory.uuid(), new TerritoryData.MemberEntry(oldOwner, "admin"));
        }
        // 移除新拥有者的成员条目
        manager.removeMember(territory.uuid(), targetUuid);

        manager.updateTerritory(territory);
        logAction(manager, "TRANSFER", oldOwner + " -> " + targetUuid);
        syncPageData();
    }

    // ================================================================
    //  权限辅助
    // ================================================================

    /** 当前玩家是否拥有写权限（OWNER 或 ADMIN）。 */
    private boolean canWrite() {
        return playerRole.isAtLeast(TerritoryRole.ADMIN);
    }

    /** 当前玩家是否为拥有者。 */
    private boolean isOwner() {
        return playerRole == TerritoryRole.OWNER;
    }

    /** 记录操作日志。 */
    private void logAction(TerritoryDataManager manager, String action, String detail) {
        if (serverPlayer == null || territory == null) return;
        manager.addLog(territory.uuid(), new TerritoryLogEntry(
            serverPlayer.getUUID().toString(), action, Instant.now(), detail
        ));
    }

    // ================================================================
    //  数据同步 S→C
    // ================================================================

    /**
     * 根据当前页面构建数据并通过 {@link TerritoryGuiSyncPayload} 推送给客户端。
     */
    public void syncPageData() {
        if (serverPlayer == null || territory == null) return;

        Map<String, Object> pageData = new HashMap<>();
        String pageType;

        switch (currentPage) {
            case PAGE_INFO -> {
                pageType = "INFO";
                pageData.put("name", territory.name());
                pageData.put("ownerUuid", territory.ownerUuid());
                pageData.put("worldKey", territory.worldKey());
                pageData.put("area", (int) TerritoryDataManager.calculateArea(territory));
                pageData.put("memberCount", territory.members().size());
                pageData.put("createdAt", territory.createdAt());
                pageData.put("isAdmin", territory.admin());

                // 坐标范围
                pageData.put("minX", territory.minX());
                pageData.put("minY", territory.minY());
                pageData.put("minZ", territory.minZ());
                pageData.put("maxX", territory.maxX());
                pageData.put("maxY", territory.maxY());
                pageData.put("maxZ", territory.maxZ());

                // 出生点
                if (territory.hasSpawn()) {
                    pageData.put("spawnX", territory.spawnX());
                    pageData.put("spawnY", territory.spawnY());
                    pageData.put("spawnZ", territory.spawnZ());
                }

                pageData.put("role", playerRole.name());
            }

            case PAGE_MEMBERS -> {
                pageType = "MEMBERS";
                List<Map<String, Object>> membersData = new ArrayList<>();
                for (TerritoryData.MemberEntry m : territory.members()) {
                    Map<String, Object> mData = new HashMap<>();
                    mData.put("playerUuid", m.playerUuid());
                    mData.put("role", m.role());
                    membersData.add(mData);
                }
                pageData.put("members", membersData);
                pageData.put("ownerUuid", territory.ownerUuid());
                pageData.put("canEdit", canWrite());
            }

            case PAGE_FLAGS -> {
                pageType = "FLAGS";
                // 按分类序列化标志位
                for (FlagCategory cat : FlagCategory.values()) {
                    Map<String, Boolean> catFlags = new HashMap<>();
                    for (FlagType flag : FlagType.getByCategory(cat)) {
                        Object val = territory.flags().get(flag.name());
                        catFlags.put(flag.name(), val instanceof Boolean b ? b
                            : val instanceof String s ? "allow".equalsIgnoreCase(s)
                            : false);
                    }
                    pageData.put(cat.name(), catFlags);
                }
                pageData.put("canEdit", canWrite());
            }

            case PAGE_SETTINGS -> {
                pageType = "SETTINGS";
                pageData.put("name", territory.name());
                pageData.put("canRename", isOwner());
                pageData.put("canDelete", isOwner());

                // 出生点信息
                if (territory.hasSpawn()) {
                    pageData.put("spawnSet", true);
                    pageData.put("spawnX", territory.spawnX());
                    pageData.put("spawnY", territory.spawnY());
                    pageData.put("spawnZ", territory.spawnZ());
                } else {
                    pageData.put("spawnSet", false);
                }
            }

            case PAGE_LOGS -> {
                pageType = "LOGS";
                List<TerritoryLogEntry> logs = TerritoryDataManager.getInstance()
                    .getLogs(territory.uuid(), MAX_LOG_ENTRIES);
                List<Map<String, Object>> logsData = new ArrayList<>();
                for (TerritoryLogEntry entry : logs) {
                    Map<String, Object> eMap = new HashMap<>();
                    eMap.put("playerUuid", entry.playerUuid());
                    eMap.put("action", entry.action());
                    eMap.put("timestamp", entry.timestamp().toString());
                    eMap.put("detail", entry.detail());
                    logsData.add(eMap);
                }
                pageData.put("logs", logsData);
            }

            case PAGE_ADMIN -> {
                pageType = "ADMIN";
                pageData.put("isAdminTerritory", territory.admin());
                pageData.put("uuid", territory.uuid());
                pageData.put("ownerUuid", territory.ownerUuid());
                pageData.put("name", territory.name());
                pageData.put("isOwner", isOwner());
            }

            default -> {
                pageType = "INFO";
            }
        }

        PacketDistributor.sendToPlayer(serverPlayer,
            new TerritoryGuiSyncPayload(territory.uuid(), pageType, pageData));
    }

    // ================================================================
    //  AbstractContainerMenu 必须实现的方法
    // ================================================================

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 领地管理桌不依赖距离校验（未来可加距离检查）
        return true;
    }
}
