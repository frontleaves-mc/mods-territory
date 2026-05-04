package com.frontleaves.mods.territory.event;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.client.ClientSelectionState;
import com.frontleaves.mods.territory.network.SelectionUpdatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handles wand left-click interactions on the GAME event bus (client-side only).
 * Left-click sets pos1 (first corner), Shift+left-click clears the selection.
 */
@EventBusSubscriber(modid = Territory.MODID, value = Dist.CLIENT)
public class WandInteractionHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack mainHand = event.getEntity().getMainHandItem();
        boolean isRegularWand = mainHand.is(Territory.TERRITORY_WAND.get());
        boolean isAdminWand = mainHand.is(Territory.ADMIN_TERRITORY_WAND.get());

        if (!isRegularWand && !isAdminWand) return;

        event.setCanceled(true);

        BlockPos pos = event.getPos();
        ClientSelectionState state = isAdminWand ? ClientSelectionState.getAdmin() : ClientSelectionState.get();

        if (event.getEntity().isShiftKeyDown()) {
            state.clearSelection();
            event.getEntity().displayClientMessage(
                    Component.translatable("territory.msg.selection_cleared").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        state.setPos1(pos);
        event.getEntity().displayClientMessage(
                Component.translatable("territory.msg.pos1_set", pos.getX(), pos.getY(), pos.getZ())
                        .withStyle(ChatFormatting.GREEN), false);

        if (state.isComplete()) {
            PacketDistributor.sendToServer(
                new SelectionUpdatePayload(
                    state.getPos1(),
                    state.getPos2(),
                    event.getLevel().dimension().location().toString(),
                    isAdminWand
                )
            );
        }
    }
}
