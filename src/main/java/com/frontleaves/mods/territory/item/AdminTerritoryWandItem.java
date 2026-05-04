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
 * 管理员领地选区工具 — 与普通领地工具交互逻辑一致，但：
 * <ul>
 *   <li>无体积限制（服务端跳过校验）</li>
 *   <li>仅 OP（permission level ≥ 2）可使用选区功能</li>
 *   <li>使用独立的 {@link ClientSelectionState#getAdmin()} 选区状态</li>
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

            ClientSelectionState state = ClientSelectionState.getAdmin();

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
                    true
                )
            );
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
