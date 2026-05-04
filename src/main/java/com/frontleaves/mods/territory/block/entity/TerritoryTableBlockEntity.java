package com.frontleaves.mods.territory.block.entity;

import com.frontleaves.mods.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 领地桌方块实体 — 存储关联的领地 UUID。
 */
public class TerritoryTableBlockEntity extends BlockEntity {

    @Nullable
    private String territoryUuid;

    public TerritoryTableBlockEntity(BlockPos pos, BlockState state) {
        super(Territory.TERRITORY_TABLE_ENTITY.get(), pos, state);
    }

    @Nullable
    public String getTerritoryUuid() {
        return territoryUuid;
    }

    public void setTerritoryUuid(@Nullable String territoryUuid) {
        this.territoryUuid = territoryUuid;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.territoryUuid != null) {
            tag.putString("TerritoryUuid", this.territoryUuid);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.territoryUuid = tag.contains("TerritoryUuid") ? tag.getString("TerritoryUuid") : null;
    }
}
