package com.frontleaves.mods.territory.data;

import com.frontleaves.mods.territory.Territory;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.minecraft.data.PackOutput;

/**
 * 领地模组语言文件数据生成器 — 英文翻译
 */
public class ModEnUsLanguageProvider extends LanguageProvider {

    public ModEnUsLanguageProvider(PackOutput output) {
        super(output, Territory.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // Block names
        add("block.territory.territory_table", "Territory Table");
        add("block.territory.admin_territory_table", "Admin Territory Table");

        // Territory messages
        add("territory.msg.create_success", "Territory created successfully!");
        add("territory.msg.create_fail_no_selection", "Please select an area with the territory wand first");
        add("territory.msg.create_fail_overlap", "Overlaps with existing territory");
        add("territory.msg.create_fail_outside", "Territory table is not within the selection");
        add("territory.msg.territory_deleted", "Territory deleted");
        add("territory.msg.right_click_confirm", "Right-click the territory table to confirm creation");
    }
}
