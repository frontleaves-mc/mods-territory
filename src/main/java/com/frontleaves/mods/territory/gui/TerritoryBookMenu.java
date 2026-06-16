package com.frontleaves.mods.territory.gui;

import com.frontleaves.mods.territory.Territory;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 领地之书 GUI — 服务端 Container Menu（无物品槽）。
 * <p>
 * 持有玩家视角的「我的领地」(owned) 与「共享领地」(shared) 列表，
 * 通过 {@link TerritoryGuiSyncPayload} 以 {@code BOOK_LIST} pageType 推送给客户端。
 * <p>
 * 列表条目扁平化编码为 String，避免 sync payload codec 不支持的嵌套类型：
 * 每个 entry 序列化为 {@code uuid\u241Fname\u241FworldKey\u241Farea\u241FhasSpawn}。
 *
 * @see com.frontleaves.mods.territory.client.TerritoryBookScreen 客户端渲染
 */
public class TerritoryBookMenu extends AbstractContainerMenu {

    /** 条目字段分隔符（Unicode Group Separator，不会出现在领地名/世界键中）。 */
    private static final String ENTRY_DELIM = "\u241F";

    // ===== 状态 =====
    private final ServerPlayer serverPlayer;
    private final String playerUuid;

    // ===== 客户端缓冲数据（实际数据由 sync payload 填充） =====
    private List<String[]> clientOwned = new ArrayList<>();
    private List<String[]> clientShared = new ArrayList<>();

    // ----------------------------------------------------------------
    //  MenuType 工厂构造器（客户端侧，由 MenuType 注册 lambda 调用）
    // ----------------------------------------------------------------
    public TerritoryBookMenu(int containerId, Inventory playerInventory) {
        super(getMenuType(), containerId);
        this.serverPlayer = null;
        this.playerUuid = null;
    }

    // ----------------------------------------------------------------
    //  客户端构造器（从 buffer 读取占位，实际数据由 sync 填充）
    // ----------------------------------------------------------------
    public TerritoryBookMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        super(getMenuType(), containerId);
        this.serverPlayer = null;
        this.playerUuid = null;
        buffer.readUtf();  // 保留读取维持 buf 位置
    }

    // ----------------------------------------------------------------
    //  服务端构造器（持有玩家引用，打开后立即 sync）
    // ----------------------------------------------------------------
    public TerritoryBookMenu(int containerId, Inventory playerInventory, ServerPlayer serverPlayer) {
        super(getMenuType(), containerId);
        this.serverPlayer = serverPlayer;
        this.playerUuid = serverPlayer.getUUID().toString();
    }

    /**
     * 获取本 Menu 的注册类型。直接引用 {@link Territory#TERRITORY_BOOK_MENU}，
     * 由 DeferredHolder 保证在任意物理端注册完成后均可获取，避免静态字段注入。
     */
    private static MenuType<TerritoryBookMenu> getMenuType() {
        return Territory.TERRITORY_BOOK_MENU.get();
    }

    /** 获取客户端侧缓存的 owned 列表（每项为 [uuid,name,worldKey,area,hasSpawn]）。 */
    public List<String[]> getClientOwned() { return clientOwned; }

    /** 获取客户端侧缓存的 shared 列表。 */
    public List<String[]> getClientShared() { return clientShared; }

    /** 接收服务端推送的列表数据（由 handleGuiSync 调用）。 */
    public void receiveBookList(List<String[]> owned, List<String[]> shared) {
        this.clientOwned = owned != null ? owned : new ArrayList<>();
        this.clientShared = shared != null ? shared : new ArrayList<>();
    }

    // ----------------------------------------------------------------
    //  数据同步 S→C — 列表扁平化编码后推送
    // ----------------------------------------------------------------

    /**
     * 构建列表数据并推送 BOOK_LIST sync payload。
     * <p>列表条目序列化为单行字符串，客户端解析回 String[]。
     */
    public void syncBookList() {
        if (serverPlayer == null || playerUuid == null) return;

        var manager = TerritoryDataManager.getInstance();
        List<String> ownedStr = new ArrayList<>();
        for (TerritoryData td : manager.findTerritoriesByOwner(playerUuid)) {
            ownedStr.add(encodeEntry(td));
        }
        List<String> sharedStr = new ArrayList<>();
        for (TerritoryData td : manager.findTerritoriesByMember(playerUuid)) {
            sharedStr.add(encodeEntry(td));
        }

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("owned", String.join("\n", ownedStr));
        pageData.put("shared", String.join("\n", sharedStr));

        PacketDistributor.sendToPlayer(serverPlayer,
            new TerritoryGuiSyncPayload("book", "BOOK_LIST", pageData));
    }

    /** 将 TerritoryData 编码为单行字符串。 */
    private static String encodeEntry(TerritoryData td) {
        return td.uuid() + ENTRY_DELIM + td.name() + ENTRY_DELIM
            + td.worldKey() + ENTRY_DELIM
            + TerritoryDataManager.calculateArea(td) + ENTRY_DELIM
            + td.hasSpawn();
    }

    /** 将单行字符串解码为 String[]（客户端用）。 */
    public static String[] decodeEntry(String line) {
        return line.split(ENTRY_DELIM, 5);
    }

    // ----------------------------------------------------------------
    //  AbstractContainerMenu 必须实现
    // ----------------------------------------------------------------
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
