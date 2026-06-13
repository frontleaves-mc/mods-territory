package com.frontleaves.mods.territory.defense;

/**
 * 领地角色枚举。
 * <p>
 * 每个角色拥有独立的显示名称和权限等级。
 * 等级越高的角色，拥有的默认权限越完整。
 */
public enum TerritoryRole {

    OWNER("拥有者", 4),
    ADMIN("管理员", 3),
    MEMBER("成员", 2),
    VISITOR("访客", 1);

    private final String displayName;
    private final int level;

    TerritoryRole(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
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
