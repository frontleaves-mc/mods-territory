package com.frontleaves.mods.territory.item;

import com.frontleaves.mods.territory.network.TerritoryBookOpenPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 领地之书 — 右键打开领地管理 GUI。
 * <p>
 * 客户端仅发送打开请求，Screen 由服务端 {@code openMenu} 通过
 * {@link com.frontleaves.mods.territory.gui.TerritoryBookMenu} 注册的工厂自动打开。
 */
public class TerritoryBookItem extends Item {

    public TerritoryBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // 仅发送打开请求；Screen 由服务端 openMenu 自动触发客户端打开
            PacketDistributor.sendToServer(new TerritoryBookOpenPayload());
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
