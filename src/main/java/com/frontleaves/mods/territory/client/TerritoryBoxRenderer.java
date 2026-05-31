package com.frontleaves.mods.territory.client;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.config.TerritoryConfig;
import com.frontleaves.mods.territory.network.TerritoryNearbySyncPayload;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * 领地选区线框渲染器 — Create 蓝图风格
 * <p>
 * 使用 3D 实体方块线（QUADS 微型长方体）绘制选区边框，
 * 配合程序化棋盘格面填充 + Chasing AABB 平滑追踪动画。
 * 管理员显示紫色，普通用户显示蓝色，验证通过时切换为绿色，冲突时显示亮红色。
 * </p>
 *
 * @author 筱锋 (xiao_lfeng)
 */
@EventBusSubscriber(modid = Territory.MODID, value = Dist.CLIENT)
public class TerritoryBoxRenderer {

    // ── 颜色常量 ──
    // 管理员 — 柔化紫
    private static final Vector3f ADMIN_COLOR = new Vector3f(0.65f, 0.35f, 0.85f);
    // 冲突 — 亮红（领地重叠）
    private static final Vector3f CONFLICT_COLOR = new Vector3f(0.9f, 0.25f, 0.25f);
    // 用户 — 柔化蓝
    private static final Vector3f USER_COLOR = new Vector3f(0.35f, 0.55f, 0.85f);
    // 点指示器 — 柔化黄
    private static final Vector3f POINT_COLOR = new Vector3f(0.9f, 0.85f, 0.4f);
    // 验证通过 — 柔绿
    private static final Vector3f VALIDATED_COLOR = new Vector3f(0.35f, 0.85f, 0.5f);

    // ── 点指示器 ──
    private static final float POINT_SIZE = 0.15f;

    // ── 可复用 Vector3f（避免 GC 压力） ──
    private static final Vector3f VEC1 = new Vector3f();
    private static final Vector3f VEC2 = new Vector3f();
    private static final Vector3f VEC3 = new Vector3f();
    private static final Vector3f VEC4 = new Vector3f();

    // ── 3D 实体方块线 ──
    private static final float LINE_WIDTH = 1 / 16f;  // Create 蓝图线宽

    // ── Inflate 缩进 ──
    private static final float INFLATE_OUTSIDE = 1 / 128f;   // 相机在 AABB 外部
    private static final float INFLATE_INSIDE = -1 / 128f;   // 相机在 AABB 内部

    // ── 程序化棋盘格面填充 ──
    private static final float CHECKER_CELL_SIZE = 1.0f;      // 每个棋盘格单元大小
    private static final float CHECKER_ALPHA_A = 0.12f;        // 棋盘格 A 的 alpha
    private static final float CHECKER_ALPHA_B = 0.04f;        // 棋盘格 B 的 alpha

    // ── Chasing 动画 ──
    private static final float CHASE_FACTOR = 0.5f;            // 指数衰减系数

    // ── 自定义 RenderType：3D 实体方块线（QUADS 模式） ──
    private static final RenderType CUBOID_LINE_TYPE = RenderType.create(
            "territory_cuboid_line",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .createCompositeState(true)
    );

    // ── 自定义 RenderType：程序化棋盘格面填充 ──
    private static final RenderType CHECKER_FACE_TYPE = RenderType.create(
            "territory_checker_face",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .createCompositeState(true)
    );

