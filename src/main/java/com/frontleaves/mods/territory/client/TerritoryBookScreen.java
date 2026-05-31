package com.frontleaves.mods.territory.client;

import com.frontleaves.mods.territory.network.TerritoryListResponsePayload.TerritoryEntry;
import com.frontleaves.mods.territory.network.TerritoryTeleportRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TerritoryBookScreen extends Screen {

    private List<TerritoryEntry> ownedList;
    private List<TerritoryEntry> sharedList;
    private List<TerritoryEntry> filteredOwned;
    private List<TerritoryEntry> filteredShared;
    private boolean showOwnedTab;
    private EditBox searchBox;
    private String searchQuery;

    public TerritoryBookScreen() {
        super(Component.translatable("gui.territory.book.title"));
        ownedList = new ArrayList<>();
        sharedList = new ArrayList<>();
        filteredOwned = new ArrayList<>();
        filteredShared = new ArrayList<>();
        showOwnedTab = true;
        searchQuery = "";
    }

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(this.font, this.width / 2 - 100, 50, 200, 20, Component.literal(""));
        searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchBox);

        int tabY = 80;
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.territory.book.tab.owned"),
            btn -> {
                showOwnedTab = true;
                this.applyFilter();
            }
        ).bounds(this.width / 2 - 110, tabY, 100, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.territory.book.tab.shared"),
            btn -> {
                showOwnedTab = false;
                this.applyFilter();
            }
        ).bounds(this.width / 2 + 10, tabY, 100, 20).build());

        this.applyFilter();
    }

    public void setTerritoryData(List<TerritoryEntry> owned, List<TerritoryEntry> shared) {
        ownedList = owned != null ? new ArrayList<>(owned) : new ArrayList<>();
        sharedList = shared != null ? new ArrayList<>(shared) : new ArrayList<>();
        this.applyFilter();
    }

    private void onSearchChanged(String query) {
        searchQuery = query.toLowerCase();
        this.applyFilter();
    }

    private void applyFilter() {
        filteredOwned = filterList(ownedList);
        filteredShared = filterList(sharedList);
    }

    private List<TerritoryEntry> filterList(List<TerritoryEntry> list) {
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new ArrayList<>(list);
        }
        List<TerritoryEntry> result = new ArrayList<>();
        for (TerritoryEntry entry : list) {
            if (entry.name().toLowerCase().contains(searchQuery)) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.territory.book.search_hint"),
                searchBox.getX() + 5, searchBox.getY() + 6, 0x808080);
        }

        List<TerritoryEntry> currentList = showOwnedTab ? filteredOwned : filteredShared;

        int listX = this.width / 2 - 150;
        int listY = 110;
        int rowHeight = 25;
        int maxVisible = 10;

        if (currentList.isEmpty()) {
            String emptyKey = showOwnedTab
                ? "gui.territory.book.empty.owned"
                : "gui.territory.book.empty.shared";
            if (!searchBox.getValue().isEmpty()) {
                emptyKey = "gui.territory.book.empty.search";
            }
            guiGraphics.drawCenteredString(this.font,
                Component.translatable(emptyKey), this.width / 2, listY + 50, 0xAAAAAA);
        } else {
            int panelX = listX - 5;
            int panelY = listY - 5;
            int panelW = 310;
            int panelH = maxVisible * rowHeight + 10;
            guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0000000);

            for (int i = 0; i < currentList.size() && i < maxVisible; i++) {
                TerritoryEntry entry = currentList.get(i);
                int y = listY + i * rowHeight;

                boolean hovered = mouseX >= listX && mouseX <= listX + 300
                    && mouseY >= y && mouseY <= y + rowHeight - 2;
                int bgColor = hovered ? 0x44FFFFFF : 0x33FFFFFF;
                guiGraphics.fill(listX, y, listX + 300, y + rowHeight - 2, bgColor);

                guiGraphics.drawString(this.font, entry.name(), listX + 5, y + 5, 0xFFFFFF);

                String info = entry.worldKey() + " | " + entry.area() + " m\u00B2";
                guiGraphics.drawString(this.font, info, listX + 5, y + 15, 0xAAAAAA);

                int btnX = listX + 260;
                int btnY = y + 4;
                int btnW = 40;
                int btnH = rowHeight - 8;
                boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                int btnBgColor = btnHovered ? 0xFF33AA33 : 0xFF228822;
                guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBgColor);
                guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFF55FF55);
                guiGraphics.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFF55FF55);
                guiGraphics.drawString(this.font, "\u4F20", btnX + 8, btnY + 4, 0xFFFFFF);
            }
        }

        String stats = Component.translatable("gui.territory.book.stats", currentList.size()).getString();
        guiGraphics.drawCenteredString(this.font, stats, this.width / 2, this.height - 30, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<TerritoryEntry> currentList = showOwnedTab ? filteredOwned : filteredShared;
            int listX = this.width / 2 - 150;
            int listY = 110;
            int rowHeight = 25;
            int maxVisible = 10;

            for (int i = 0; i < currentList.size() && i < maxVisible; i++) {
                int y = listY + i * rowHeight;
                if (mouseX >= listX + 260 && mouseX <= listX + 300
                    && mouseY >= y + 4 && mouseY <= y + rowHeight - 4) {
                    TerritoryEntry entry = currentList.get(i);
                    PacketDistributor.sendToServer(new TerritoryTeleportRequestPayload(entry.uuid()));
                    Minecraft.getInstance().setScreen(null);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
