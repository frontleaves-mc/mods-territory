package com.frontleaves.mods.territory.defense;

import com.frontleaves.mods.territory.storage.TerritoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 领地权限检查服务。
 * <p>
 * 实现四级优先链：个人覆盖 → 角色模板 → 领地全局标志 → 默认拒绝。
 * 同时处理宏标志联动（如 build 被拒绝时自动拒绝 destroy/place），
 * 并提供 Actionbar 拒绝消息发送。
 *
 * @see FlagType#isMacro()
 * @see RoleTemplate#getDefaultFlags(TerritoryRole)
 */
public final class TerritoryPermissionService {

    private TerritoryPermissionService() {
        // 工具类，禁止实例化
    }

    // ============================================================
    // 核心权限检查
    // ============================================================

    /**
     * 检查玩家在指定领地内是否拥有某项权限。
     * <p>
     * 流程概述：
     * <ol>
     *   <li>若领地为 {@code null}（即不在任何领地范围内），直接放行</li>
     *   <li>解析宏标志联动（如 build 被拒绝 → destroy/place 自动拒绝）</li>
     *   <li>沿优先链获取最终权限值</li>
     * </ol>
     *
     * @param player    执行操作的玩家
     * @param territory 领地数据，可为 {@code null}
     * @param flag      待检查的权限标志位
     * @return {@code true} 表示允许，{@code false} 表示拒绝
     */
    public static boolean checkPermission(ServerPlayer player, TerritoryData territory, FlagType flag) {
        // 不在任何领地范围内 → 放行
        if (territory == null) return true;

        // 1. 解析宏标志联动
        FlagType effectiveFlag = resolveMacro(flag, territory);

        // 2. 沿优先链获取最终权限值
        String playerUuid = player.getUUID().toString();
        return getEffectiveFlag(territory, playerUuid, effectiveFlag);
    }

    /**
     * 带坐标的权限检查重载，保留 BlockPos 供未来扩展（如子区域权限）。
     *
     * @param player    执行操作的玩家
     * @param territory 领地数据，可为 {@code null}
     * @param flag      待检查的权限标志位
     * @param pos       操作发生的方块坐标
     * @return {@code true} 表示允许，{@code false} 表示拒绝
     */
    public static boolean checkPermission(ServerPlayer player, TerritoryData territory, FlagType flag, BlockPos pos) {
        // 目前 BlockPos 未参与逻辑，保留接口以供未来子区域权限扩展
        return checkPermission(player, territory, flag);
    }

    // ============================================================
    // 角色解析
    // ============================================================

    /**
     * 获取玩家在指定领地中的角色。
     * <p>
     * 优先级：拥有者 → 管理员 → 成员 → 访客。
     *
     * @param player    目标玩家
     * @param territory 领地数据
     * @return 对应的 {@link TerritoryRole}
     */
    public static TerritoryRole getPlayerRole(ServerPlayer player, TerritoryData territory) {
        return getPlayerRoleByUuid(territory, player.getUUID().toString());
    }

    /**
     * 通过 UUID 字符串获取玩家在领地中的角色（内部辅助方法）。
     */
    private static TerritoryRole getPlayerRoleByUuid(TerritoryData territory, String playerUuid) {
        // 拥有者
        if (territory.ownerUuid().equals(playerUuid)) return TerritoryRole.OWNER;

        // 遍历成员列表
        for (TerritoryData.MemberEntry member : territory.members()) {
            if (member.playerUuid().equals(playerUuid)) {
                String roleStr = member.role();
                if ("admin".equals(roleStr)) return TerritoryRole.ADMIN;
                if ("member".equals(roleStr)) return TerritoryRole.MEMBER;
            }
        }

        // 默认访客
        return TerritoryRole.VISITOR;
    }

    // ============================================================
    // 优先链：个人覆盖 → 角色模板 → 全局标志 → 默认拒绝
    // ============================================================

