package com.frontleaves.mods.territory.defense;

/**
 * 领地权限标志位枚举。
 * <p>
 * 每个标志位归属于一个 {@link FlagCategory}，并标注是否为宏标志（macro）。
 * 宏标志在 {@link com.frontleaves.mods.territory.defense.TerritoryPermissionService}
 * 中控制其子标志的联动行为。
 * <p>
 * 显示名称通过 i18n 键 {@code territory.flag.<name>} 本地化（见 lang/*.json）。
 */
public enum FlagType {

    // ========== BUILD ==========
    build(FlagCategory.BUILD, true),
    destroy(FlagCategory.BUILD, false),
    place(FlagCategory.BUILD, false),
    piston(FlagCategory.BUILD, false),

    // ========== CONTAINER ==========
    container(FlagCategory.CONTAINER, false),

    // ========== INTERACT ==========
    interact(FlagCategory.INTERACT, true),
    button(FlagCategory.INTERACT, false),
    lever(FlagCategory.INTERACT, false),
    door(FlagCategory.INTERACT, false),
    pressure(FlagCategory.INTERACT, false),
    redstone(FlagCategory.INTERACT, false),
    craft(FlagCategory.INTERACT, false),
    bed(FlagCategory.INTERACT, false),

    // ========== ENVIRONMENT ==========
    explosion(FlagCategory.ENVIRONMENT, true),
    tnt(FlagCategory.ENVIRONMENT, false),
    creeper(FlagCategory.ENVIRONMENT, false),
    fire(FlagCategory.ENVIRONMENT, false),
    firespread(FlagCategory.ENVIRONMENT, false),
    flow(FlagCategory.ENVIRONMENT, true),
    waterflow(FlagCategory.ENVIRONMENT, false),
    lavaflow(FlagCategory.ENVIRONMENT, false),
    trample(FlagCategory.ENVIRONMENT, false),
    decay(FlagCategory.ENVIRONMENT, false),

    // ========== ENTITY ==========
    damage(FlagCategory.ENTITY, true),
    animal(FlagCategory.ENTITY, false),
    monster(FlagCategory.ENTITY, false),
    animalkilling(FlagCategory.ENTITY, false),
    mobkilling(FlagCategory.ENTITY, false),
    riding(FlagCategory.ENTITY, false),

    // ========== SPECIAL ==========
    move(FlagCategory.SPECIAL, false),
    pvp(FlagCategory.SPECIAL, false),
    enderpearl(FlagCategory.SPECIAL, false),
    vehicledestroy(FlagCategory.SPECIAL, false),
    itemdrop(FlagCategory.SPECIAL, false),
    itempickup(FlagCategory.SPECIAL, false);

    private final FlagCategory category;
    private final boolean macro;

    FlagType(FlagCategory category, boolean macro) {
        this.category = category;
        this.macro = macro;
    }

    public FlagCategory getCategory() {
        return category;
    }

    /**
     * 获取本标志位对应的 i18n 翻译键。
     * <p>调用方应使用 {@code Component.translatable(flag.getTranslationKey())} 渲染。
     */
    public String getTranslationKey() {
        return "territory.flag." + name();
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
    public static java.util.List<FlagType> getByCategory(FlagCategory category) {
        return java.util.Arrays.stream(values())
                .filter(type -> type.category == category)
                .toList();
    }
}
