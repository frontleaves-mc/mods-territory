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
 * 管理员领地选区工具 — 与普通领地工具交互逻辑一致，但：
 * <ul>
 *   <li>无体积限制（服务端跳过校验）</li>
 *   <li>仅 OP（permission level ≥ 2）可使用选区功能</li>
 *   <li>使用独立的 {@link com.frontleaves.mods.territory.client.ClientSelectionState#getAdmin()} 选区状态</li>
 * </ul>
 */
public class AdminTerritoryWandItem extends Item {

    public AdminTerritoryWandItem(Properties properties) {
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
                true,   // isAdminWand
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
                    true,   // isAdminWand
                    player.isShiftKeyDown()
                );
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
