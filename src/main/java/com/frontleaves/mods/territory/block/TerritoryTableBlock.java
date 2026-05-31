package com.frontleaves.mods.territory.block;

import com.frontleaves.mods.territory.block.entity.TerritoryTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 领地桌方块 — 领地系统的核心方块，防爆且不可被活塞推动。
 */
public class TerritoryTableBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0),
            Shapes.box(0.25, 0.125, 0.25, 0.75, 0.9375, 0.75),
            Shapes.box(0.0, 0.75, 0.1875, 1.0, 1.0, 1.0)
    );

    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0),
            Shapes.box(0.25, 0.125, 0.25, 0.75, 0.9375, 0.75),
            Shapes.box(0.0, 0.75, 0.0, 1.0, 1.0, 0.8125)
    );

    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0),
            Shapes.box(0.25, 0.125, 0.25, 0.75, 0.9375, 0.75),
            Shapes.box(0.1875, 0.75, 0.0, 1.0, 1.0, 1.0)
    );

    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0),
            Shapes.box(0.25, 0.125, 0.25, 0.75, 0.9375, 0.75),
            Shapes.box(0.0, 0.75, 0.0, 0.8125, 1.0, 1.0)
    );

    public TerritoryTableBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerritoryTableBlockEntity(pos, state);
    }

    private VoxelShape shapeForState(BlockState state) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForState(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForState(state);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shapeForState(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shapeForState(state);
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }
}
