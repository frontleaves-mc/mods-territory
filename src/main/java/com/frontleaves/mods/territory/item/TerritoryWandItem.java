package com.frontleaves.mods.territory.item;

import com.frontleaves.mods.territory.client.ClientSelectionState;
import com.frontleaves.mods.territory.event.WandInteractionHandler;
import com.frontleaves.mods.territory.network.TerritoryWandSpawnSetPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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

            if (player.isShiftKeyDown()) {
                return this.handleShiftRightClick(player, context.getClickedPos(), false);
            }

            WandInteractionHandler.handleWandInteraction(
                player,
                context.getClickedPos(),
                false,  // isAdminWand
                false
            );
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            var hitResult = player.pick(player.blockInteractionRange(), 0f, false);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                var blockHit = (BlockHitResult) hitResult;
                if (player.isShiftKeyDown()) {
                    var result = this.handleShiftRightClick(player, blockHit.getBlockPos(), false);
                    return result == InteractionResult.SUCCESS
                        ? InteractionResultHolder.success(player.getItemInHand(hand))
                        : InteractionResultHolder.pass(player.getItemInHand(hand));
                }
                WandInteractionHandler.handleWandInteraction(
                    player,
                    blockHit.getBlockPos(),
                    false,  // isAdminWand
                    false
                );
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @OnlyIn(Dist.CLIENT)
    private InteractionResult handleShiftRightClick(Player player, net.minecraft.core.BlockPos pos, boolean isAdminWand) {
        var state = isAdminWand ? ClientSelectionState.getAdmin() : ClientSelectionState.get();
        if (state.getPos1() != null) {
            WandInteractionHandler.handleWandInteraction(player, pos, isAdminWand, true);
        } else {
            PacketDistributor.sendToServer(new TerritoryWandSpawnSetPayload(pos, isAdminWand));
        }
        return InteractionResult.SUCCESS;
    }
}
