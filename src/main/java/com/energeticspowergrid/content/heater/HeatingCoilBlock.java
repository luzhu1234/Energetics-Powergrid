package com.energeticspowergrid.content.heater;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HeatingCoilBlock extends SimpleElectricalDeviceBlock<HeatingCoilDevice> implements IBE<HeatingCoilBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final NodeConfigurator NODES_NORTH = new NodeConfigurator.Builder()
            .add(2f, 16f, 8f)
            .add(14f, 16f, 8f)
            .simple(Direction.UP);
    public static final NodeConfigurator NODES_EAST = NODES_NORTH.rotate(new Vec3(0, 90, 0));
    public static final NodeConfigurator NODES_SOUTH = NODES_NORTH.rotate(new Vec3(0, 180, 0));
    public static final NodeConfigurator NODES_WEST = NODES_NORTH.rotate(new Vec3(0, 270, 0));

    private static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(box(0, 0, 5, 16, 16, 11), Direction.NORTH);

    public HeatingCoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public SimulatedDeviceType<HeatingCoilDevice> getDevice() {
        return EPGSimulatedDevices.HEATING_COIL.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodesFor(state).getNodes(Direction.UP);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodesFor(state).getNodePos(Direction.UP, id);
    }

    private static NodeConfigurator nodesFor(BlockState state) {
        return switch (state.getValue(FACING)) {
            case EAST -> NODES_EAST;
            case SOUTH -> NODES_SOUTH;
            case WEST -> NODES_WEST;
            default -> NODES_NORTH;
        };
    }

    @Override
    public Class<HeatingCoilBlockEntity> getBlockEntityClass() {
        return HeatingCoilBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HeatingCoilBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.HEATING_COIL.get();
    }
}
