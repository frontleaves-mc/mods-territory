package com.frontleaves.mods.territory.item;

import com.frontleaves.mods.territory.client.TerritoryBookScreen;
import com.frontleaves.mods.territory.network.TerritoryBookOpenPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 领地之书 — 右键打开领地管理 GUI
 */
public class TerritoryBookItem extends Item {

    public TerritoryBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            this.openBookScreen();
            PacketDistributor.sendToServer(new TerritoryBookOpenPayload());
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @OnlyIn(Dist.CLIENT)
    private void openBookScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new TerritoryBookScreen());
    }
}
