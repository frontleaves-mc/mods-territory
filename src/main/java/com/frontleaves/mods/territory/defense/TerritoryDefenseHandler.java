package com.frontleaves.mods.territory.defense;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

/**
 * 领地防御事件处理器 — L1 方块/实体/爆炸事件拦截 + L2 FakePlayer 检测。
 * <p>
 * 通过 {@code @EventBusSubscriber} 自动注册到 NeoForge GAME 事件总线，
 * 涵盖方块破坏、放置、流体流动、玩家交互、爆炸、实体生成等 9 个事件处理方法。
 * 当权限检查失败时取消事件并向玩家发送 Actionbar 拒绝消息。
 */
@EventBusSubscriber(modid = Territory.MODID)
public class TerritoryDefenseHandler {

    private TerritoryDefenseHandler() {
        // 工具类，禁止实例化
    }

    // ============================================================
    // 1. 方块破坏 — BlockEvent.BreakEvent
    // ============================================================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        Player rawPlayer = event.getPlayer();
        if (rawPlayer == null) return;

        ServerPlayer player = resolveServerPlayer(rawPlayer, event, "territory.defend.destroy");
        if (player == null) return;

        BlockPos pos = event.getPos();
        String worldKey = resolveWorldKey(event.getLevel());
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        cancelIfDenied(player, territory.get(), FlagType.destroy, event);
    }

    // ============================================================
    // 2. 方块放置 — BlockEvent.EntityPlaceEvent
    // ============================================================

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        if (entity == null) return;

        // 解析有效的 ServerPlayer（含 FakePlayer L2 检测）
        ServerPlayer player = resolveServerPlayerFromEntity(entity, event, "territory.defend.place");
        if (player == null) return;

        BlockPos pos = event.getPos();
        String worldKey = resolveWorldKey(event.getLevel());
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        if (!TerritoryPermissionService.checkPermission(player, territory.get(), FlagType.place)) {
            event.setCanceled(true);
            TerritoryPermissionService.sendDenyMessage(player, resolveDenyKey(FlagType.place));
            restorePlacedItem(player, event);
        }
    }

    // ============================================================
    // 3. 流体放置 — BlockEvent.FluidPlaceBlockEvent
    // ============================================================

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel().isClientSide()) return;

        BlockPos pos = event.getPos();
        String worldKey = resolveWorldKey(event.getLevel());
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        // 流体事件无玩家参与，直接检查宏标志 flow
        if (!isFlowAllowed(territory.get())) {
            event.setCanceled(true);
        }
    }

    // ============================================================
    // 3.5 活塞推动 — BlockEvent.PistonEvent.Pre
    // ============================================================

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (event.getLevel().isClientSide()) return;

        String worldKey = resolveWorldKey(event.getLevel());
        if (worldKey == null) return;

        net.minecraft.world.level.block.piston.PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null) return;

        var manager = TerritoryDataManager.getInstance();
        // 检查被推动的方块是否落在无 piston 权限的领地
        for (BlockPos pushed : resolver.getToPush()) {
            Optional<TerritoryData> territory = manager.findTerritoryAt(
                worldKey, pushed.getX(), pushed.getY(), pushed.getZ());
            if (territory.isPresent() && !isPistonAllowed(territory.get())) {
                event.setCanceled(true);
                return;  // 无玩家参与，不发 actionbar（与 flow 一致）
            }
        }
        // 检查被活塞破坏的目标方块
        for (BlockPos destroyed : resolver.getToDestroy()) {
            Optional<TerritoryData> territory = manager.findTerritoryAt(
                worldKey, destroyed.getX(), destroyed.getY(), destroyed.getZ());
            if (territory.isPresent() && !isPistonAllowed(territory.get())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // ============================================================
    // 4. 右键方块 — PlayerInteractEvent.RightClickBlock
    // ============================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);

        // 跳过领地桌交互（由 TerritoryTableHandler 处理）
        if (state.is(Territory.TERRITORY_TABLE.get()) || state.is(Territory.ADMIN_TERRITORY_TABLE.get())) {
            return;
        }

        String worldKey = resolveWorldKey(event.getLevel());
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        // 查询方块分类，确定具体 FlagType
        FlagType flag = resolveBlockFlag(state);
        cancelIfDenied(player, territory.get(), flag, event);
    }

    // ============================================================
    // 5. 左键方块 — PlayerInteractEvent.LeftClickBlock
    // ============================================================

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        String worldKey = resolveWorldKey(event.getLevel());
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        cancelIfDenied(player, territory.get(), FlagType.destroy, event);
    }

    // ============================================================
    // 6. 实体交互 — PlayerInteractEvent.EntityInteract
    // ============================================================

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        BlockPos pos = target.blockPosition();
        String worldKey = resolveWorldKey(event.getLevel());
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        // 动物 → animalkilling，怪物 → mobkilling，其他 → interact
        FlagType flag;
        if (target instanceof Animal) {
            flag = FlagType.animalkilling;
        } else if (target instanceof Monster) {
            flag = FlagType.mobkilling;
        } else {
            flag = FlagType.interact;
        }
        cancelIfDenied(player, territory.get(), flag, event);
    }

    // ============================================================
    // 7. 爆炸开始 — ExplosionEvent.Start
    // ============================================================

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        BlockPos pos = BlockPos.containing(event.getExplosion().center());
        String worldKey = resolveWorldKeyFromLevel(level);
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        // 爆炸事件无特定玩家，直接检查 explosion 标志
        if (!isExplosionAllowed(territory.get())) {
            event.setCanceled(true);
        }
    }

    // ============================================================
    // 8. 爆炸扩散 — ExplosionEvent.Detonate
    // ============================================================

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        String worldKey = resolveWorldKeyFromLevel(level);
        if (worldKey == null) return;

        // 不取消事件，仅过滤领地内的方块
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                    .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
            if (territory.isPresent() && !isExplosionAllowed(territory.get())) {
                it.remove();
            }
        }
    }

    // ============================================================
    // 9. 实体加入世界 — EntityJoinLevelEvent
    // ============================================================

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob)) return;

        BlockPos pos = entity.blockPosition();
        String worldKey = resolveWorldKeyFromLevel(level);
        if (worldKey == null) return;

        Optional<TerritoryData> territory = TerritoryDataManager.getInstance()
                .findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());
        if (territory.isEmpty()) return;

        // 动物 → animal 标志，怪物 → monster 标志
        boolean allowed;
        if (mob instanceof Animal) {
            allowed = isEntitySpawnAllowed(territory.get(), FlagType.animal);
        } else if (mob instanceof Monster) {
            allowed = isEntitySpawnAllowed(territory.get(), FlagType.monster);
        } else {
            return;
        }

        if (!allowed) {
            event.setCanceled(true);
        }
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 权限拒绝时取消事件并发送 Actionbar 消息。
     * <p>提示键由 {@link #resolveDenyKey(FlagType)} 按 flag 自动派生：仅 destroy/place/container/damage
     * 走专属提示键，其余细分交互回退通用 {@code territory.defend.interact}。
     *
     * @param player    目标玩家
     * @param territory 领地数据
     * @param flag      权限标志（决定提示文案）
     * @param event     可取消事件
     */
    private static void cancelIfDenied(ServerPlayer player, TerritoryData territory,
                                       FlagType flag, ICancellableEvent event) {
        if (!TerritoryPermissionService.checkPermission(player, territory, flag)) {
            event.setCanceled(true);
            TerritoryPermissionService.sendDenyMessage(player, resolveDenyKey(flag));
        }
    }

    /** lang 中已有专属翻译键的 flag 集合；其余 flag 回退通用 interact 键。 */
    private static final java.util.Set<String> DEDICATED_DENY_KEYS = java.util.Set.of(
        "destroy", "place", "container", "damage"
    );

    /**
     * 按 flag 名解析拒绝提示的 i18n 键。
     * <p>仅 destroy/place/container/damage 走专属键（lang 中已有），
     * 其余细分交互回退通用 {@code territory.defend.interact}，避免出现未翻译键名。
     */
    private static String resolveDenyKey(FlagType flag) {
        return DEDICATED_DENY_KEYS.contains(flag.name())
            ? "territory.defend." + flag.name()
            : "territory.defend.interact";
    }

    /**
     * 从 Player 引用解析有效的 ServerPlayer，包含 L2 FakePlayer 检测。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>普通 ServerPlayer → 直接返回</li>
     *   <li>FakePlayer → 尝试解析 Create DeployerFakePlayer 的 owner UUID</li>
     *   <li>无法追踪 owner 的 FakePlayer → 拒绝并取消事件</li>
     * </ol>
     *
     * @param rawPlayer 原始玩家引用
     * @param event     可取消事件
     * @param i18nKey   拒绝消息国际化键
     * @return 解析后的 ServerPlayer，若为非法 FakePlayer 则返回 {@code null}
     */
    private static ServerPlayer resolveServerPlayer(Player rawPlayer, ICancellableEvent event, String i18nKey) {
        if (rawPlayer instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }

        if (rawPlayer instanceof FakePlayer fakePlayer) {
            return resolveFakePlayerOwner(fakePlayer, event, i18nKey);
        }

        // 非 ServerPlayer 也非 FakePlayer → 放行
        return null;
    }

    /**
     * 从 Entity 引用解析有效的 ServerPlayer（用于 EntityPlaceEvent 等只有 Entity 的事件）。
     */
    private static ServerPlayer resolveServerPlayerFromEntity(Entity entity, ICancellableEvent event, String i18nKey) {
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }

        if (entity instanceof FakePlayer fakePlayer) {
            return resolveFakePlayerOwner(fakePlayer, event, i18nKey);
        }

        // 非玩家实体（如发射器、活塞）→ 放行
        return null;
    }

    /**
     * L2 FakePlayer 检测：尝试从 Create DeployerFakePlayer 追踪 owner UUID。
     * <p>
     * 若 Create 模组已加载，通过反射获取 DeployerFakePlayer 的 owner 字段，
     * 并在服务器玩家列表中查找对应的 ServerPlayer 实例。
     * 若 Create 未加载或无法解析 owner → 视为非法 FakePlayer，拒绝操作。
     */
    private static ServerPlayer resolveFakePlayerOwner(FakePlayer fakePlayer, ICancellableEvent event, String i18nKey) {
        if (!ModList.get().isLoaded("create")) {
            // Create 未加载，FakePlayer 无 owner 追踪 → 拒绝
            event.setCanceled(true);
            return null;
        }

        try {
            Class<?> deployerClass = Class.forName(
                    "com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer");
            if (deployerClass.isInstance(fakePlayer)) {
                var ownerField = deployerClass.getDeclaredField("owner");
                ownerField.setAccessible(true);
                Object owner = ownerField.get(fakePlayer);
                if (owner instanceof UUID ownerUuid) {
                    // 在服务器玩家列表中查找 owner 对应的 ServerPlayer
                    ServerLevel level = fakePlayer.serverLevel();
                    ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(ownerUuid);
                    if (ownerPlayer != null) {
                        return ownerPlayer;
                    }
                }
            }
        } catch (Exception ignored) {
            // 反射失败（字段不存在、访问异常等），按非法 FakePlayer 处理
        }

        // 无法追踪 owner → 拒绝
        event.setCanceled(true);
        return null;
    }

    /**
     * 从方块状态解析对应的权限 FlagType。
     * <p>
     * 通过 {@link ModBlockRegistry#getBlockCategory} 查询方块分类，
     * 未命中时回退到 {@link FlagType#interact}。
     */
    private static FlagType resolveBlockFlag(BlockState state) {
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        FlagType category = ModBlockRegistry.getBlockCategory(blockId);
        return category != null ? category : FlagType.interact;
    }

    /**
     * 从 LevelAccessor 解析世界维度标识。
     */
    private static String resolveWorldKey(net.minecraft.world.level.LevelAccessor levelAccessor) {
        if (levelAccessor instanceof ServerLevel serverLevel) {
            return serverLevel.dimension().location().toString();
        }
        return null;
    }

    /**
     * 从 Level 解析世界维度标识。
     */
    private static String resolveWorldKeyFromLevel(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.dimension().location().toString();
        }
        return null;
    }

    /**
     * 放置被拒绝后返还玩家手中被取消消耗的物品。
     * <p>服务端 {@code setCanceled} 无法回滚客户端 vanilla 预测的物品消耗，故在此显式归还。
     * 通过比对主/副手物品所对应的方块与被放置方块是否一致来确定来源手；
     * 歧义（两手都匹配）时默认归主手。
     *
     * @param player 放置方块的玩家
     * @param event  被取消的放置事件
     */
    private static void restorePlacedItem(ServerPlayer player, BlockEvent.EntityPlaceEvent event) {
        net.minecraft.world.level.block.Block placedBlock = event.getPlacedBlock().getBlock();
        net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
        net.minecraft.world.item.ItemStack offHand = player.getOffhandItem();

        boolean mainMatches = isItemForBlock(mainHand, placedBlock);
        boolean offMatches = isItemForBlock(offHand, placedBlock);

        net.minecraft.world.item.ItemStack toRestore;
        if (mainMatches) {
            toRestore = mainHand;        // 主手优先（含歧义归主手）
        } else if (offMatches) {
            toRestore = offHand;
        } else {
            return;  // 两手都不匹配（防御性兜底，基本不触发）
        }

        net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player, toRestore.copy());
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * 判断给定物品栈是否是指定方块的放置物品（BlockItem 比对）。
     */
    private static boolean isItemForBlock(net.minecraft.world.item.ItemStack stack,
                                          net.minecraft.world.level.block.Block block) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            return blockItem.getBlock() == block;
        }
        return false;
    }

    /**
     * 判断领地是否允许活塞操作（piston flag 本身，回退 build 宏）。
     * <p>未显式设置 piston 时，回退到 build 宏标志判定。
     */
    private static boolean isPistonAllowed(TerritoryData territory) {
        Object val = territory.flags().get(FlagType.piston.name());
        if (val instanceof Boolean boolVal) return boolVal;
        if (val instanceof String strVal) return "allow".equalsIgnoreCase(strVal);
        // 未显式设置 piston → 回退 build 宏标志
        Object buildVal = territory.flags().get(FlagType.build.name());
        if (buildVal instanceof Boolean boolVal) return boolVal;
        if (buildVal instanceof String strVal) return "allow".equalsIgnoreCase(strVal);
        return false;  // 默认禁止
    }

    /**
     * 检查流体流动权限（宏标志 flow）。
     * <p>
     * 流体事件无玩家参与，需直接读取领地全局标志。
     */
    private static boolean isFlowAllowed(TerritoryData territory) {
        Object value = territory.flags().get(FlagType.flow.name());
        if (value instanceof Boolean boolVal) return boolVal;
        if (value instanceof String strVal) return "allow".equalsIgnoreCase(strVal);
        return false; // 默认拒绝
    }

    /**
     * 检查爆炸权限（宏标志 explosion）。
     * <p>
     * 爆炸事件无特定玩家参与，直接读取领地全局标志。
     */
    private static boolean isExplosionAllowed(TerritoryData territory) {
        Object value = territory.flags().get(FlagType.explosion.name());
        if (value instanceof Boolean boolVal) return boolVal;
        if (value instanceof String strVal) return "allow".equalsIgnoreCase(strVal);
        return false; // 默认拒绝
    }

    /**
     * 检查实体生成权限。
     * <p>
     * 实体生成事件无玩家参与，直接读取领地全局标志。
     */
    private static boolean isEntitySpawnAllowed(TerritoryData territory, FlagType flag) {
        Object value = territory.flags().get(flag.name());
        if (value instanceof Boolean boolVal) return boolVal;
        if (value instanceof String strVal) return "allow".equalsIgnoreCase(strVal);
        return false; // 默认拒绝
    }
}