    /**
     * 根据选区状态返回对应颜色 — 优先级：冲突 > 验证通过 > 管理员/用户
     */
    private static Vector3f getColorForState(ClientSelectionState state, boolean isAdmin) {
        // 冲突检测（最高优先级）
        if (state.isComplete()) {
            var nearby = state.getNearbyTerritories();
            if (!nearby.isEmpty()) {
                float minX = Math.min(state.getPos1().getX(), state.getPos2().getX());
                float minY = Math.min(state.getPos1().getY(), state.getPos2().getY());
                float minZ = Math.min(state.getPos1().getZ(), state.getPos2().getZ());
                float maxX = Math.max(state.getPos1().getX(), state.getPos2().getX()) + 1;
                float maxY = Math.max(state.getPos1().getY(), state.getPos2().getY()) + 1;
                float maxZ = Math.max(state.getPos1().getZ(), state.getPos2().getZ()) + 1;

                for (var boundary : nearby) {
                    if (minX <= boundary.maxX() + 1 && maxX >= boundary.minX()
                        && minY <= boundary.maxY() + 1 && maxY >= boundary.minY()
                        && minZ <= boundary.maxZ() + 1 && maxZ >= boundary.minZ()) {
                        return CONFLICT_COLOR;
                    }
                }
            }
        }

        if (state.isRecentlyValidated()) {
            return VALIDATED_COLOR;
        }
        return isAdmin ? ADMIN_COLOR : USER_COLOR;
    }

    @SubscribeEvent
    public static void onRenderLevel(@NotNull RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        boolean isRegularWand = mainHand.is(Territory.TERRITORY_WAND.get());
        boolean isAdminWand = mainHand.is(Territory.ADMIN_TERRITORY_WAND.get());

        if (!isRegularWand && !isAdminWand) return;

        ClientSelectionState state = isAdminWand ? ClientSelectionState.getAdmin() : ClientSelectionState.get();
        if (state.getPos1() == null) return;

        // 驱动 Chasing AABB 动画
        state.tickChasing();

        // 使用 partialTick 在 prev 和 chased 之间插值，获得当前帧的平滑 AABB
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float minX = Mth.lerp(partialTick, state.getPrevMinX(), state.getChasedMinX());
        float minY = Mth.lerp(partialTick, state.getPrevMinY(), state.getChasedMinY());
        float minZ = Mth.lerp(partialTick, state.getPrevMinZ(), state.getChasedMinZ());
        float maxX = Mth.lerp(partialTick, state.getPrevMaxX(), state.getChasedMaxX());
        float maxY = Mth.lerp(partialTick, state.getPrevMaxY(), state.getChasedMaxY());
        float maxZ = Mth.lerp(partialTick, state.getPrevMaxZ(), state.getChasedMaxZ());

        // 选区完整时扩展到完整方块（+1）
        if (state.isComplete()) {
            maxX += 1; maxY += 1; maxZ += 1;
        }

        Vector3f color = getColorForState(state, isAdminWand);

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        Matrix4f matrix = poseStack.last().pose();

        if (!state.isComplete()) {
            // ── 单点指示器（3D cuboid line 十字） ──
            VertexConsumer lineConsumer = bufferSource.getBuffer(CUBOID_LINE_TYPE);
            renderPointIndicator(lineConsumer, matrix, state.getPos1(), POINT_COLOR);
            bufferSource.endBatch(CUBOID_LINE_TYPE);
        } else {
            // Apply maxRenderSize clamping
            int maxSize = TerritoryConfig.MAX_RENDER_SIZE.get();
            float clampedMinX = clampAABB(minX, maxX, maxSize, true);
            float clampedMaxX = clampAABB(minX, maxX, maxSize, false);
            float clampedMinY = clampAABB(minY, maxY, maxSize, true);
            float clampedMaxY = clampAABB(minY, maxY, maxSize, false);
            float clampedMinZ = clampAABB(minZ, maxZ, maxSize, true);
            float clampedMaxZ = clampAABB(minZ, maxZ, maxSize, false);

            // 检测相机是否在 AABB 内部
            float inflate = INFLATE_OUTSIDE;
            if (camX >= clampedMinX && camX <= clampedMaxX &&
                camY >= clampedMinY && camY <= clampedMaxY &&
                camZ >= clampedMinZ && camZ <= clampedMaxZ) {
                inflate = INFLATE_INSIDE;
            }

            // ── 棋盘格面填充层（背景） ──
            VertexConsumer faceConsumer = bufferSource.getBuffer(CHECKER_FACE_TYPE);
            renderCheckerFaceFill(faceConsumer, matrix,
                    clampedMinX, clampedMinY, clampedMinZ, clampedMaxX, clampedMaxY, clampedMaxZ,
                    inflate, color);
            bufferSource.endBatch(CHECKER_FACE_TYPE);

            // ── 3D 实体方块线层（前景） ──
            VertexConsumer lineConsumer = bufferSource.getBuffer(CUBOID_LINE_TYPE);
            float eMinX = clampedMinX - inflate, eMinY = clampedMinY - inflate, eMinZ = clampedMinZ - inflate;
            float eMaxX = clampedMaxX + inflate, eMaxY = clampedMaxY + inflate, eMaxZ = clampedMaxZ + inflate;
            renderBoxEdges(lineConsumer, matrix,
                    eMinX, eMinY, eMinZ, eMaxX, eMaxY, eMaxZ,
                    color, 220);
            bufferSource.endBatch(CUBOID_LINE_TYPE);
        }

        // ── 附近领地边界渲染 ──
        List<TerritoryNearbySyncPayload.TerritoryBoundary> nearby = state.getNearbyTerritories();
        if (!nearby.isEmpty()) {
            VertexConsumer nearbyConsumer = bufferSource.getBuffer(CUBOID_LINE_TYPE);
            int maxSize = TerritoryConfig.MAX_RENDER_SIZE.get();
            for (var boundary : nearby) {
                Vector3f nearbyColor = switch (boundary.colorType()) {
                    case 1 -> ADMIN_COLOR;
                    case 2 -> new Vector3f(0.35f, 0.75f, 0.35f);
                    default -> USER_COLOR;
                };
                float nMinX = clampAABB(boundary.minX(), boundary.maxX() + 1, maxSize, true);
                float nMaxX = clampAABB(boundary.minX(), boundary.maxX() + 1, maxSize, false);
                float nMinY = clampAABB(boundary.minY(), boundary.maxY() + 1, maxSize, true);
                float nMaxY = clampAABB(boundary.minY(), boundary.maxY() + 1, maxSize, false);
                float nMinZ = clampAABB(boundary.minZ(), boundary.maxZ() + 1, maxSize, true);
                float nMaxZ = clampAABB(boundary.minZ(), boundary.maxZ() + 1, maxSize, false);
                renderBoxEdges(nearbyConsumer, matrix,
                    nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ,
                    nearbyColor, 150);
            }
            bufferSource.endBatch(CUBOID_LINE_TYPE);
        }

        poseStack.popPose();
    }

