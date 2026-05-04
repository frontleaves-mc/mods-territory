package com.frontleaves.mods.territory.geometry;

import net.minecraft.core.BlockPos;

/**
 * 3D 轴对齐包围盒，用于领地选区的空间计算。
 *
 * @param minX 最小 X
 * @param minY 最小 Y
 * @param minZ 最小 Z
 * @param maxX 最大 X
 * @param maxY 最大 Y
 * @param maxZ 最大 Z
 */
public record AABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static AABB from(BlockPos p1, BlockPos p2) {
        return new AABB(
            Math.min(p1.getX(), p2.getX()),
            Math.min(p1.getY(), p2.getY()),
            Math.min(p1.getZ(), p2.getZ()),
            Math.max(p1.getX(), p2.getX()),
            Math.max(p1.getY(), p2.getY()),
            Math.max(p1.getZ(), p2.getZ())
        );
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean intersects(AABB o) {
        return minX <= o.maxX && maxX >= o.minX
            && minY <= o.maxY && maxY >= o.minY
            && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
