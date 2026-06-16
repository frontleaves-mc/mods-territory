package com.frontleaves.mods.territory.gui;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.defense.FlagCategory;
import com.frontleaves.mods.territory.defense.FlagType;
import com.frontleaves.mods.territory.defense.PlayerNameResolver;
import com.frontleaves.mods.territory.defense.TerritoryLogEntry;
import com.frontleaves.mods.territory.defense.TerritoryPermissionService;
import com.frontleaves.mods.territory.defense.TerritoryRole;
import com.frontleaves.mods.territory.network.TerritoryGuiSyncPayload;
import com.frontleaves.mods.territory.network.TerritoryLogsSyncPayload;
import com.frontleaves.mods.territory.network.TerritoryMembersSyncPayload;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 领地管理桌 GUI — 服务端 Container Menu。
 * <p>
 * 管理 6 页面（INFO / MEMBERS / FLAGS / SETTINGS / LOGS / ADMIN）的数据同步，
 * 客户端 GUI 操作通过 {@link TerritoryGuiActionPayload}（在 {@code TerritoryPayloads.handleGuiAction}）
 * 直接处理，本类仅负责服务端数据构建与 {@link TerritoryGuiSyncPayload} 推送。
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
    /** 服务端持有的领地数据；客户端为 {@code null}。 */
    private final TerritoryData territory;
    /**
     * 当前玩家在此领地中的角色。
     * <p>服务端由 {@link TerritoryPermissionService#getPlayerRole} 计算；
     * 客户端由 ExtendedMenuType setup buffer 注入（见 {@link Territory#TERRITORY_TABLE_MENU}）。
     */
    private TerritoryRole playerRole;
    private final ServerPlayer serverPlayer;
    /** 客户端侧领地 UUID（由 ExtendedMenuType setup buffer 注入）。 */
    private String clientTerritoryUuid;
    private int currentPage = PAGE_INFO;

    /**
     * 客户端构造器：从 {@link RegistryFriendlyByteBuf} 读取服务端写入的领地 UUID 与角色，
     * 由 {@code IMenuTypeExtension.create} 在客户端打开容器时调用。
     * <p>读取顺序必须与服务端 {@code openMenu(provider, buf -> ...)} 的写入顺序严格一致：
     * 先 territoryUuid，再 role.name()。
     *
     * @param containerId     容器 ID
     * @param playerInventory 玩家背包
     * @param data            服务端写入的额外数据（territoryUuid + role）
     */
    public TerritoryTableMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        super(getMenuType(), containerId);
        this.territory = null;
        this.serverPlayer = null;
        this.clientTerritoryUuid = data.readUtf();
        try {
            this.playerRole = TerritoryRole.valueOf(data.readUtf());
        } catch (IllegalArgumentException ex) {
            // 非法角色名 — 降级为访客，避免客户端崩溃
            this.playerRole = TerritoryRole.VISITOR;
        }
    }

    /**
     * 占位构造器：仅用于领地不存在或玩家类型不符时的兜底。
     * <p>不通过 MenuType 工厂调用，角色固定为 {@link TerritoryRole#VISITOR}。
     */
    public TerritoryTableMenu(int containerId, Inventory playerInventory) {
        super(getMenuType(), containerId);
        this.territory = null;
        this.serverPlayer = null;
        this.playerRole = TerritoryRole.VISITOR;
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
        this.clientTerritoryUuid = territory.uuid();
        this.playerRole = TerritoryPermissionService.getPlayerRole(serverPlayer, territory);
    }

    // ================================================================
    //  MenuType — 直接引用 Territory 注册的 DeferredHolder
    // ================================================================

    /**
     * 获取本 Menu 的注册类型。直接引用 {@link Territory#TERRITORY_TABLE_MENU}，
     * 由 DeferredHolder 保证在任意物理端注册完成后均可获取，避免静态字段注入。
     */
    private static MenuType<TerritoryTableMenu> getMenuType() {
        return Territory.TERRITORY_TABLE_MENU.get();
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

    /**
     * 获取客户端持有的领地 UUID。
     * <p>客户端 Menu 实例的 {@link #territory} 为 null，此方法返回由 setup buffer 注入的 UUID，
     * 供 {@code TerritoryTableScreen.sendAction} 等客户端逻辑使用。提交 2 的 ExtendedMenuType 重构将真正填充此值。
     *
     * @return 领地 UUID，若客户端尚未注入则返回 {@code null}
     */
    public String getClientTerritoryUuid() { return clientTerritoryUuid; }

    /** 获取当前玩家在此领地中的角色。 */
    public TerritoryRole getPlayerRole() { return playerRole; }

    // ================================================================
    //  权限辅助 — 供 syncPageData 构建页面数据时使用
    // ================================================================

    /** 当前玩家是否拥有写权限（OWNER 或 ADMIN）。 */
    private boolean canWrite() {
        return playerRole.isAtLeast(TerritoryRole.ADMIN);
    }

    /** 当前玩家是否为拥有者。 */
    private boolean isOwner() {
        return playerRole == TerritoryRole.OWNER;
    }

    // ================================================================
    //  数据同步 S→C
    // ================================================================

    /**
     * 解析领主玩家名（在线/缓存/回退 UUID）。
     */
    private String resolveOwnerName() {
        if (serverPlayer == null || territory == null) return null;
        return PlayerNameResolver.resolveName(serverPlayer.server, territory.ownerUuid());
    }

    /**
     * 根据当前页面构建数据并通过 {@link TerritoryGuiSyncPayload} 推送给客户端。
     * <p>MEMBERS / LOGS 页改用专用 payload（绕开 GuiSyncPayload codec 不支持复合类型的限制）；
     * FLAGS 页采用扁平 Boolean 键（避免 Map 被退化）。
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
                pageData.put("ownerName", resolveOwnerName());
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
                // 成员列表改用专用 payload 同步（绕开 GuiSyncPayload codec 限制）
                pageType = "MEMBERS";
                pageData.put("ownerUuid", territory.ownerUuid());
                pageData.put("ownerName", resolveOwnerName());
                pageData.put("canEdit", canWrite());

                List<TerritoryMembersSyncPayload.MemberInfo> membersData = new ArrayList<>();
                for (TerritoryData.MemberEntry m : territory.members()) {
                    String mName = PlayerNameResolver.resolveName(serverPlayer.server, m.playerUuid());
                    boolean isOwner = m.playerUuid().equals(territory.ownerUuid());
                    membersData.add(new TerritoryMembersSyncPayload.MemberInfo(
                        m.playerUuid(), mName, m.role(), isOwner));
                }
                PacketDistributor.sendToPlayer(serverPlayer,
                    new TerritoryMembersSyncPayload(territory.uuid(), membersData,
                        territory.ownerUuid(), canWrite()));
            }

            case PAGE_FLAGS -> {
                pageType = "FLAGS";
                // 扁平化：每个 flag 用独立 Boolean 键，避免 Map 被 codec 退化成 String
                for (FlagCategory cat : FlagCategory.values()) {
                    for (FlagType flag : FlagType.getByCategory(cat)) {
                        Object val = territory.flags().get(flag.name());
                        boolean boolVal = val instanceof Boolean b ? b
                            : val instanceof String s ? "allow".equalsIgnoreCase(s) : false;
                        pageData.put("flag." + flag.name(), boolVal);
                    }
                }
                pageData.put("canEdit", canWrite());
            }

            case PAGE_SETTINGS -> {
                pageType = "SETTINGS";
                pageData.put("name", territory.name());
                pageData.put("ownerName", resolveOwnerName());
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
                // 日志列表改用专用 payload 同步
                pageType = "LOGS";
                List<TerritoryLogEntry> logs = TerritoryDataManager.getInstance()
                    .getLogs(territory.uuid(), MAX_LOG_ENTRIES);
                List<TerritoryLogsSyncPayload.LogInfo> logsData = new ArrayList<>();
                for (TerritoryLogEntry entry : logs) {
                    String opName = PlayerNameResolver.resolveName(serverPlayer.server, entry.playerUuid());
                    logsData.add(new TerritoryLogsSyncPayload.LogInfo(
                        opName, entry.action(), entry.timestamp().toString(), entry.detail()));
                }
                PacketDistributor.sendToPlayer(serverPlayer,
                    new TerritoryLogsSyncPayload(territory.uuid(), logsData));
            }

            case PAGE_ADMIN -> {
                pageType = "ADMIN";
                pageData.put("isAdminTerritory", territory.admin());
                pageData.put("uuid", territory.uuid());
                pageData.put("ownerUuid", territory.ownerUuid());
                pageData.put("ownerName", resolveOwnerName());
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
