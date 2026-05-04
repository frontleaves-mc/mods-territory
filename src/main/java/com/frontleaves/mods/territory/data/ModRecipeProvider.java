package com.frontleaves.mods.territory.data;

import com.frontleaves.mods.territory.Territory;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * 领地模组配方数据生成器 — 生成领地选区工具与领地台的合成配方
 */
public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // 领地选区工具：纵向排列 — 纸(上)、绿宝石(中)、木棍(下)
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Territory.TERRITORY_WAND.get())
                .pattern("P")
                .pattern("E")
                .pattern("S")
                .define('P', Items.PAPER)
                .define('E', Items.EMERALD)
                .define('S', Items.STICK)
                .unlockedBy("has_emerald", has(Items.EMERALD))
                .save(output);

        // 领地台：无序合成 — 讲台 + 绿宝石
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, Territory.TERRITORY_TABLE.get())
                .requires(Items.LECTERN)
                .requires(Items.EMERALD)
                .unlockedBy("has_lectern", has(Items.LECTERN))
                .save(output);

        // 注意：管理员工具与管理员领地台不提供合成配方
    }
}
