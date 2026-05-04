package com.frontleaves.mods.territory.event;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.block.entity.TerritoryTableBlockEntity;
import com.frontleaves.mods.territory.geometry.AABB;
import com.frontleaves.mods.territory.storage.ServerSelectionCache;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 领地桌事件处理器 — 处理放置、右键确认创建、破坏删除领地。
 */
@EventBusSubscriber(modid = Territory.MODID)
public class TerritoryTableHandler {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;

        BlockState state = event.getPlacedBlock();
        if (!state.is(Territory.TERRITORY_TABLE.get()) && !state.is(Territory.ADMIN_TERRITORY_TABLE.get())) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerSelectionCache.SelectionEntry selection = ServerSelectionCache.get(player.getUUID());

        if (selection == null || !selection.validated()) {
            player.displayClientMessage(
                Component.translatable("territory.msg.create_fail_no_selection").withStyle(ChatFormatting.RED), false);
            return;
        }

        player.displayClientMessage(
            Component.translatable("territory.msg.right_click_confirm").withStyle(ChatFormatting.YELLOW), false);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof TerritoryTableBlockEntity tableBE)) return;

        if (tableBE.getTerritoryUuid() != null) return;

        ServerSelectionCache.SelectionEntry selection = ServerSelectionCache.get(player.getUUID());
        if (selection == null || !selection.validated()) {
            player.displayClientMessage(
                Component.translatable("territory.msg.create_fail_no_selection").withStyle(ChatFormatting.RED), false);
            return;
        }

        String worldKey = ((ServerLevel) player.level()).dimension().location().toString();
        if (!selection.dimensionKey().equals(worldKey)) {
            player.displayClientMessage(
                Component.translatable("territory.msg.create_fail_no_selection").withStyle(ChatFormatting.RED), false);
            return;
        }

        AABB selectionBox = AABB.from(selection.pos1(), selection.pos2());
        BlockPos tablePos = event.getPos();
        if (!selectionBox.contains(tablePos.getX(), tablePos.getY(), tablePos.getZ())) {
            player.displayClientMessage(
                Component.translatable("territory.msg.create_fail_outside").withStyle(ChatFormatting.RED), false);
            return;
        }

        TerritoryDataManager manager = TerritoryDataManager.getInstance();
        if (manager.checkOverlap(worldKey, selectionBox.minX(), selectionBox.minY(), selectionBox.minZ(),
                selectionBox.maxX(), selectionBox.maxY(), selectionBox.maxZ())) {
            player.displayClientMessage(
                Component.translatable("territory.msg.create_fail_overlap").withStyle(ChatFormatting.RED), false);
            return;
        }

        String ownerUuid = player.getUUID().toString();
        String territoryName = player.getName().getString() + "_领地" + (manager.getTerritoriesByOwner(ownerUuid).size() + 1);
        TerritoryData data = TerritoryData.create(ownerUuid, territoryName, worldKey,
            selectionBox.minX(), selectionBox.minY(), selectionBox.minZ(),
            selectionBox.maxX(), selectionBox.maxY(), selectionBox.maxZ());

        manager.createTerritory(data);
        tableBE.setTerritoryUuid(data.uuid());

        player.displayClientMessage(
            Component.translatable("territory.msg.create_success").withStyle(ChatFormatting.GREEN), false);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof TerritoryTableBlockEntity tableBE)) return;

        String territoryUuid = tableBE.getTerritoryUuid();
        if (territoryUuid == null) return;

        TerritoryDataManager manager = TerritoryDataManager.getInstance();
        for (TerritoryData td : manager.getAllTerritories()) {
            if (td.uuid().equals(territoryUuid)) {
                manager.deleteTerritory(td.ownerUuid(), territoryUuid);
                if (event.getPlayer() instanceof ServerPlayer player) {
                    player.displayClientMessage(
                        Component.translatable("territory.msg.territory_deleted").withStyle(ChatFormatting.YELLOW), false);
                }
                break;
            }
        }
    }
}
