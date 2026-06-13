package com.frontleaves.mods.territory.mixin;

import com.frontleaves.mods.territory.defense.FlagType;
import com.frontleaves.mods.territory.defense.TerritoryPermissionService;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

/**
 * Mixin for Create's {@link ContraptionCollider} to prevent contraptions
 * from destroying blocks inside protected territories.
 * <p>
 * Targets {@code collideBlocks} — the method responsible for block-level
 * collision processing. Also covers Aeronautics mod contraptions since
 * they extend Create's contraption system and share the same collision pipeline.
 *
 * @see ContraptionCollider#collideBlocks(AbstractContraptionEntity)
 */
@Mixin(ContraptionCollider.class)
public abstract class ContraptionColliderMixin {

    private static final Logger TERRITORY_LOGGER = LoggerFactory.getLogger("Territory/ContraptionCollider");

    /**
     * 在 Create 装置方块碰撞处理前注入领地权限检查。
     * <p>
     * 若装置锚点位于受保护领地内，且装置操控者不具备 {@code destroy} 权限，
     * 则阻止碰撞以防止方块破坏。
     *
     * @param contraptionEntity 装置实体
     * @param cir               Mixin 回调（用于提前返回）
     */
    @Inject(
        method = "collideBlocks",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void territory$checkBlockCollisionPermission(
            AbstractContraptionEntity contraptionEntity,
            CallbackInfoReturnable<Boolean> cir) {

        // 客户端侧跳过
        Level world = contraptionEntity.level();
        if (world.isClientSide()) return;

        // 解析操控者 UUID
        UUID controllerUuid = resolveControllerUuid(contraptionEntity);
        if (controllerUuid == null) {
            // 无操控者 → 保守策略：不拦截
            return;
        }

        // 检查装置锚点是否在领地范围内
        Contraption contraption = contraptionEntity.getContraption();
        if (contraption == null) return;

        BlockPos anchor = contraption.anchor;
        if (anchor == null) return;

        String worldKey = ((ServerLevel) world).dimension().location().toString();
        Optional<TerritoryData> territoryOpt = TerritoryDataManager.getInstance()
            .findTerritoryAt(worldKey, anchor.getX(), anchor.getY(), anchor.getZ());

        if (territoryOpt.isEmpty()) return;

        // 沿四级优先链判定 destroy 权限
        TerritoryData territory = territoryOpt.get();
        boolean allowed = TerritoryPermissionService.getEffectiveFlag(
            territory, controllerUuid.toString(), FlagType.destroy
        );

        if (!allowed) {
            TERRITORY_LOGGER.debug(
                "Contraption block collision blocked in territory '{}' for controller {}",
                territory.name(), controllerUuid
            );
            cir.setReturnValue(false);
        }
    }

    /**
     * 从装置实体中解析操控者的 UUID。
     * <p>
     * 优先使用 {@link AbstractContraptionEntity#getControllingPlayer()} 获取
     * 当前操控玩家；若无操控玩家，则使用装置实体自身的 UUID 作为标识。
     *
     * @param entity 装置实体
     * @return 操控者 UUID，若无法解析则返回 {@code null}
     */
    private static UUID resolveControllerUuid(AbstractContraptionEntity entity) {
        // 优先：从 getControllingPlayer() 获取操控者
        Optional<UUID> controllerOpt = entity.getControllingPlayer();
        if (controllerOpt != null && controllerOpt.isPresent()) {
            return controllerOpt.get();
        }

        // 备选：使用装置实体自身的 UUID
        try {
            return entity.getUUID();
        } catch (Exception ignored) {
            return null;
        }
    }
}
