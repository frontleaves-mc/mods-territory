package com.frontleaves.mods.territory.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 领地模组数据生成入口 — 注册所有数据提供者
 */
public class ModDataGen {

    public static void generate(GatherDataEvent event) {
        // 服务端数据：配方、掉落表
        event.createProvider((output, lookupProvider) -> new ModRecipeProvider(output, lookupProvider));
        event.createProvider((output, lookupProvider) -> new ModLootTableProvider(output, lookupProvider));

        // 注：语言文件（中英文）采用手写 lang/*.json 维护，不走 datagen。
        // 历史上 datagen 仅生成 8 个键的残缺文件，构建时因 duplicatesStrategy=INCLUDE
        // 覆盖了手写的完整 lang 文件，导致游戏内大面积显示键名。详见 build.gradle。
    }
}
