package com.frontleaves.mods.territory.defense;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领地权限标志位枚举。
 * <p>
 * 每个标志位归属于一个 {@link FlagCategory}，并标注是否为宏标志（macro）。
 * 宏标志在 {@link com.frontleaves.mods.territory.defense.PermissionService} 中
 * 控制其子标志的联动行为。
 */
public enum FlagType {

    // ========== BUILD ==========
    build(FlagCategory.BUILD, "建造/破坏", true),
    destroy(FlagCategory.BUILD, "破坏", false),
    place(FlagCategory.BUILD, "放置", false),
    piston(FlagCategory.BUILD, "活塞", false),

    // ========== CONTAINER ==========
    container(FlagCategory.CONTAINER, "容器访问", false),

    // ========== INTERACT ==========
    interact(FlagCategory.INTERACT, "交互", true),
    button(FlagCategory.INTERACT, "按钮", false),
    lever(FlagCategory.INTERACT, "拉杆", false),
    door(FlagCategory.INTERACT, "门", false),
    pressure(FlagCategory.INTERACT, "压力板", false),
    redstone(FlagCategory.INTERACT, "红石", false),
    craft(FlagCategory.INTERACT, "合成", false),
    bed(FlagCategory.INTERACT, "床", false),

    // ========== ENVIRONMENT ==========
    explosion(FlagCategory.ENVIRONMENT, "爆炸", true),
    tnt(FlagCategory.ENVIRONMENT, "TNT", false),
    creeper(FlagCategory.ENVIRONMENT, "苦力怕", false),
    fire(FlagCategory.ENVIRONMENT, "点火", false),
    firespread(FlagCategory.ENVIRONMENT, "火焰蔓延", false),
    flow(FlagCategory.ENVIRONMENT, "液体流动", true),
    waterflow(FlagCategory.ENVIRONMENT, "水流", false),
    lavaflow(FlagCategory.ENVIRONMENT, "岩浆流", false),
    trample(FlagCategory.ENVIRONMENT, "踩踏", false),
    decay(FlagCategory.ENVIRONMENT, "腐烂", false),

    // ========== ENTITY ==========
    damage(FlagCategory.ENTITY, "伤害", true),
    animal(FlagCategory.ENTITY, "动物生成", false),
    monster(FlagCategory.ENTITY, "怪物生成", false),
    animalkilling(FlagCategory.ENTITY, "杀动物", false),
    mobkilling(FlagCategory.ENTITY, "杀怪物", false),
    riding(FlagCategory.ENTITY, "骑乘", false),

    // ========== SPECIAL ==========
    move(FlagCategory.SPECIAL, "移动", false),
    pvp(FlagCategory.SPECIAL, "PVP", false),
    enderpearl(FlagCategory.SPECIAL, "末影珍珠", false),
    vehicledestroy(FlagCategory.SPECIAL, "破坏载具", false),
    itemdrop(FlagCategory.SPECIAL, "丢弃物品", false),
    itempickup(FlagCategory.SPECIAL, "拾取物品", false);

    private static final Set<String> MACRO_FLAGS = Set.of(
            "build", "interact", "explosion", "flow", "damage"
    );

    private final FlagCategory category;
    private final String displayName;
    private final boolean macro;

    FlagType(FlagCategory category, String displayName, boolean macro) {
        this.category = category;
        this.displayName = displayName;
        this.macro = macro;
    }

    public FlagCategory getCategory() {
        return category;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 是否为宏标志。宏标志控制其子标志的联动行为。
     */
    public boolean isMacro() {
        return macro;
    }

    /**
     * 获取指定分类下的所有标志位。
     *
     * @param category 目标分类
     * @return 属于该分类的标志位列表
     */
    public static List<FlagType> getByCategory(FlagCategory category) {
        return Arrays.stream(values())
                .filter(type -> type.category == category)
                .collect(Collectors.toList());
    }
}