    /**
     * 沿四级优先链获取最终标志位权限值。
     * <p>
     * 优先级（由高到低）：
     * <ol>
     *   <li><b>个人覆盖</b> — {@code MemberEntry.personalFlags} 中的显式设定</li>
     *   <li><b>角色模板</b> — {@link RoleTemplate#getDefaultFlags(TerritoryRole)}</li>
     *   <li><b>领地全局标志</b> — {@code TerritoryData.flags()} 中的设定</li>
     *   <li><b>默认拒绝</b> — 以上均无匹配时返回 {@code false}</li>
     * </ol>
     *
     * @param territory   领地数据
     * @param playerUuid  玩家 UUID 字符串
     * @param flag        目标标志位
     * @return {@code true} 表示允许，{@code false} 表示拒绝
     */
    public static boolean getEffectiveFlag(TerritoryData territory, String playerUuid, FlagType flag) {
        // ---- Level 1: 个人覆盖 ----
        for (TerritoryData.MemberEntry member : territory.members()) {
            if (member.playerUuid().equals(playerUuid)) {
                Map<String, Boolean> personalFlags = member.personalFlags();
                if (personalFlags != null && personalFlags.containsKey(flag.name())) {
                    return personalFlags.get(flag.name());
                }
            }
        }

        // ---- Level 2: 角色模板 ----
        TerritoryRole role = getPlayerRoleByUuid(territory, playerUuid);
        Map<FlagType, Boolean> roleDefaults = RoleTemplate.getDefaultFlags(role);
        Boolean roleValue = roleDefaults.get(flag);
        if (roleValue != null) return roleValue;

        // ---- Level 3: 领地全局标志 ----
        Object globalValue = territory.flags().get(flag.name());
        if (globalValue instanceof Boolean boolVal) return boolVal;
        if (globalValue instanceof String strVal) {
            return "allow".equalsIgnoreCase(strVal);
        }

        // ---- Level 4: 默认拒绝 ----
        return false;
    }

    // ============================================================
    // 宏标志联动
    // ============================================================

    /**
     * 解析宏标志联动。
     * <p>
     * 若待检查的标志位存在父级宏标志（如 destroy 的父级是 build），
     * 且该宏标志在领地全局标志中被设为拒绝，则返回宏标志本身，
     * 使后续优先链判定直接走宏标志的拒绝路径。
     *
     * @param flag      原始请求标志位
     * @param territory 领地数据
     * @return 实际生效的标志位（可能为宏标志）
     */
    private static FlagType resolveMacro(FlagType flag, TerritoryData territory) {
        // 直接检查的是宏标志本身 → 无需联动
        if (flag.isMacro()) return flag;

        // 查找父级宏标志
        FlagType parent = getMacroParent(flag);
        if (parent == null) return flag;

        // 判断宏标志是否在领地全局标志中被拒绝
        Object macroValue = territory.flags().get(parent.name());
        boolean macroDenied = false;
        if (macroValue instanceof Boolean boolVal) {
            macroDenied = !boolVal;
        } else if (macroValue instanceof String strVal) {
            macroDenied = !"allow".equalsIgnoreCase(strVal);
        }

        if (macroDenied) {
            // 宏标志被拒绝 → 返回宏标志，后续优先链将沿宏标志判定
            return parent;
        }

        return flag;
    }

    /**
     * 获取子标志位的父级宏标志。
     * <p>
     * 映射规则：
     * <ul>
     *   <li>destroy / place / piston → build</li>
     *   <li>button / lever / door / pressure / redstone / craft / bed → interact</li>
     *   <li>tnt / creeper → explosion</li>
     *   <li>waterflow / lavaflow → flow</li>
     *   <li>animal / monster / animalkilling / mobkilling / riding → damage</li>
     * </ul>
     *
     * @param flag 子标志位
     * @return 父级宏标志，若无父级则返回 {@code null}
     */
    private static FlagType getMacroParent(FlagType flag) {
        return switch (flag.getCategory()) {
            case BUILD -> (flag == FlagType.build) ? null : FlagType.build;
            case INTERACT -> (flag == FlagType.interact) ? null : FlagType.interact;
            case ENVIRONMENT -> {
                if (flag == FlagType.tnt || flag == FlagType.creeper) yield FlagType.explosion;
                if (flag == FlagType.waterflow || flag == FlagType.lavaflow) yield FlagType.flow;
                yield null;
            }
            case ENTITY -> (flag == FlagType.damage) ? null : FlagType.damage;
            default -> null;
        };
    }

    // ============================================================
    // 拒绝消息
    // ============================================================

    /**
     * 向玩家发送 Actionbar 形式的拒绝提示消息。
     *
     * @param player  目标玩家
     * @param i18nKey 国际化翻译键（如 {@code "territory.deny.build"}）
     */
    public static void sendDenyMessage(ServerPlayer player, String i18nKey) {
        player.displayClientMessage(
                Component.translatable(i18nKey)
                        .withStyle(ChatFormatting.RED),
                true // actionBar = true
        );
    }
}
