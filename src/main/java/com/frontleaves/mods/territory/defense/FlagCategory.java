package com.frontleaves.mods.territory.defense;

/**
 * 领地旗帜分类枚举，用于 GUI 分组显示。
 * <p>
 * 分类名称通过 i18n 键 {@code territory.flag.category.<name>} 本地化（见 lang/*.json）。
 * Emoji 图标为纯装饰，无需翻译。
 */
public enum FlagCategory {

    BUILD("🛠️"),
    CONTAINER("📦"),
    INTERACT("✋"),
    ENVIRONMENT("🌍"),
    ENTITY("🐾"),
    SPECIAL("⚡");

    private final String icon;

    FlagCategory(String icon) {
        this.icon = icon;
    }

    /**
     * 获取本分类对应的 i18n 翻译键。
     * <p>调用方应使用 {@code Component.translatable(cat.getTranslationKey())} 渲染。
     */
    public String getTranslationKey() {
        return "territory.flag.category." + name().toLowerCase();
    }

    /** 获取 GUI 图标（Emoji，无需 i18n）。 */
    public String getIcon() {
        return icon;
    }
}
