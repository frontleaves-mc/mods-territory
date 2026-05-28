package com.frontleaves.mods.territory.item;

import com.frontleaves.mods.territory.event.WandInteractionHandler;
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

            WandInteractionHandler.handleWandInteraction(
                player,
                context.getClickedPos(),
                false,  // isAdminWand
                player.isShiftKeyDown()
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
                WandInteractionHandler.handleWandInteraction(
                    player,
                    blockHit.getBlockPos(),
                    false,  // isAdminWand
                    player.isShiftKeyDown()
                );
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
