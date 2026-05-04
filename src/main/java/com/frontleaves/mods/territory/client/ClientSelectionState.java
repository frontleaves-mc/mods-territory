package com.frontleaves.mods.territory.client;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only singleton that manages the current territory selection state.
 * Stores two corner positions (pos1, pos2) that define an axis-aligned box.
 */
@OnlyIn(Dist.CLIENT)
public class ClientSelectionState {

    private static final ClientSelectionState INSTANCE = new ClientSelectionState();
    private static final ClientSelectionState ADMIN_INSTANCE = new ClientSelectionState();

    private BlockPos pos1 = null;
    private BlockPos pos2 = null;
    private boolean validated = false;
    private long selectionTimestamp = 0;
    private long validationTimestamp = 0;

    // ── Chasing AABB 动画字段 ──
    private float prevMinX, prevMinY, prevMinZ;
    private float prevMaxX, prevMaxY, prevMaxZ;
    private float chasedMinX, chasedMinY, chasedMinZ;
    private float chasedMaxX, chasedMaxY, chasedMaxZ;
    private float targetMinX, targetMinY, targetMinZ;
    private float targetMaxX, targetMaxY, targetMaxZ;
    private boolean chasingActive = false;

    private ClientSelectionState() {
    }

    public static ClientSelectionState get() {
        return INSTANCE;
    }

    public static ClientSelectionState getAdmin() {
        return ADMIN_INSTANCE;
    }

    public String setPos1(BlockPos pos) {
        this.pos1 = pos.immutable();
        this.validated = false;
        this.updateChasingTargets();
        return null;
    }

    public String setPos2(BlockPos pos) {
        if (pos1 == null) return "territory.msg.set_pos1_first";
        this.pos2 = pos.immutable();
        this.validated = false;
        selectionTimestamp = System.nanoTime();
        this.updateChasingTargets();
        return null;
    }

    public void clearSelection() {
        pos1 = null;
        pos2 = null;
        validated = false;
        selectionTimestamp = 0;
        validationTimestamp = 0;
        prevMinX = prevMinY = prevMinZ = 0;
        prevMaxX = prevMaxY = prevMaxZ = 0;
        chasedMinX = chasedMinY = chasedMinZ = 0;
        chasedMaxX = chasedMaxY = chasedMaxZ = 0;
        targetMinX = targetMinY = targetMinZ = 0;
        targetMaxX = targetMaxY = targetMaxZ = 0;
        chasingActive = false;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }

    @Nullable
    public BlockPos getMin() {
        if (pos1 == null) return null;
        if (pos2 == null) return pos1;
        return new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    @Nullable
    public BlockPos getMax() {
        if (pos1 == null) return null;
        if (pos2 == null) return pos1;
        return new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    @Nullable
    public BlockPos getPos1() {
        return pos1;
    }

    @Nullable
    public BlockPos getPos2() {
        return pos2;
    }

    public boolean isValidated() {
        return validated;
    }

    public void setValidated(boolean v) {
        validated = v;
        if (v) {
            validationTimestamp = System.nanoTime();
        }
    }

    public long getSelectionTimestamp() {
        return selectionTimestamp;
    }

    public long getValidationTimestamp() {
        return validationTimestamp;
    }

    public boolean isRecentlyValidated() {
        if (validationTimestamp == 0) return false;
        return (System.nanoTime() - validationTimestamp) < 2_000_000_000L;
    }

    private void updateChasingTargets() {
        if (pos1 == null) return;
        if (pos2 == null) {
            targetMinX = pos1.getX();
            targetMinY = pos1.getY();
            targetMinZ = pos1.getZ();
            targetMaxX = pos1.getX();
            targetMaxY = pos1.getY();
            targetMaxZ = pos1.getZ();
        } else {
            targetMinX = Math.min(pos1.getX(), pos2.getX());
            targetMinY = Math.min(pos1.getY(), pos2.getY());
            targetMinZ = Math.min(pos1.getZ(), pos2.getZ());
            targetMaxX = Math.max(pos1.getX(), pos2.getX());
            targetMaxY = Math.max(pos1.getY(), pos2.getY());
            targetMaxZ = Math.max(pos1.getZ(), pos2.getZ());
        }
        if (!chasingActive) {
            prevMinX = targetMinX;
            prevMinY = targetMinY;
            prevMinZ = targetMinZ;
            prevMaxX = targetMaxX;
            prevMaxY = targetMaxY;
            prevMaxZ = targetMaxZ;
            chasedMinX = targetMinX;
            chasedMinY = targetMinY;
            chasedMinZ = targetMinZ;
            chasedMaxX = targetMaxX;
            chasedMaxY = targetMaxY;
            chasedMaxZ = targetMaxZ;
            chasingActive = true;
        }
    }

    public void tickChasing() {
        if (!chasingActive) return;
        float factor = 0.5f;
        prevMinX = chasedMinX;
        prevMinY = chasedMinY;
        prevMinZ = chasedMinZ;
        prevMaxX = chasedMaxX;
        prevMaxY = chasedMaxY;
        prevMaxZ = chasedMaxZ;
        chasedMinX += (targetMinX - chasedMinX) * factor;
        chasedMinY += (targetMinY - chasedMinY) * factor;
        chasedMinZ += (targetMinZ - chasedMinZ) * factor;
        chasedMaxX += (targetMaxX - chasedMaxX) * factor;
        chasedMaxY += (targetMaxY - chasedMaxY) * factor;
        chasedMaxZ += (targetMaxZ - chasedMaxZ) * factor;
    }

    public float getChasedMinX() {
        return chasedMinX;
    }

    public float getChasedMinY() {
        return chasedMinY;
    }

    public float getChasedMinZ() {
        return chasedMinZ;
    }

    public float getChasedMaxX() {
        return chasedMaxX;
    }

    public float getChasedMaxY() {
        return chasedMaxY;
    }

    public float getChasedMaxZ() {
        return chasedMaxZ;
    }

    public float getPrevMinX() {
        return prevMinX;
    }

    public float getPrevMinY() {
        return prevMinY;
    }

    public float getPrevMinZ() {
        return prevMinZ;
    }

    public float getPrevMaxX() {
        return prevMaxX;
    }

    public float getPrevMaxY() {
        return prevMaxY;
    }

    public float getPrevMaxZ() {
        return prevMaxZ;
    }
}
