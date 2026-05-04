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

        // 客户端数据：语言文件（中英文）
        event.createProvider(ModZhCnLanguageProvider::new);
        event.createProvider(ModEnUsLanguageProvider::new);
    }
}
