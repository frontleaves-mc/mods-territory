package com.frontleaves.mods.territory.data;

import com.frontleaves.mods.territory.Territory;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.minecraft.data.PackOutput;

/**
 * 领地模组语言文件数据生成器 — 中文翻译
 */
public class ModZhCnLanguageProvider extends LanguageProvider {

    public ModZhCnLanguageProvider(PackOutput output) {
        super(output, Territory.MODID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        // 方块名称
        add("block.territory.territory_table", "领地台");
        add("block.territory.admin_territory_table", "管理员领地台");

        // 领地消息
        add("territory.msg.create_success", "领地创建成功！");
        add("territory.msg.create_fail_no_selection", "请先用圈地棒选择区域");
        add("territory.msg.create_fail_overlap", "与已有领地重叠");
        add("territory.msg.create_fail_outside", "领地台不在选区内");
        add("territory.msg.territory_deleted", "领地已删除");
        add("territory.msg.right_click_confirm", "右键领地台以确认创建领地");
    }
}
