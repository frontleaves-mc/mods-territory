package com.frontleaves.mods.territory.defense;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 角色权限模板。
 * <p>
 * 为每个 {@link TerritoryRole} 提供默认的权限标志位配置，
 * 参见设计文档 §1.2 角色模板表。
 */
public final class RoleTemplate {

    private RoleTemplate() {
        // 工具类，禁止实例化
    }

    /**
     * 获取指定角色的默认权限标志位配置。
     *
     * @param role 目标角色
     * @return 标志位名称 → 是否允许的映射
     */
    public static Map<FlagType, Boolean> getDefaultFlags(TerritoryRole role) {
        Map<FlagType, Boolean> flags = new EnumMap<>(FlagType.class);

        // 先将所有标志位设为 false，再按角色逐项开启
        for (FlagType flag : FlagType.values()) {
            flags.put(flag, false);
        }

        switch (role) {
            case OWNER:
                applyAllTrue(flags);
                break;
            case ADMIN:
                applyAdminDefaults(flags);
                break;
            case MEMBER:
                applyMemberDefaults(flags);
                break;
            case VISITOR:
                applyVisitorDefaults(flags);
                break;
        }

        return flags;
    }

    // ---- 角色默认值 ----

    private static void applyAllTrue(Map<FlagType, Boolean> flags) {
        for (FlagType flag : FlagType.values()) {
            flags.put(flag, true);
        }
    }

    private static void applyAdminDefaults(Map<FlagType, Boolean> flags) {
        // BUILD(全部) → true
        setCategoryTrue(flags, FlagCategory.BUILD);
        // CONTAINER → true
        setCategoryTrue(flags, FlagCategory.CONTAINER);
        // INTERACT(全部) → true
        setCategoryTrue(flags, FlagCategory.INTERACT);
        // ENVIRONMENT(全部) → false (已默认)
        // ENTITY(全部) → true
        setCategoryTrue(flags, FlagCategory.ENTITY);
        // SPECIAL: move/pvp/enderpearl → true, 其余 false
        setFlagsTrue(flags, Set.of(FlagType.move, FlagType.pvp, FlagType.enderpearl));
    }

    private static void applyMemberDefaults(Map<FlagType, Boolean> flags) {
        // BUILD: build/destroy/place → true, piston → false
        setFlagsTrue(flags, Set.of(FlagType.build, FlagType.destroy, FlagType.place));
        // CONTAINER → false (已默认)
        // INTERACT(全部) → true
        setCategoryTrue(flags, FlagCategory.INTERACT);
        // ENVIRONMENT(全部) → false (已默认)
        // ENTITY(全部) → false (已默认)
        // SPECIAL: move → true, itemdrop/itempickup → true
        setFlagsTrue(flags, Set.of(FlagType.move, FlagType.itemdrop, FlagType.itempickup));
    }

    private static void applyVisitorDefaults(Map<FlagType, Boolean> flags) {
        // ONLY move → true
        flags.put(FlagType.move, true);
    }

    // ---- 工具方法 ----

    private static void setCategoryTrue(Map<FlagType, Boolean> flags, FlagCategory category) {
        for (FlagType flag : FlagType.getByCategory(category)) {
            flags.put(flag, true);
        }
    }

    private static void setFlagsTrue(Map<FlagType, Boolean> flags, Set<FlagType> targetFlags) {
        for (FlagType flag : targetFlags) {
            flags.put(flag, true);
        }
    }
}
