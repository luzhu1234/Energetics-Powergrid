package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGSimulatedDevices;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FactoryLightBlock extends SimpleElectricalDeviceBlock<FactoryLightDevice> implements IBE<FactoryLightBlockEntity> {
    public static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 6);
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 3);

    public static final VoxelShape SHAPE_SINGLE = box(2, 8, 2, 14, 16, 14);
    public static final VoxelShaper SHAPER_ENDS = VoxelShaper.forHorizontal(box(2, 8, 2, 14, 16, 16), Direction.NORTH);
    public static final VoxelShaper SHAPER_MIDDLE = VoxelShaper.forHorizontalAxis(box(2, 8, 0, 14, 16, 16), Direction.Axis.Z);

    public static final NodeConfigurator NODES_Z = new NodeConfigurator.Builder()
            .add(5f, 16f, 8f)
            .add(11f, 16f, 8f)
            .simple(Direction.UP);
    public static final NodeConfigurator NODES_X = NODES_Z.rotate(new Vec3(0, 90, 0));

    public FactoryLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HORIZONTAL_AXIS, Direction.Axis.Z)
                .setValue(PART, 0)
                .setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_AXIS, PART, POWER);
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (state.getValue(POWER)) {
            case 2 -> ILightBulb.LIGHT_LEVEL_LOW_POWER;
            case 3 -> ILightBulb.LIGHT_LEVEL_FULL_POWER;
            default -> 0;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case 0 -> SHAPE_SINGLE;
            case 1 -> SHAPER_ENDS.get(Direction.NORTH);
            case 2 -> SHAPER_MIDDLE.get(Direction.Axis.Z);
            case 3 -> SHAPER_ENDS.get(Direction.SOUTH);
            case 4 -> SHAPER_ENDS.get(Direction.WEST);
            case 5 -> SHAPER_MIDDLE.get(Direction.Axis.X);
            case 6 -> SHAPER_ENDS.get(Direction.EAST);
            default -> SHAPE_SINGLE;
        };
    }

    private static boolean canConnect(Direction dir, BlockState neighbour) {
        int part = neighbour.getValue(PART);
        var axis = neighbour.getValue(HORIZONTAL_AXIS);
        if (part == 0)
            return true;
        boolean isZ = axis == Direction.Axis.Z;
        boolean isX = axis == Direction.Axis.X;
        if (isZ && (part < 1 || part > 3))
            return false;
        if (isX && (part < 4 || part > 6))
            return false;
        if ((isZ && part == 2) || (isX && part == 5))
            return false;
        if (axis != dir.getAxis())
            return false;
        if ((part == 1 || part == 4) && dir.getAxisDirection() == Direction.AxisDirection.POSITIVE)
            return false;
        if ((part == 3 || part == 6) && dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE)
            return false;
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var dir = ctx.getClickedFace();
        var clickedState = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(dir, -1));
        if (clickedState.is(this) && dir.getAxis() != Direction.Axis.Y && canConnect(dir, clickedState)) {
            int part;
            if (dir.getAxis() == Direction.Axis.Z)
                part = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 3 : 1;
            else
                part = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 6 : 4;
            return defaultBlockState()
                    .setValue(HORIZONTAL_AXIS, dir.getAxis())
                    .setValue(PART, part);
        }
        var facing = ctx.getHorizontalDirection();
        if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown())
            facing = facing.getClockWise();
        return defaultBlockState().setValue(HORIZONTAL_AXIS, facing.getAxis());
    }

    public static boolean isCenter(int part) {
        return part == 2 || part == 5;
    }

    public static boolean isNegativeEdge(int part) {
        return part == 1 || part == 4;
    }

    public static boolean isPositiveEdge(int part) {
        return part == 3 || part == 6;
    }

    private static int getCenter(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 2 : 5;
    }

    private static int getNegativeEdge(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 1 : 4;
    }

    private static int getPositiveEdge(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 3 : 6;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        int part = state.getValue(PART);
        if (neighborState.is(this)) {
            if (neighborState.getValue(HORIZONTAL_AXIS) == direction.getAxis()) {
                int nPart = neighborState.getValue(PART);
                var axis = direction.getAxis();
                var axisDir = direction.getAxisDirection();
                if (part == 0 && axisDir == Direction.AxisDirection.POSITIVE && (isPositiveEdge(nPart) || isCenter(nPart)))
                    return state.setValue(HORIZONTAL_AXIS, axis).setValue(PART, getNegativeEdge(axis));
                if (part == 0 && axisDir == Direction.AxisDirection.NEGATIVE && (isNegativeEdge(nPart) || isCenter(nPart)))
                    return state.setValue(HORIZONTAL_AXIS, axis).setValue(PART, getPositiveEdge(axis));
                if (isNegativeEdge(part) && axisDir == Direction.AxisDirection.NEGATIVE && (isNegativeEdge(nPart) || isCenter(nPart)))
                    return state.setValue(PART, getCenter(axis));
                if (isPositiveEdge(part) && axisDir == Direction.AxisDirection.POSITIVE && (isPositiveEdge(nPart) || isCenter(nPart)))
                    return state.setValue(PART, getCenter(axis));
            }
        } else if (neighborState.is(Blocks.AIR)) {
            var axis = state.getValue(HORIZONTAL_AXIS);
            if (axis == direction.getAxis()) {
                if (isNegativeEdge(part) && direction.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                    return state.setValue(PART, 0);
                if (isPositiveEdge(part) && direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE)
                    return state.setValue(PART, 0);
                if (isCenter(part)) {
                    if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                        return state.setValue(PART, getPositiveEdge(axis));
                    return state.setValue(PART, getNegativeEdge(axis));
                }
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        return onBlockEntityUse(level, pos, be ->
                be.replaceBulb(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.FAIL);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof ILightBulb) {
            return onBlockEntityUseItemOn(level, pos, be ->
                    be.replaceBulb(player, hand, stack)
                            ? ItemInteractionResult.SUCCESS
                            : ItemInteractionResult.FAIL);
        }
        if (stack.getItem() instanceof DyeItem dye) {
            return onBlockEntityUseItemOn(level, pos, be -> be.setColor(dye.getDyeColor()));
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            var be = getBlockEntity(level, pos);
            if (be != null)
                be.removeLights();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof FactoryLightBlockEntity fixture) {
            ItemStack bulb = fixture.copyBulbStack();
            if (!bulb.isEmpty()) {
                var drops = new ArrayList<>(super.getDrops(state, params));
                drops.add(bulb);
                return drops;
            }
        }
        return super.getDrops(state, params);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(HORIZONTAL_AXIS);
        return state.setValue(HORIZONTAL_AXIS,
                rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, axis)).getAxis());
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    @Override
    public SimulatedDeviceType<FactoryLightDevice> getDevice() {
        return EPGSimulatedDevices.FACTORY_LIGHT.get();
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
        return state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X ? NODES_X : NODES_Z;
    }

    @Override
    public Class<FactoryLightBlockEntity> getBlockEntityClass() {
        return FactoryLightBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FactoryLightBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.FACTORY_LIGHT.get();
    }
}
