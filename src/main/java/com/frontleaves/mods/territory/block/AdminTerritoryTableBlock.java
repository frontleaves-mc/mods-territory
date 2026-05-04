package com.frontleaves.mods.territory.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 管理员领地桌方块 — 无碰撞箱、无遮挡，用于管理端操作。
 */
public class AdminTerritoryTableBlock extends TerritoryTableBlock {

    public AdminTerritoryTableBlock(Properties properties) {
        super(properties.noCollission().noOcclusion());
    }
}
