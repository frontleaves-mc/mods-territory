package com.frontleaves.mods.territory.client;

import com.frontleaves.mods.territory.gui.TerritoryBookMenu;
import com.frontleaves.mods.territory.network.TerritoryTeleportRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 领地之书 GUI — 客户端渲染层。
 * <p>
 * 基于 {@link AbstractContainerScreen} 实现，视觉沿用领地桌深色面板范式：
 * 顶部 OWNED/SHARED 双标签 + 搜索框 + 可滚动列表 + 每行传送按钮。
 * 数据由服务端 {@link com.frontleaves.mods.territory.network.TerritoryGuiSyncPayload}
 * 以 BOOK_LIST pageType 推送，存入 {@link TerritoryBookMenu}。
 * <p>
 * 传送操作发送 {@link TerritoryTeleportRequestPayload}（复用领地桌传送管道）。
 *
 * @see TerritoryBookMenu 服务端容器
 */
public class TerritoryBookScreen extends AbstractContainerScreen<TerritoryBookMenu> {

    // ===== 布局常量（沿用领地桌） =====
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_PADDING = 10;       // 标签左右内边距
    private static final int TAB_SPACING = 2;        // 标签间距
    private static final int CONTENT_PADDING = 8;
    private static final int ROW_HEIGHT = 25;
    private static final int MAX_VISIBLE_ROWS = 12;

    // ===== 标签（翻译键，由 renderPageTabs 解析为本地化文本） =====
    private static final String[] TAB_KEYS = {
        "gui.territory.book.tab.owned",
        "gui.territory.book.tab.shared"
    };
    private static final int[] TAB_COLORS = {0xFF4A90D9, 0xFF50C878};  // 蓝 / 绿

    // ===== 状态 =====
    private boolean showOwnedTab = true;
    private EditBox searchBox;
    private String searchQuery = "";
    private int scrollOffset = 0;

    public TerritoryBookScreen(TerritoryBookMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 260;
        this.imageHeight = 200;
    }

