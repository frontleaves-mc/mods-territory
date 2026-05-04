package com.frontleaves.mods.territory.item;

import com.frontleaves.mods.territory.client.ClientSelectionState;
import com.frontleaves.mods.territory.network.SelectionUpdatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 领地选区工具 — 右键设置 pos2（第二个角），Shift+右键清除选区。
 */
public class TerritoryWandItem extends Item {

    public TerritoryWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            Player player = context.getPlayer();
            if (player == null) return InteractionResult.PASS;

            ClientSelectionState state = ClientSelectionState.get();

            if (player.isShiftKeyDown()) {
                state.clearSelection();
                player.displayClientMessage(
                        Component.translatable("territory.msg.selection_cleared").withStyle(ChatFormatting.YELLOW), false);
                return InteractionResult.SUCCESS;
            }

            var pos = context.getClickedPos();
            String error = state.setPos2(pos);
            if (error != null) {
                player.displayClientMessage(
                        Component.translatable(error).withStyle(ChatFormatting.RED), false);
                return InteractionResult.FAIL;
            }

            player.displayClientMessage(
                    Component.translatable("territory.msg.pos2_set", pos.getX(), pos.getY(), pos.getZ())
                            .withStyle(ChatFormatting.GREEN), false);

            PacketDistributor.sendToServer(
                new SelectionUpdatePayload(
                    state.getPos1(),
                    state.getPos2(),
                    context.getLevel().dimension().location().toString(),
                    false
                )
            );
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