    // ── 3D 实体方块线渲染（Create 蓝图风格） ──

    private static void renderCuboidFace(VertexConsumer consumer, Matrix4f matrix,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            int r, int g, int b, int alpha,
            float nx, float ny, float nz) {
        VEC1.set(x0, y0, z0); matrix.transformPosition(VEC1);
        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
        VEC1.set(x1, y1, z1); matrix.transformPosition(VEC1);
        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
        VEC1.set(x2, y2, z2); matrix.transformPosition(VEC1);
        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
        VEC1.set(x3, y3, z3); matrix.transformPosition(VEC1);
        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
    }

    /**
     * 将边线渲染为 QUADS 模式的微型长方体（Create 蓝图风格）。
     * 根据主方向选择垂直扩展轴：X→YZ, Y→XZ, Z→XY。
     * 只渲染 4 个侧面，不渲染端面以避免角点重叠。
     */
    private static void bufferCuboidLine(VertexConsumer consumer, Matrix4f matrix,
            float x1, float y1, float z1, float x2, float y2, float z2,
            Vector3f color, int alpha) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) return;

        float nx = dx / length, ny = dy / length, nz = dz / length;
        float hw = LINE_WIDTH / 2.0f;
        int r = (int) (color.x() * 255);
        int g = (int) (color.y() * 255);
        int b = (int) (color.z() * 255);

        float absNx = Math.abs(nx), absNy = Math.abs(ny), absNz = Math.abs(nz);
        float ux, uy, uz, vx, vy, vz;

        if (absNx >= absNy && absNx >= absNz) {
            ux = 0; uy = hw; uz = 0;
            vx = 0; vy = 0; vz = hw;
        } else if (absNy >= absNx && absNy >= absNz) {
            ux = hw; uy = 0; uz = 0;
            vx = 0; vy = 0; vz = hw;
        } else {
            ux = hw; uy = 0; uz = 0;
            vx = 0; vy = hw; vz = 0;
        }

        // Start face corners
        float sx0 = x1 - ux - vx, sy0 = y1 - uy - vy, sz0 = z1 - uz - vz;
        float sx1 = x1 + ux - vx, sy1 = y1 + uy - vy, sz1 = z1 + uz - vz;
        float sx2 = x1 + ux + vx, sy2 = y1 + uy + vy, sz2 = z1 + uz + vz;
        float sx3 = x1 - ux + vx, sy3 = y1 - uy + vy, sz3 = z1 - uz + vz;

        // End face corners
        float ex0 = x2 - ux - vx, ey0 = y2 - uy - vy, ez0 = z2 - uz - vz;
        float ex1 = x2 + ux - vx, ey1 = y2 + uy - vy, ez1 = z2 + uz - vz;
        float ex2 = x2 + ux + vx, ey2 = y2 + uy + vy, ez2 = z2 + uz + vz;
        float ex3 = x2 - ux + vx, ey3 = y2 - uy + vy, ez3 = z2 - uz + vz;

        // 4 side faces (skip end caps to avoid corner overlap)
        renderCuboidFace(consumer, matrix,
                sx0, sy0, sz0, sx1, sy1, sz1, ex1, ey1, ez1, ex0, ey0, ez0,
                r, g, b, alpha, -ux, -uy, -uz);
        renderCuboidFace(consumer, matrix,
                sx3, sy3, sz3, ex3, ey3, ez3, ex2, ey2, ez2, sx2, sy2, sz2,
                r, g, b, alpha, ux, uy, uz);
        renderCuboidFace(consumer, matrix,
                sx0, sy0, sz0, ex0, ey0, ez0, ex3, ey3, ez3, sx3, sy3, sz3,
                r, g, b, alpha, -vx, -vy, -vz);
        renderCuboidFace(consumer, matrix,
                sx1, sy1, sz1, sx2, sy2, sz2, ex2, ey2, ez2, ex1, ey1, ez1,
                r, g, b, alpha, vx, vy, vz);
    }

    /**
     * 渲染 AABB 的 12 条边线（3D 实体方块线）。
     * 坐标应已经过 inflate 缩进处理。
     */
    private static void renderBoxEdges(VertexConsumer consumer, Matrix4f matrix,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            Vector3f color, int alpha) {
        // Bottom (y = minY)
        bufferCuboidLine(consumer, matrix, minX, minY, minZ, maxX, minY, minZ, color, alpha);
        bufferCuboidLine(consumer, matrix, maxX, minY, minZ, maxX, minY, maxZ, color, alpha);
        bufferCuboidLine(consumer, matrix, maxX, minY, maxZ, minX, minY, maxZ, color, alpha);
        bufferCuboidLine(consumer, matrix, minX, minY, maxZ, minX, minY, minZ, color, alpha);
        // Top (y = maxY)
        bufferCuboidLine(consumer, matrix, minX, maxY, minZ, maxX, maxY, minZ, color, alpha);
        bufferCuboidLine(consumer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, color, alpha);
        bufferCuboidLine(consumer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, color, alpha);
        bufferCuboidLine(consumer, matrix, minX, maxY, maxZ, minX, maxY, minZ, color, alpha);
        // Vertical
        bufferCuboidLine(consumer, matrix, minX, minY, minZ, minX, maxY, minZ, color, alpha);
        bufferCuboidLine(consumer, matrix, maxX, minY, minZ, maxX, maxY, minZ, color, alpha);
        bufferCuboidLine(consumer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, color, alpha);
        bufferCuboidLine(consumer, matrix, minX, minY, maxZ, minX, maxY, maxZ, color, alpha);
    }

    // ── 程序化棋盘格面填充 ──

    /**
     * 程序化棋盘格面填充 — 将 AABB 的 6 个面细分为棋盘格 QUAD。
     * 每个子 QUAD 根据 (cellX + cellZ) % 2 选择 CHECKER_ALPHA_A 或 CHECKER_ALPHA_B。
     * 应用 inflate 缩进：相机在 AABB 外用 +1/128f，在内部用 -1/128f。
     *
     * @param consumer     VertexConsumer (使用 CHECKER_FACE_TYPE)
     * @param matrix       变换矩阵
     * @param minX/Y/Z, maxX/Y/Z  AABB 边界（来自 Chasing AABB）
     * @param inflate      inflate 偏移量 (+1/128f 或 -1/128f)
     * @param color        颜色
     */
    private static void renderCheckerFaceFill(VertexConsumer consumer, Matrix4f matrix,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            float inflate, Vector3f color) {

        int r = (int) (color.x() * 255);
        int g = (int) (color.y() * 255);
        int b = (int) (color.z() * 255);

        // Apply inflate
        minX -= inflate; minY -= inflate; minZ -= inflate;
        maxX += inflate; maxY += inflate; maxZ += inflate;

        float cellSize = CHECKER_CELL_SIZE;

        // ── 底面 (y = minY, normal = 0, -1, 0) ──
        renderCheckerFace(consumer, matrix,
                minX, minZ, maxZ,
                maxX, maxZ, minY,
                r, g, b, 0, -1, 0, cellSize, true);

        // ── 顶面 (y = maxY, normal = 0, 1, 0) ──
        renderCheckerFace(consumer, matrix,
                minX, minZ, maxZ,
                maxX, maxZ, maxY,
                r, g, b, 0, 1, 0, cellSize, true);

        // ── 前面 (z = maxZ, normal = 0, 0, 1) ──
        renderCheckerFace(consumer, matrix,
                minX, minY, maxY,
                maxX, maxY, maxZ,
                r, g, b, 0, 0, 1, cellSize, false);

        // ── 后面 (z = minZ, normal = 0, 0, -1) ──
        renderCheckerFace(consumer, matrix,
                minX, minY, maxY,
                maxX, maxY, minZ,
                r, g, b, 0, 0, -1, cellSize, false);

        // ── 右面 (x = maxX, normal = 1, 0, 0) ──
        renderCheckerFace(consumer, matrix,
                minZ, minY, maxY,
                maxZ, maxY, maxX,
                r, g, b, 1, 0, 0, cellSize, false);

        // ── 左面 (x = minX, normal = -1, 0, 0) ──
        renderCheckerFace(consumer, matrix,
                minZ, minY, maxY,
                maxZ, maxY, minX,
                r, g, b, -1, 0, 0, cellSize, false);
    }

    /**
     * 渲染单个面的棋盘格细分。
     * 对于水平面 (isHorizontal=true): 在 XZ 平面细分，固定 Y
     * 对于垂直面 (isHorizontal=false): 根据法线判断迭代轴
     *
     * @param consumer   VertexConsumer
     * @param matrix     变换矩阵
     * @param startA     第一个细分轴的起始值
     * @param endA       第一个细分轴的结束值
     * @param startB     第二个细分轴的起始值
     * @param endB       第二个细分轴的结束值
     * @param fixedVal   固定轴的值（面的位置）
     * @param r,g,b      颜色分量
     * @param nx,ny,nz   法线
     * @param cellSize   棋盘格单元大小
     * @param isHorizontal 是否为水平面（底/顶）
     */
    private static void renderCheckerFace(VertexConsumer consumer, Matrix4f matrix,
            float startA, float startB, float endB,
            float endA, float endB2, float fixedVal,
            int r, int g, int b,
            float nx, float ny, float nz, float cellSize, boolean isHorizontal) {

        int cellsA = Math.max(1, (int) Math.ceil((endA - startA) / cellSize));
        int cellsB = Math.max(1, (int) Math.ceil((endB2 - startB) / cellSize));

        for (int ca = 0; ca < cellsA; ca++) {
            for (int cb = 0; cb < cellsB; cb++) {
                float a0 = startA + ca * cellSize;
                float a1 = Math.min(a0 + cellSize, endA);
                float b0 = startB + cb * cellSize;
                float b1 = Math.min(b0 + cellSize, endB2);

                boolean isEven = (ca + cb) % 2 == 0;
                float alpha = isEven ? CHECKER_ALPHA_A : CHECKER_ALPHA_B;
                int alphaInt = (int) (alpha * 255);

                if (isHorizontal) {
                    // Horizontal face: iterate X(a) × Z(b), Y fixed
                    VEC1.set(a0, fixedVal, b0); matrix.transformPosition(VEC1);
                    consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                    VEC1.set(a1, fixedVal, b0); matrix.transformPosition(VEC1);
                    consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                    VEC1.set(a1, fixedVal, b1); matrix.transformPosition(VEC1);
                    consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                    VEC1.set(a0, fixedVal, b1); matrix.transformPosition(VEC1);
                    consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                } else {
                    if (nz != 0) {
                        // Front/back face (z fixed): iterate X(a) × Y(b)
                        VEC1.set(a0, b0, fixedVal); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                        VEC1.set(a1, b0, fixedVal); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                        VEC1.set(a1, b1, fixedVal); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                        VEC1.set(a0, b1, fixedVal); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                    } else {
                        // Left/right face (x fixed): iterate Z(a) × Y(b)
                        VEC1.set(fixedVal, b0, a0); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                        VEC1.set(fixedVal, b0, a1); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                        VEC1.set(fixedVal, b1, a1); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                        VEC1.set(fixedVal, b1, a0); matrix.transformPosition(VEC1);
                        consumer.addVertex(VEC1.x, VEC1.y, VEC1.z).setColor(r, g, b, alphaInt).setNormal(nx, ny, nz);
                    }
                }
            }
        }
    }

    // ── 单点指示器 — 使用 3D cuboid line 渲染十字形标记 ──

    /**
     * 将 AABB 某一轴的坐标限制在最大渲染尺寸内。
     * 如果边长超过 maxSize，以中心点为基准截断。
     *
     * @param min     最小值
     * @param max     最大值
     * @param maxSize 最大允许边长
     * @param isMin   true 返回截断后的 min，false 返回截断后的 max
     */
    private static float clampAABB(float min, float max, int maxSize, boolean isMin) {
        float size = max - min;
        if (size <= maxSize) return isMin ? min : max;
        float center = (min + max) / 2.0f;
        float halfSize = maxSize / 2.0f;
        return isMin ? center - halfSize : center + halfSize;
    }

    private static void renderPointIndicator(VertexConsumer consumer, Matrix4f matrix,
            BlockPos pos, Vector3f color) {
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + 0.5f;
        float z = pos.getZ() + 0.5f;
        float s = POINT_SIZE;
        int alpha = 220;

        // 三条轴对齐短线段
        bufferCuboidLine(consumer, matrix, x - s, y, z, x + s, y, z, color, alpha);
        bufferCuboidLine(consumer, matrix, x, y - s, z, x, y + s, z, color, alpha);
        bufferCuboidLine(consumer, matrix, x, y, z - s, x, y, z + s, color, alpha);
    }
}
