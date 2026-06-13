package com.frontleaves.mods.territory.client;

import com.frontleaves.mods.territory.defense.FlagCategory;
import com.frontleaves.mods.territory.defense.FlagType;
import com.frontleaves.mods.territory.defense.TerritoryRole;
import com.frontleaves.mods.territory.gui.TerritoryTableMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 领地管理桌 GUI — 客户端渲染层。
 * <p>
 * 基于 {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen} 实现 6 页面标签式 UI，
 * 接收服务端 {@link com.frontleaves.mods.territory.network.TerritoryGuiSyncPayload} 数据并渲染，
 * 通过 {@link com.frontleaves.mods.territory.network.TerritoryGuiActionPayload} 向服务端发送操作请求。
 * <p>
 * 页面可见性由 {@link TerritoryTableMenu#getPlayerRole()} 决定：
 * <ul>
 *   <li>OWNER — 全部页面</li>
 *   <li>ADMIN — 除 ADMIN 外全部</li>
 *   <li>MEMBER — INFO / MEMBERS / FLAGS</li>
 *   <li>VISITOR — 仅 INFO</li>
 * </ul>
 *
 * @see TerritoryTableMenu 服务端 Menu
 */
public class TerritoryTableScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<TerritoryTableMenu> {

    // ===== 布局常量 =====
    private static final int TAB_WIDTH = 40;
    private static final int TAB_HEIGHT = 14;
    private static final int CONTENT_PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int SECTION_GAP = 6;

    // ===== 标签定义 =====
    private static final String[] TAB_NAMES = {"INFO", "MEMBERS", "FLAGS", "SETTINGS", "LOGS", "ADMIN"};
    private static final int[] TAB_COLORS = {
        0xFF4A90D9,  // INFO — 蓝
        0xFF50C878,  // MEMBERS — 绿
        0xFFFFB347,  // FLAGS — 橙
        0xFF9B59B6,  // SETTINGS — 紫
        0xFFE74C3C,  // LOGS — 红
        0xFF95A5A6,  // ADMIN — 灰
    };

    // ===== 同步数据缓存（由 S→C payload 更新） =====
    /** 当前页面的同步数据。 */
    private Map<String, Object> currentPageData = new java.util.HashMap<>();

    // ===== 滚动状态 =====
    private int membersScrollOffset = 0;
    private int flagsScrollOffset = 0;
    private int logsScrollOffset = 0;
    private static final int MAX_VISIBLE_ROWS = 12;

    public TerritoryTableScreen(TerritoryTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 260;   // 比 MC 标准容器更宽，容纳 6 个标签 + 内容
        this.imageHeight = 200;
    }

    // ================================================================
    //  数据接收接口 — 由网络 handler 在收到 S→C payload 时调用
    // ================================================================

    /**
     * 接收服务端推送的页面数据并缓存。
     *
     * @param pageType 页面类型标识
     * @param pageData 页面数据键值对
     */
    public void receiveSyncData(String pageType, Map<String, Object> pageData) {
        this.currentPageData = pageData != null ? pageData : new java.util.HashMap<>();
        // 重置滚动位置
        this.membersScrollOffset = 0;
        this.flagsScrollOffset = 0;
        this.logsScrollOffset = 0;
    }

    // ================================================================
    //  渲染
    // ================================================================

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        // ---- 背景 ----
        renderPanelBackground(guiGraphics, leftPos, topPos);

        // ---- 标签栏 ----
        renderPageTabs(guiGraphics, leftPos, topPos);

        // ---- 页面内容 ----
        renderPageContent(guiGraphics, leftPos, topPos, mouseX, mouseY);
    }

    /**
     * 渲染主面板背景（深色半透明 + 边框）。
     */
    private void renderPanelBackground(GuiGraphics guiGraphics, int x, int y) {
        // 主背景
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xDD1A1A1A);
        // 内边框高光
        guiGraphics.fill(x, y, x + imageWidth, y + 1, 0xFF555555);
        guiGraphics.fill(x, y, x + 1, y + imageHeight, 0xFF555555);
        guiGraphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF333333);
        guiGraphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF333333);
    }

    /**
     * 渲染顶部 6 个页面标签。
     */
    private void renderPageTabs(GuiGraphics guiGraphics, int x, int y) {
        TerritoryRole role = menu.getPlayerRole();
        int totalTabs = Math.min(TAB_NAMES.length, getMaxPageForRole(role) + 1);
        int tabStartX = x + (imageWidth - totalTabs * (TAB_WIDTH + 2)) / 2;

        for (int i = 0; i < totalTabs; i++) {
            if (!isPageVisible(i, role)) continue;

            int tabX = tabStartX + i * (TAB_WIDTH + 2);
            int tabY = y - TAB_HEIGHT + 2;
            boolean isActive = menu.getCurrentPage() == i;

            // 标签背景
            int bgColor = isActive ? TAB_COLORS[i] : 0xFF3A3A3A;
            guiGraphics.fill(tabX, tabY, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, bgColor);

            // 激活状态底部指示线
            if (isActive) {
                guiGraphics.fill(tabX, tabY + TAB_HEIGHT - 2, tabX + TAB_WIDTH, tabY + TAB_HEIGHT, 0xFFFFFFFF);
            }

            // 标签文字
            String label = TAB_NAMES[i];
            int textWidth = font.width(label);
            guiGraphics.drawString(font, label,
                tabX + (TAB_WIDTH - textWidth) / 2,
                tabY + (TAB_HEIGHT - font.lineHeight) / 2,
                0xFFFFFF);
        }
    }

    /**
     * 根据当前页面分发内容渲染。
     */
    private void renderPageContent(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        int cx = x + CONTENT_PADDING;
        int cy = y + CONTENT_PADDING + 4;

        switch (menu.getCurrentPage()) {
            case TerritoryTableMenu.PAGE_INFO -> renderInfoPage(guiGraphics, cx, cy);
            case TerritoryTableMenu.PAGE_MEMBERS -> renderMembersPage(guiGraphics, cx, cy, mouseX, mouseY);
            case TerritoryTableMenu.PAGE_FLAGS -> renderFlagsPage(guiGraphics, cx, cy, mouseX, mouseY);
            case TerritoryTableMenu.PAGE_SETTINGS -> renderSettingsPage(guiGraphics, cx, cy);
            case TerritoryTableMenu.PAGE_LOGS -> renderLogsPage(guiGraphics, cx, cy);
            case TerritoryTableMenu.PAGE_ADMIN -> renderAdminPage(guiGraphics, cx, cy);
            default -> { /* 未知页面 */ }
        }

        // 底部角色提示
        TerritoryRole role = menu.getPlayerRole();
        String roleHint = "[" + role.getDisplayName() + "] " + this.title.getString();
        guiGraphics.drawString(font, roleHint, cx, y + imageHeight - 16, 0x888888);
    }

    // ----- INFO 页面 -----
    private void renderInfoPage(GuiGraphics guiGraphics, int x, int y) {
        int ly = y;
        ly = drawRow(guiGraphics, x, ly, "Name", getString(currentPageData, "name"));
        ly = drawRow(guiGraphics, x, ly, "Owner", getString(currentPageData, "ownerUuid"));
        ly = drawRow(guiGraphics, x, ly, "World", getString(currentPageData, "worldKey"));

        Object areaObj = currentPageData.get("area");
        String areaStr = areaObj instanceof Number n ? String.valueOf(n.intValue()) : "?";
        ly = drawRow(guiGraphics, x, ly, "Area", areaStr + " m²");

        Object memberCountObj = currentPageData.get("memberCount");
        String countStr = memberCountObj instanceof Number n ? String.valueOf(n.intValue()) : "0";
        ly = drawRow(guiGraphics, x, ly, "Members", countStr);

        ly += SECTION_GAP;
        ly = drawSectionHeader(guiGraphics, x, ly, "Coordinates");
        ly = drawRow(guiGraphics, x, ly, "Min",
            getInt(currentPageData, "minX") + ", "
            + getInt(currentPageData, "minY") + ", "
            + getInt(currentPageData, "minZ"));
        ly = drawRow(guiGraphics, x, ly, "Max",
            getInt(currentPageData, "maxX") + ", "
            + getInt(currentPageData, "maxY") + ", "
            + getInt(currentPageData, "maxZ"));

        // 出生点
        if (currentPageData.containsKey("spawnX")) {
            ly += SECTION_GAP;
            ly = drawSectionHeader(guiGraphics, x, ly, "Spawn Point");
            Object sx = currentPageData.get("spawnX");
            Object sy = currentPageData.get("spawnY");
            Object sz = currentPageData.get("spawnZ");
            String spawnStr = (sx != null ? String.format("%.1f", ((Number) sx).doubleValue()) : "?")
                + ", " + (sy != null ? String.format("%.1f", ((Number) sy).doubleValue()) : "?")
                + ", " + (sz != null ? String.format("%.1f", ((Number) sz).doubleValue()) : "?");
            ly = drawRow(guiGraphics, x, ly, "Position", spawnStr);
        }

        // 创建时间
        String createdAt = getString(currentPageData, "createdAt");
        if (createdAt != null && !createdAt.isEmpty()) {
            ly += SECTION_GAP;
            ly = drawRow(guiGraphics, x, ly, "Created", createdAt.substring(0, Math.min(19, createdAt.length())));
        }
    }

    // ----- MEMBERS 页面 -----
    private void renderMembersPage(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        boolean canEdit = getBool(currentPageData, "canEdit", false);
        String ownerUuid = getString(currentPageData, "ownerUuid");

        y = drawSectionHeader(guiGraphics, x, y, "Members "
            + (canEdit ? "[Editable]" : "[Read-only]"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) currentPageData.get("members");
        if (members == null || members.isEmpty()) {
            guiGraphics.drawString(font, "(No members)", x + 4, y + 2, 0x888888);
            return;
        }

        // 表头
        int headerY = y;
        guiGraphics.fill(x, headerY, x + imageWidth - CONTENT_PADDING * 2, headerY + LINE_HEIGHT + 2, 0xFF2A2A2A);
        guiGraphics.drawString(font, "UUID", x + 4, headerY + 2, 0xAAAAAA);
        guiGraphics.drawString(font, "Role", x + 140, headerY + 2, 0xAAAAAA);

        int rowY = headerY + LINE_HEIGHT + 4;
        int visibleEnd = Math.min(members.size(), membersScrollOffset + MAX_VISIBLE_ROWS);

        for (int i = membersScrollOffset; i < visibleEnd; i++) {
            Map<String, Object> m = members.get(i);
            String uuid = getString(m, "playerUuid");
            String role = getString(m, "role");

            // 行悬停效果
            boolean hovered = mouseX >= x && mouseX <= x + imageWidth - CONTENT_PADDING * 2
                && mouseY >= rowY && mouseY <= rowY + LINE_HEIGHT;
            if (hovered) {
                guiGraphics.fill(x, rowY, x + imageWidth - CONTENT_PADDING * 2, rowY + LINE_HEIGHT + 1, 0x33FFFFFF);
            }

            // 高亮拥有者
            boolean isOwner = uuid != null && uuid.equals(ownerUuid);
            int roleColor = switch (role == null ? "" : role.toLowerCase()) {
                case "admin" -> 0xFF9B59B6;
                case "member" -> 0xFF50C878;
                default -> 0xFFFFFF;
            };
            if (isOwner) roleColor = 0xFFFFD700;

            // 截断 UUID 显示
            String displayUuid = uuid != null && uuid.length() > 20
                ? uuid.substring(0, 17) + "..." : (uuid != null ? uuid : "?");
            guiGraphics.drawString(font, displayUuid, x + 4, rowY + 2, 0xFFFFFF);
            guiGraphics.drawString(font, role != null ? role : "?", x + 140, rowY + 2, roleColor);

            // 所有者标记
            if (isOwner) {
                guiGraphics.drawString(font, "[OWNER]", x + 175, rowY + 2, 0xFFFFD700);
            }

            rowY += LINE_HEIGHT + 2;
        }

        // 滚动提示
        if (members.size() > MAX_VISIBLE_ROWS) {
            String scrollHint = (membersScrollOffset + 1) + "-" + visibleEnd + " / " + members.size();
            guiGraphics.drawString(font, scrollHint, x + imageWidth - CONTENT_PADDING * 2 - font.width(scrollHint),
                y + imageHeight - 40, 0x666666);
        }
    }

    // ----- FLAGS 页面 -----
    private void renderFlagsPage(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        boolean canEdit = getBool(currentPageData, "canEdit", false);
        y = drawSectionHeader(guiGraphics, x, y, "Permission Flags "
            + (canEdit ? "[Editable]" : "[Read-only]"));

        int colX = x;
        int categoryTop = y;

        for (FlagCategory cat : FlagCategory.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> catFlags = (Map<String, Boolean>) currentPageData.get(cat.name());
            if (catFlags == null) continue;

            // 分类标题
            int catY = categoryTop;
            guiGraphics.drawString(font, cat.getDisplayName() + " " + cat.getIcon(),
                colX, catY, 0xFFCCAA33);
            catY += LINE_HEIGHT + 2;

            for (FlagType flag : FlagType.getByCategory(cat)) {
                Boolean value = catFlags.get(flag.name());
                if (value == null) value = false;

                // 行背景交替
                int rowIdx = FlagType.getByCategory(cat).indexOf(flag);
                if (rowIdx % 2 == 0) {
                    guiGraphics.fill(colX, catY, colX + 220, catY + LINE_HEIGHT + 1, 0x15FFFFFF);
                }

                // 标志名称
                guiGraphics.drawString(font, flag.getDisplayName(), colX + 4, catY + 2, 0xCCCCCC);

                // 开关按钮
                int toggleX = colX + 170;
                int toggleW = 44;
                int toggleH = LINE_HEIGHT;
                boolean toggleHovered = mouseX >= toggleX && mouseX <= toggleX + toggleW
                    && mouseY >= catY && mouseY <= catY + toggleH;
                int toggleColor = value ? (toggleHovered ? 0xFF33BB33 : 0xFF228822)
                    : (toggleHovered ? 0xFFCC3333 : 0xFFAA2222);
                guiGraphics.fill(toggleX, catY, toggleX + toggleW, catY + toggleH, toggleColor);

                String stateLabel = value ? " [ON] " : " [OFF]";
                int stateColor = value ? 0xFF55FF55 : 0xFFFF5555;
                int labelW = font.width(stateLabel);
                guiGraphics.drawString(font, stateLabel,
                    toggleX + (toggleW - labelW) / 2, catY + 2, stateColor);

                // 宏标志标记
                if (flag.isMacro()) {
                    guiGraphics.drawString(font, "*", colX + 195, catY + 2, 0xFF88FFFF);
                }

                catY += LINE_HEIGHT + 2;
            }

            // 列偏移：两列布局
            colX += 230;
            if (colX + 220 > x + imageWidth - CONTENT_PADDING * 2) {
                colX = x;
                categoryTop = catY + SECTION_GAP;
            } else {
                categoryTop = y; // 第二列从同一行开始
            }
        }
    }

    // ----- SETTINGS 页面 -----
    private void renderSettingsPage(GuiGraphics guiGraphics, int x, int y) {
        boolean canRename = getBool(currentPageData, "canRename", false);
        boolean canDelete = getBool(currentPageData, "canDelete", false);
        boolean spawnSet = getBool(currentPageData, "spawnSet", false);

        y = drawSectionHeader(guiGraphics, x, y, "Territory Settings");

        y = drawRow(guiGraphics, x, y, "Name", getString(currentPageData, "name"));
        if (canRename) {
            guiGraphics.drawString(font, "(editable)", x + 160, y - LINE_HEIGHT, 0xFF50C878);
        }

        y += SECTION_GAP;
        y = drawSectionHeader(guiGraphics, x, y, "Spawn Point");
        if (spawnSet) {
            Object sx = currentPageData.get("spawnX");
            Object sy = currentPageData.get("spawnY");
            Object sz = currentPageData.get("spawnZ");
            y = drawRow(guiGraphics, x, y, "Position",
                String.format("%.1f, %.1f, %.1f",
                    sx instanceof Number n ? n.doubleValue() : 0,
                    sy instanceof Number n ? n.doubleValue() : 0,
                    sz instanceof Number n ? n.doubleValue() : 0));
        } else {
            guiGraphics.drawString(font, "(not set)", x + 4, y + 2, 0x888888);
            y += LINE_HEIGHT;
        }

        // 危险操作区
        if (canDelete) {
            y += SECTION_GAP + 4;
            guiGraphics.fill(x, y, x + imageWidth - CONTENT_PADDING * 2, y + LINE_HEIGHT + 4, 0x44FF0000);
            guiGraphics.drawString(font, "[!] Delete Territory — OWNER only", x + 4, y + 2, 0xFFCC3333);
        }
    }

    // ----- LOGS 页面 -----
    private void renderLogsPage(GuiGraphics guiGraphics, int x, int y) {
        y = drawSectionHeader(guiGraphics, x, y, "Operation Logs");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) currentPageData.get("logs");
        if (logs == null || logs.isEmpty()) {
            guiGraphics.drawString(font, "(No logs)", x + 4, y + 2, 0x888888);
            return;
        }

        // 表头
        int headerY = y;
        guiGraphics.fill(x, headerY, x + imageWidth - CONTENT_PADDING * 2, headerY + LINE_HEIGHT + 2, 0xFF2A2A2A);
        guiGraphics.drawString(font, "Time", x + 4, headerY + 2, 0xAAAAAA);
        guiGraphics.drawString(font, "Action", x + 80, headerY + 2, 0xAAAAAA);
        guiGraphics.drawString(font, "Detail", x + 130, headerY + 2, 0xAAAAAA);

        int rowY = headerY + LINE_HEIGHT + 4;
        int visibleEnd = Math.min(logs.size(), logsScrollOffset + MAX_VISIBLE_ROWS);

        for (int i = logsScrollOffset; i < visibleEnd; i++) {
            Map<String, Object> entry = logs.get(i);
            String timestamp = getString(entry, "timestamp");
            String action = getString(entry, "action");
            String detail = getString(entry, "detail");

            // 截断显示
            String timeShort = timestamp != null && timestamp.length() > 19
                ? timestamp.substring(11, 19) : (timestamp != null ? timestamp : "?");
            String detailShort = detail != null && detail.length() > 30
                ? detail.substring(0, 27) + "..." : (detail != null ? detail : "");

            // 操作类型颜色编码
            int actionColor = switch (action == null ? "" : action) {
                case "SET_FLAG" -> 0xFFFFB347;
                case "ADD_MEMBER", "REMOVE_MEMBER", "SET_ROLE" -> 0xFF50C878;
                case "RENAME" -> 0xFF4A90D9;
                case "DELETE", "TRANSFER" -> 0xFFE74C3C;
                default -> 0xFFFFFF;
            };

            guiGraphics.drawString(font, timeShort, x + 4, rowY + 2, 0x888888);
            guiGraphics.drawString(font, action != null ? action : "?", x + 80, rowY + 2, actionColor);
            guiGraphics.drawString(font, detailShort, x + 130, rowY + 2, 0xCCCCCC);

            rowY += LINE_HEIGHT + 1;
        }

        // 滚动提示
        if (logs.size() > MAX_VISIBLE_ROWS) {
            String hint = (logsScrollOffset + 1) + "-" + visibleEnd + " / " + logs.size();
            guiGraphics.drawString(font, hint,
                x + imageWidth - CONTENT_PADDING * 2 - font.width(hint),
                y + imageHeight - 40, 0x666666);
        }
    }

    // ----- ADMIN 页面 -----
    private void renderAdminPage(GuiGraphics guiGraphics, int x, int y) {
        boolean isOwner = getBool(currentPageData, "isOwner", false);
        boolean isAdminTerritory = getBool(currentPageData, "isAdminTerritory", false);

        y = drawSectionHeader(guiGraphics, x, y, "Administration "
            + (isOwner ? "[Full Access]" : "[Limited]"));

        y = drawRow(guiGraphics, x, y, "UUID", getString(currentPageData, "uuid"));
        y = drawRow(guiGraphics, x, y, "Type", isAdminTerritory ? "Admin Territory" : "Normal Territory");
        y = drawRow(guiGraphics, x, y, "Owner", getString(currentPageData, "ownerUuid"));

        if (isOwner) {
            y += SECTION_GAP + 4;
            // 转让操作区域
            guiGraphics.fill(x, y, x + imageWidth - CONTENT_PADDING * 2, y + LINE_HEIGHT + 4, 0x44CC8800);
            guiGraphics.drawString(font, "[!] Transfer Ownership — irreversible!", x + 4, y + 2, 0xFFCC8800);
        } else {
            y += SECTION_GAP;
            guiGraphics.drawString(font, "Only the OWNER can access admin functions.",
                x + 4, y + 2, 0x888888);
        }
    }

    // ================================================================
    //  交互处理
    // ================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int leftPos = (this.width - this.imageWidth) / 2;
        int topPos = (this.height - this.imageHeight) / 2;

        // ---- 标签点击检测 ----
        if (handleTabClick(mouseX, mouseY, leftPos, topPos)) {
            return true;
        }

        // ---- Flags 页面开关点击 ----
        if (menu.getCurrentPage() == TerritoryTableMenu.PAGE_FLAGS) {
            if (handleFlagToggleClick(mouseX, mouseY, leftPos, topPos)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 根据当前页面分配滚轮事件
        double delta = scrollY;
        if (delta == 0) delta = scrollX;

        return switch (menu.getCurrentPage()) {
            case TerritoryTableMenu.PAGE_MEMBERS -> scrollMembers(delta);
            case TerritoryTableMenu.PAGE_FLAGS -> scrollFlags(delta);
            case TerritoryTableMenu.PAGE_LOGS -> scrollLogs(delta);
            default -> false;
        };
    }

    /**
     * 处理标签栏点击切换页面。
     */
    private boolean handleTabClick(double mouseX, double mouseY, int leftPos, int topPos) {
        TerritoryRole role = menu.getPlayerRole();
        int totalTabs = Math.min(TAB_NAMES.length, getMaxPageForRole(role) + 1);
        int tabStartX = leftPos + (imageWidth - totalTabs * (TAB_WIDTH + 2)) / 2;
        int tabY = topPos - TAB_HEIGHT + 2;

        for (int i = 0; i < totalTabs; i++) {
            if (!isPageVisible(i, role)) continue;
            int tabX = tabStartX + i * (TAB_WIDTH + 2);
            if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                menu.setPage(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 处理 Flags 页面的开关点击，发送 SET_FLAG 操作到服务端。
     */
    private boolean handleFlagToggleClick(double mouseX, double mouseY, int leftPos, int topPos) {
        boolean canEdit = getBool(currentPageData, "canEdit", false);
        if (!canEdit) return false;

        int cx = leftPos + CONTENT_PADDING;
        int cy = topPos + CONTENT_PADDING + 4;
        cy += LINE_HEIGHT + 2 + SECTION_GAP; // 跳过标题

        for (FlagCategory cat : FlagCategory.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> catFlags = (Map<String, Boolean>) currentPageData.get(cat.name());
            if (catFlags == null) continue;

            cy += LINE_HEIGHT + 2; // 分类标题

            for (FlagType flag : FlagType.getByCategory(cat)) {
                int toggleX = cx + 170;
                int toggleW = 44;
                int toggleH = LINE_HEIGHT;

                if (mouseX >= toggleX && mouseX <= toggleX + toggleW
                    && mouseY >= cy && mouseY <= cy + toggleH) {

                    // 发送 SET_FLAG 操作
                    Boolean currentVal = catFlags.get(flag.name());
                    sendAction("SET_FLAG", Map.of(
                        "flag", flag.name(),
                        "value", String.valueOf(currentVal == null || !currentVal)
                    ));
                    return true;
                }
                cy += LINE_HEIGHT + 2;
            }
        }
        return false;
    }

    // ---- 滚动方法 ----

    private boolean scrollMembers(double delta) {
        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) currentPageData.get("members");
        if (list == null) return false;
        int maxOffset = Math.max(0, list.size() - MAX_VISIBLE_ROWS);
        membersScrollOffset = (int) Math.clamp(membersScrollOffset - (int) delta, 0, maxOffset);
        return true;
    }

    private boolean scrollFlags(double delta) {
        flagsScrollOffset = (int) Math.max(0, flagsScrollOffset - (int) delta);
        return true;
    }

    private boolean scrollLogs(double delta) {
        @SuppressWarnings("unchecked")
        List<?> list = (List<?>) currentPageData.get("logs");
        if (list == null) return false;
        int maxOffset = Math.max(0, list.size() - MAX_VISIBLE_ROWS);
        logsScrollOffset = (int) Math.clamp(logsScrollOffset - (int) delta, 0, maxOffset);
        return true;
    }

    // ================================================================
    //  网络发送辅助
    // ================================================================

    /**
     * 向服务端发送 GUI 操作请求。
     *
     * @param actionType 操作类型
     * @param targetData 目标数据
     */
    private void sendAction(String actionType, Map<String, String> targetData) {
        // 获取领地 UUID — 从 Menu 的 territory 或缓存中获取
        String territoryUuid = null;
        if (menu.getTerritory() != null) {
            territoryUuid = menu.getTerritory().uuid();
        } else {
            // 回退：尝试从数据中获取
            territoryUuid = getString(currentPageData, "uuid");
        }
        if (territoryUuid == null) return;

        var payload = new com.frontleaves.mods.territory.network.TerritoryGuiActionPayload(
            territoryUuid, actionType, targetData
        );
        PacketDistributor.sendToServer(payload);
    }

    // ================================================================
    //  角色与页面可见性
    // ================================================================

    /**
     * 获取指定角色可访问的最大页面索引。
     */
    private static int getMaxPageForRole(TerritoryRole role) {
        return switch (role) {
            case OWNER -> TerritoryTableMenu.PAGE_ADMIN;
            case ADMIN -> TerritoryTableMenu.PAGE_LOGS;   // ADMIN 不能看 ADMIN 页
            case MEMBER -> TerritoryTableMenu.PAGE_FLAGS; // MEMBER 只能看 INFO/MEMBERS/FLAGS
            case VISITOR -> TerritoryTableMenu.PAGE_INFO; // VISITOR 只能看 INFO
        };
    }

    /**
     * 判断指定页面是否对当前角色可见。
     */
    private static boolean isPageVisible(int page, TerritoryRole role) {
        return page <= getMaxPageForRole(role);
    }

    // ================================================================
    //  渲染辅助方法
    // ================================================================

    /** 绘制一行键值对，返回下一行 Y 坐标。 */
    private int drawRow(GuiGraphics g, int x, int y, String key, String value) {
        g.drawString(font, key + ":", x, y, 0xAAAAAA);
        g.drawString(font, value != null ? value : "N/A", x + 80, y, 0xFFFFFF);
        return y + LINE_HEIGHT + 1;
    }

    /** 绘制分区标题，返回下一行 Y 坐标。 */
    private int drawSectionHeader(GuiGraphics g, int x, int y, String title) {
        g.fill(x, y - 2, x + imageWidth - CONTENT_PADDING * 2, y + LINE_HEIGHT + 1, 0xFF333355);
        g.drawString(font, title, x + 4, y, 0xFFCCAA33);
        return y + LINE_HEIGHT + 4;
    }

    // ---- 安全的数据读取 ----

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Number n ? n.intValue() : 0;
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean fallback) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    // ================================================================
    //  覆写
    // ================================================================

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 不绘制默认标题 — 我们在 renderBg 中自定义了标题
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 绘制工具提示（鼠标悬停信息）
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
