package com.frontleaves.mods.territory.data;

import com.frontleaves.mods.territory.Territory;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 领地模组掉落表数据生成器 — 方块被破坏时掉落自身
 */
public class ModLootTableProvider extends LootTableProvider {

    public ModLootTableProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, Set.of(), List.of(
                new SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK)
        ), lookupProvider);
    }

    /**
     * 领地模组方块掉落表 — 所有方块掉落自身
     */
    private static class ModBlockLoot extends BlockLootSubProvider {

        private static final Set<Item> EXPLOSION_RESISTANT = Collections.emptySet();

        protected ModBlockLoot(HolderLookup.Provider provider) {
            super(EXPLOSION_RESISTANT, FeatureFlags.REGISTRY.allFlags(), provider);
        }

        @Override
        protected void generate() {
            // 领地台 — 掉落自身
            this.dropSelf(Territory.TERRITORY_TABLE.get());
            // 管理员领地台 — 掉落自身
            this.dropSelf(Territory.ADMIN_TERRITORY_TABLE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            // 只处理本模组注册的方块，避免为其他模组的方块生成掉落表
            return List.<Block>of(
                    Territory.TERRITORY_TABLE.get(),
                    Territory.ADMIN_TERRITORY_TABLE.get()
            );
        }
    }
}
