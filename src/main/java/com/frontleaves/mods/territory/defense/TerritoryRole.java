package com.frontleaves.mods.territory.defense;

/**
 * 领地角色枚举。
 * <p>
 * 每个角色拥有独立的权限等级，显示名称通过 i18n 键
 * {@code territory.role.<name>} 本地化（见 lang/*.json）。
 * 等级越高的角色，拥有的默认权限越完整。
 */
public enum TerritoryRole {

    OWNER(4),
    ADMIN(3),
    MEMBER(2),
    VISITOR(1);

    private final int level;

    TerritoryRole(int level) {
        this.level = level;
    }

    /**
     * 获取本角色对应的 i18n 翻译键。
     * <p>调用方应使用 {@code Component.translatable(role.getTranslationKey())} 渲染。
     */
    public String getTranslationKey() {
        return "territory.role." + name().toLowerCase();
    }

    public int getLevel() {
        return level;
    }

    /**
     * 判断当前角色权限是否不低于目标角色。
     *
     * @param other 待比较的角色
     * @return 当前角色的等级 ≥ 目标角色时返回 {@code true}
     */
    public boolean isAtLeast(TerritoryRole other) {
        return this.level >= other.level;
    }
}
