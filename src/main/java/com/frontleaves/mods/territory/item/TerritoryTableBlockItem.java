package com.frontleaves.mods.territory.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 领地桌方块物品 — 放置时自动面向玩家水平朝向的相反方向（像讲台一样）。
 */
public class TerritoryTableBlockItem extends BlockItem {

    public TerritoryTableBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return context.getLevel().setBlock(
                context.getClickedPos(),
                state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing),
                3
        );
    }
}
