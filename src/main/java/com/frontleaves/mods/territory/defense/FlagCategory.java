package com.frontleaves.mods.territory.defense;

/**
 * 领地旗帜分类枚举，用于 GUI 分组显示。
 * <p>
 * 每个分类包含一个中文显示名称和一个 GUI 图标。
 */
public enum FlagCategory {

    BUILD("建筑", "🛠️"),
    CONTAINER("容器", "📦"),
    INTERACT("交互", "✋"),
    ENVIRONMENT("环境", "🌍"),
    ENTITY("实体", "🐾"),
    SPECIAL("特殊", "⚡");

    private final String displayName;
    private final String icon;

    FlagCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
