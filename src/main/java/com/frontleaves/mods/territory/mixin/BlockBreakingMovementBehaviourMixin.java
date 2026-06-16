package com.frontleaves.mods.territory.mixin;

import com.frontleaves.mods.territory.defense.FlagType;
import com.frontleaves.mods.territory.defense.TerritoryPermissionService;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * 拦截 Create 模块的方块破坏移动行为，防止装置在领地内未授权破坏方块。
 * <p>
 * 注入 {@code visitNewPosition} 方法，在装置移动到新位置时
 * 检查目标方块是否位于领地范围内，并验证装置拥有者是否拥有破坏权限。
 * <p>
 * <b>兼容性提示</b>：本 Mixin 硬依赖 Create 模组的
 * {@code BlockBreakingMovementBehaviour#visitNewPosition} 与 {@link MovementContext#blockEntityData}
 * 中的 {@code Owner} NBT 约定。经验证兼容 Create 6.0.x；若 Create 跨大版本变更方法签名或 NBT 约定，
 * 需同步更新 {@code @Inject} 的 method 名与 owner UUID 提取逻辑。该 Mixin 仅在 Create 已加载时
 * 由 FML 解析（见 {@code mods.toml} 的 optional 依赖声明），缺失时自动跳过。
 *
 * @author xiao_lfeng
 */
@Mixin(BlockBreakingMovementBehaviour.class)
public abstract class BlockBreakingMovementBehaviourMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Territory/Mixin");

    /**
     * 拦截 visitNewPosition，在装置尝试破坏方块前检查领地权限。
     * <p>
     * 从 {@link MovementContext#blockEntityData} 提取装置拥有者 UUID，
     * 通过 {@link TerritoryDataManager#findTerritoryAt} 判断目标位置是否在领地内，
     * 再调用 {@link TerritoryPermissionService#getEffectiveFlag} 验证破坏权限。
     *
     * @param context 装置移动上下文
     * @param pos     装置到达的新方块位置
     * @param ci      回调信息，用于取消原始方法执行
     */
    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void territory$checkPermissionBeforeBreak(MovementContext context, BlockPos pos, CallbackInfo ci) {
        // 仅在服务端执行
        Level level = context.world;
        if (level == null || level.isClientSide()) return;

        // 从 blockEntityData 提取装置拥有者 UUID
        CompoundTag blockEntityData = context.blockEntityData;
        if (blockEntityData == null) return;

        UUID ownerUuid = null;
        if (blockEntityData.hasUUID("Owner")) {
            ownerUuid = blockEntityData.getUUID("Owner");
        }

        // 无法确定拥有者 → 阻止在领地内破坏（保守策略）
        if (ownerUuid == null) {
            String worldKey = ((ServerLevel) level).dimension().location().toString();
            Optional<TerritoryData> territoryOpt = TerritoryDataManager.getInstance()
                    .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
            if (territoryOpt.isPresent()) {
                LOGGER.debug("Contraption without owner attempted to break block at {} in territory '{}' - denied",
                        pos, territoryOpt.get().name());
                ci.cancel();
            }
            return;
        }

        // 获取世界维度标识
        String worldKey = ((ServerLevel) level).dimension().location().toString();

        // 查找目标位置是否在领地范围内
        Optional<TerritoryData> territoryOpt = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());

        // 不在领地范围内 → 放行
        if (territoryOpt.isEmpty()) return;

        TerritoryData territory = territoryOpt.get();

        // 检查装置拥有者是否拥有破坏权限
        if (!TerritoryPermissionService.getEffectiveFlag(territory, ownerUuid.toString(), FlagType.destroy)) {
            LOGGER.debug("Contraption owner '{}' denied destroy permission in territory '{}' at {}",
                    ownerUuid, territory.name(), pos);
            ci.cancel();
        }
    }
}
