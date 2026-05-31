package com.frontleaves.mods.territory.event;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.client.ClientSelectionState;
import com.frontleaves.mods.territory.network.SelectionUpdatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handles wand left-click interactions on the GAME event bus (client-side only).
 * Left-click sets pos1 (first corner), Shift+left-click clears the selection.
 */
@EventBusSubscriber(modid = Territory.MODID, value = Dist.CLIENT)
public class WandInteractionHandler {

    public static void handleWandInteraction(Player player, BlockPos pos, boolean isAdminWand, boolean isShiftDown) {
        ClientSelectionState state = isAdminWand ? ClientSelectionState.getAdmin() : ClientSelectionState.get();

        if (isShiftDown) {
            state.clearSelection();
            player.displayClientMessage(
                Component.translatable("territory.msg.selection_cleared").withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        if (state.getPos1() == null) {
            state.setPos1(pos);
            player.displayClientMessage(
                Component.translatable("territory.msg.pos1_set", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN), false);
            return;
        }

        if (state.getPos2() == null) {
            state.setPos2(pos);
            player.displayClientMessage(
                Component.translatable("territory.msg.pos2_set", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN), false);

            PacketDistributor.sendToServer(
                new SelectionUpdatePayload(
                    state.getPos1(),
                    state.getPos2(),
                    player.level().dimension().location().toString(),
                    isAdminWand
                )
            );
        } else {
            state.setPos2(pos);
            player.displayClientMessage(
                Component.translatable("territory.msg.pos2_updated", pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.GREEN), false);

            PacketDistributor.sendToServer(
                new SelectionUpdatePayload(
                    state.getPos1(),
                    state.getPos2(),
                    player.level().dimension().location().toString(),
                    isAdminWand
                )
            );
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean mainHandIsWand = mainHand.is(Territory.TERRITORY_WAND.get()) || mainHand.is(Territory.ADMIN_TERRITORY_WAND.get());
        boolean offHandIsWand = offHand.is(Territory.TERRITORY_WAND.get()) || offHand.is(Territory.ADMIN_TERRITORY_WAND.get());
        if (!mainHandIsWand && !offHandIsWand) return;

        if (!player.isShiftKeyDown()) return;

        event.setCanceled(true);

        boolean isAdminWand = mainHandIsWand ? mainHand.is(Territory.ADMIN_TERRITORY_WAND.get()) : offHand.is(Territory.ADMIN_TERRITORY_WAND.get());
        ClientSelectionState state = isAdminWand ? ClientSelectionState.getAdmin() : ClientSelectionState.get();

        if (state.getPos1() == null || state.getPos2() == null) return;

        float yaw = player.getYRot();
        while (yaw < 0) yaw += 360;
        while (yaw >= 360) yaw -= 360;

        int axisIndex = Math.round(yaw / 90f) % 4;

        double scrollDelta = event.getScrollDeltaY();
        if (scrollDelta == 0) return;
        boolean expand = scrollDelta > 0;

        BlockPos pos1 = state.getPos1();
        BlockPos pos2 = state.getPos2();

        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        switch (axisIndex) {
            case 0:
                if (expand) {
                    maxZ++;
                } else {
                    if (maxZ > minZ) maxZ--;
                }
                break;
            case 1:
                if (expand) {
                    minX--;
                } else {
                    if (maxX > minX) minX++;
                }
                break;
            case 2:
                if (expand) {
                    minZ--;
                } else {
                    if (maxZ > minZ) minZ++;
                }
                break;
            case 3:
                if (expand) {
                    maxX++;
                } else {
                    if (maxX > minX) maxX--;
                }
                break;
        }

        BlockPos newPos1 = new BlockPos(
            pos1.getX() <= pos2.getX() ? minX : maxX,
            pos1.getY() <= pos2.getY() ? minY : maxY,
            pos1.getZ() <= pos2.getZ() ? minZ : maxZ
        );
        BlockPos newPos2 = new BlockPos(
            pos1.getX() <= pos2.getX() ? maxX : minX,
            pos1.getY() <= pos2.getY() ? maxY : minY,
            pos1.getZ() <= pos2.getZ() ? maxZ : minZ
        );

        state.setPos1(newPos1);
        state.setPos2(newPos2);

        PacketDistributor.sendToServer(
            new SelectionUpdatePayload(
                state.getPos1(),
                state.getPos2(),
                player.level().dimension().location().toString(),
                isAdminWand
            )
        );

        player.displayClientMessage(
            Component.translatable(
                expand ? "territory.msg.selection_expanded" : "territory.msg.selection_shrunk",
                switch (axisIndex) {
                    case 0 -> "+Z";
                    case 1 -> "-X";
                    case 2 -> "-Z";
                    case 3 -> "+X";
                    default -> "?";
                }
            ).withStyle(ChatFormatting.GREEN), false);
    }
}