    // ----------------------------------------------------------------
    //  数据接收 — 由 handleGuiSync 调用（数据已存入 menu）
    // ----------------------------------------------------------------
    public void onSyncReceived() {
        this.scrollOffset = 0;
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = -100;  // 隐藏默认"物品栏"标签
        this.titleLabelY = -100;       // 隐藏默认标题（自定义绘制）

        int searchW = 200;
        int searchX = (this.width - searchW) / 2;
        int searchY = this.topPos + TAB_HEIGHT + 10;
        searchBox = new EditBox(this.font, searchX, searchY, searchW, 16, Component.literal(""));
        searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchBox);
    }

    private void onSearchChanged(String query) {
        searchQuery = query.toLowerCase();
        this.scrollOffset = 0;
    }

    // ----------------------------------------------------------------
    //  渲染
    // ----------------------------------------------------------------
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        renderPanelBackground(guiGraphics, x, y);
        renderPageTabs(guiGraphics, x, y);
    }

    /** 深色半透明面板 + 边框（同领地桌）。 */
    private void renderPanelBackground(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xDD1A1A1A);
        g.fill(x, y, x + imageWidth, y + 1, 0xFF555555);
        g.fill(x, y, x + 1, y + imageHeight, 0xFF555555);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF333333);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF333333);
    }

    /** 计算每个标签的横向位置与宽度（宽度按本地化文本自适应，整体居中）。 */
    private int[][] computeTabLayout() {
        int totalTabs = TAB_KEYS.length;
        int[] widths = new int[totalTabs];
        int totalWidth = 0;
        for (int i = 0; i < totalTabs; i++) {
            widths[i] = font.width(Component.translatable(TAB_KEYS[i])) + TAB_PADDING * 2;
            totalWidth += widths[i] + (i > 0 ? TAB_SPACING : 0);
        }
        int[] xs = new int[totalTabs];
        int cursor = this.leftPos + (imageWidth - totalWidth) / 2;
        for (int i = 0; i < totalTabs; i++) {
            if (i > 0) cursor += TAB_SPACING;
            xs[i] = cursor;
            cursor += widths[i];
        }
        return new int[][]{xs, widths};
    }

    /** 顶部 OWNED/SHARED 标签（同领地桌样式，宽度随本地化文本自适应）。 */
    private void renderPageTabs(GuiGraphics g, int x, int y) {
        int[][] layout = computeTabLayout();
        int[] xs = layout[0];
        int[] widths = layout[1];
        int tabY = y - TAB_HEIGHT + 2;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int tabX = xs[i];
            int tabW = widths[i];
            boolean isActive = (i == 0) == showOwnedTab;
            int bgColor = isActive ? TAB_COLORS[i] : 0xFF3A3A3A;
            g.fill(tabX, tabY, tabX + tabW, tabY + TAB_HEIGHT, bgColor);
            if (isActive) {
                g.fill(tabX, tabY + TAB_HEIGHT - 2, tabX + tabW, tabY + TAB_HEIGHT, 0xFFFFFFFF);
            }
            Component label = Component.translatable(TAB_KEYS[i]);
            int textWidth = font.width(label);
            g.drawString(font, label,
                tabX + (tabW - textWidth) / 2,
                tabY + (TAB_HEIGHT - font.lineHeight) / 2, 0xFFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 搜索框 placeholder
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.territory.book.search_hint"),
                searchBox.getX() + 5, searchBox.getY() + 4, 0x808080);
        }

        renderList(guiGraphics, mouseX, mouseY);

        // 底部统计
        List<String[]> currentList = getFilteredList();
        String stats = Component.translatable("gui.territory.book.stats", currentList.size()).getString();
        guiGraphics.drawString(this.font, stats,
            this.leftPos + CONTENT_PADDING, this.topPos + imageHeight - 14, 0x888888);
    }

    /** 渲染领地列表（含每行 hover 高亮 + 传送按钮）。 */
    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        List<String[]> currentList = getFilteredList();
        int listX = this.leftPos + CONTENT_PADDING;
        int listY = searchBox.getY() + searchBox.getHeight() + 8;
        int listW = imageWidth - CONTENT_PADDING * 2;

        if (currentList.isEmpty()) {
            String emptyKey = showOwnedTab
                ? "gui.territory.book.empty.owned"
                : "gui.territory.book.empty.shared";
            if (!searchQuery.isEmpty()) {
                emptyKey = "gui.territory.book.empty.search";
            }
            g.drawCenteredString(this.font, Component.translatable(emptyKey),
                this.leftPos + imageWidth / 2, listY + 40, 0xAAAAAA);
            return;
        }

        int maxVisible = Math.min(MAX_VISIBLE_ROWS, currentList.size());
        for (int i = 0; i < maxVisible; i++) {
            int idx = i + scrollOffset;
            if (idx >= currentList.size()) break;
            String[] entry = currentList.get(idx);
            int rowY = listY + i * ROW_HEIGHT;

            boolean hovered = mouseX >= listX && mouseX <= listX + listW
                && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            g.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT - 2,
                hovered ? 0x44FFFFFF : 0x33FFFFFF);

            // 领地名（白）
            g.drawString(this.font, safe(entry, 1), listX + 5, rowY + 4, 0xFFFFFF);
            // 世界 | 面积 m²（灰）
            String info = safe(entry, 2) + " | " + safe(entry, 3) + " m\u00B2";
            g.drawString(this.font, info, listX + 5, rowY + 15, 0xAAAAAA);

            // 传送按钮（绿）
            int btnX = listX + listW - 44;
            int btnY = rowY + 4;
            int btnW = 38;
            int btnH = ROW_HEIGHT - 8;
            boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHovered ? 0xFF33AA33 : 0xFF228822);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFF55FF55);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFF55FF55);
            Component tpLabel = Component.translatable("gui.territory.book.teleport");
            g.drawString(this.font, tpLabel,
                btnX + (btnW - font.width(tpLabel)) / 2,
                btnY + (btnH - font.lineHeight) / 2, 0xFFFFFF);
        }
    }

    /** 当前标签过滤后的列表。 */
    private List<String[]> getFilteredList() {
        List<String[]> source = showOwnedTab ? menu.getClientOwned() : menu.getClientShared();
        if (searchQuery == null || searchQuery.isEmpty()) {
            return source;
        }
        List<String[]> result = new ArrayList<>();
        for (String[] e : source) {
            if (safe(e, 1).toLowerCase().contains(searchQuery)) {
                result.add(e);
            }
        }
        return result;
    }

    private static String safe(String[] arr, int i) {
        return (i < arr.length) ? arr[i] : "";
    }

    // ----------------------------------------------------------------
    //  鼠标交互
    // ----------------------------------------------------------------
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 标签点击（复用渲染时的横向布局）
            int[][] layout = computeTabLayout();
            int[] xs = layout[0];
            int[] widths = layout[1];
            int tabY = this.topPos - TAB_HEIGHT + 2;
            for (int i = 0; i < TAB_KEYS.length; i++) {
                int tabX = xs[i];
                int tabW = widths[i];
                if (mouseX >= tabX && mouseX <= tabX + tabW
                    && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
                    boolean newTab = (i == 0);
                    if (newTab != showOwnedTab) {
                        showOwnedTab = newTab;
                        this.scrollOffset = 0;
                    }
                    return true;
                }
            }

            // 传送按钮点击
            List<String[]> currentList = getFilteredList();
            int listX = this.leftPos + CONTENT_PADDING;
            int listY = searchBox.getY() + searchBox.getHeight() + 8;
            int listW = imageWidth - CONTENT_PADDING * 2;
            int btnW = 38;
            int btnX = listX + listW - 44;
            int maxVisible = Math.min(MAX_VISIBLE_ROWS, currentList.size());
            for (int i = 0; i < maxVisible; i++) {
                int idx = i + scrollOffset;
                if (idx >= currentList.size()) break;
                int rowY = listY + i * ROW_HEIGHT;
                int btnY = rowY + 4;
                int btnH = ROW_HEIGHT - 8;
                if (mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH) {
                    String[] entry = currentList.get(idx);
                    String uuid = safe(entry, 0);
                    PacketDistributor.sendToServer(new TerritoryTeleportRequestPayload(uuid));
                    Minecraft.getInstance().setScreen(null);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<String[]> currentList = getFilteredList();
        int maxScroll = Math.max(0, currentList.size() - MAX_VISIBLE_ROWS);
        if (scrollY > 0) {
            this.scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (scrollY < 0) {
            this.scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
