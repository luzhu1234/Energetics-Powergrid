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

/**
 * 加热线圈（Heating Coil）方块。
 * <p>
 * 电气设备方块：作为固定阻值的电阻性负载接入电网，通电后发热升温。
 * 温度状态决定它对 Create 风扇的加工类型——
 * 200~400°C 提供烟熏（SMOKING），≥400°C 提供鼓风焙烧（BLASTING），
 * 即可替代原版的火/岩浆给机械风扇当热源（见 {@link HeaterSmokingType} /
 * {@link HeaterBlastingType} 两个风扇加工类型）。
 * 本方块负责外观、朝向与接线点；热学模拟在 {@link HeatingCoilDevice}，
 * 温度/工作状态显示在 {@link HeatingCoilBlockEntity}（含护目镜信息）。
 */
public class HeatingCoilBlock extends SimpleElectricalDeviceBlock<HeatingCoilDevice> implements IBE<HeatingCoilBlockEntity> {
    /** 水平朝向属性（四个水平方向）。 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 北向（默认朝向）的节点配置：两个接线点位于顶面 x=2 与 x=14 处。 */
    public static final NodeConfigurator NODES_NORTH = new NodeConfigurator.Builder()
            .add(2f, 16f, 8f)
            .add(14f, 16f, 8f)
            .simple(Direction.UP);
    /** 其余三个水平朝向的节点配置：由北向配置绕 Y 轴旋转得到。 */
    public static final NodeConfigurator NODES_EAST = NODES_NORTH.rotate(new Vec3(0, 90, 0));
    public static final NodeConfigurator NODES_SOUTH = NODES_NORTH.rotate(new Vec3(0, 180, 0));
    public static final NodeConfigurator NODES_WEST = NODES_NORTH.rotate(new Vec3(0, 270, 0));

    /** 碰撞箱：0~16 高、厚度 6 像素（z: 5~11）的竖直板状线圈，按水平朝向旋转。 */
    private static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(box(0, 0, 5, 16, 16, 11), Direction.NORTH);

    public HeatingCoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** 注册 FACING 属性。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * 放置时按玩家水平朝向确定 FACING；
     * 潜行放置时取反（线圈“正面”面向玩家）。
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())
            facing = facing.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** 碰撞箱随水平朝向旋转。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    /** 结构旋转时旋转朝向。 */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** 结构镜像时镜像朝向。 */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    /** 返回对应的模拟设备类型（HEATING_COIL）。 */
    @Override
    public SimulatedDeviceType<HeatingCoilDevice> getDevice() {
        return EPGSimulatedDevices.HEATING_COIL.get();
    }

    /** 返回按朝向选择的节点坐标表。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodesFor(state).getNodes(Direction.UP);
    }

    /** 返回指定编号节点的坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodesFor(state).getNodePos(Direction.UP, id);
    }

    /** 按水平朝向选择节点配置。 */
    private static NodeConfigurator nodesFor(BlockState state) {
        return switch (state.getValue(FACING)) {
            case EAST -> NODES_EAST;
            case SOUTH -> NODES_SOUTH;
            case WEST -> NODES_WEST;
            default -> NODES_NORTH;
        };
    }

    /** 方块实体类型 Class。 */
    @Override
    public Class<HeatingCoilBlockEntity> getBlockEntityClass() {
        return HeatingCoilBlockEntity.class;
    }

    /** 方块实体类型注册项。 */
    @Override
    public BlockEntityType<? extends HeatingCoilBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.HEATING_COIL.get();
    }
}
