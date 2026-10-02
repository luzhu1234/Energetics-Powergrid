package com.energeticspowergrid.content.excitation;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlock;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * 励磁定子方块。复用 electroenergetics（CEE）原版定子的模型与贴图，
 * 但在定子的开口面（即磁极伸出的一面）上携带两个带电端子。
 * <p>
 * 端子坐标按 {@code facing=up} 编写。布局会随 {@code FACING}/{@code ROLL}
 * 一起旋转并跟随模型翻转，因此无论定子朝向如何，端子始终落在开口面上。
 * 节点 0 为正极端子，节点 1 为负极端子。
 */
public class ExcitationStatorBlock extends DirectionalRolledDeviceBlock<ExcitationStatorDevice> implements IBE<ExcitationStatorBlockEntity> {
    /**
     * "满环"状态：表示转子四周（垂直于转子轴的 4 个方向）已被励磁定子完全环绕。
     * 用于驱动模型/贴图变化（四台定子围成一圈时显示完整磁轭外观）。
     */
    public static final BooleanProperty FULL = BooleanProperty.create("full");

    /**
     * 两个端子都位于定子的开口面上，各占一个磁极端头。坐标按 {@code facing=up} 编写；
     * 在该原始朝向下，定子模型的磁极朝下（且 blockstate 不做额外旋转），
     * 所以端子编写在底面，才能与磁极处于同一侧。{@link NodeConfigurator#getNodes}
     * 会把它们随模型一起旋转，因此无论定子指向哪里，端子都会跟着走。
     * 节点 0 为正极端子，节点 1 为负极端子。
     */
    public static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(8f, 0f, 4f)
            .add(8f, 0f, 12f)
            .simple(Direction.UP);

    public ExcitationStatorBlock(Properties properties) {
        super(properties);
        // 默认不处于"满环"状态，等邻块变化时再计算
        registerDefaultState(defaultBlockState().setValue(FULL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        // 把 FULL 属性注册进方块状态定义，否则 setValue(FULL, ...) 会抛异常
        builder.add(FULL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 直接复用原版定子使用的 3px 外壳碰撞箱，保证与原版定子手感一致
        return AllShapes.CASING_3PX.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        // 遮挡形状置空：避免与相邻方块的贴图面剔除产生缝隙/黑面问题
        return Shapes.empty();
    }

    @Override
    public SimulatedDeviceType<ExcitationStatorDevice> getDevice() {
        // 声明本方块对应的模拟设备类型（电气仿真用）
        return EPGSimulatedDevices.EXCITATION_STATOR.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        // 返回随 FACING/ROLL 旋转后的全部端子位置
        return NODES.getNodes(state.getValue(FACING), state.getValue(ROLL));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        // 按端子 id 返回单个端子的世界坐标
        return NODES.getNodePos(state.getValue(FACING), state.getValue(ROLL), id);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        // 导线连接界面上显示的端子标签：0 号为正极、1 号为负极
        return CEELang.nodeLabel(id == 0 ? "positive" : "negative");
    }

    /**
     * 两个端子排布所沿的轴。在该轴方向上、且 facing/roll 相同的相邻定子
     * 可以端子对端子直接串联（导线自然对齐）。
     */
    public static Direction.Axis terminalAxis(BlockState state) {
        return getRotorAxis(state);
    }

    /**
     * 判断指定位置是否为励磁定子方块。
     */
    static boolean isStator(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ExcitationStatorBlock;
    }

    /**
     * 与原版定子的判定规则保持一致：{@code FACING} 指向的位置必须是一台转子，
     * 且转子的轴与本定子推导出的转子轴一致。只有满足该条件的定子才会真正
     * 对转子提供励磁（保证"贴着转子、方向正确"才算供电）。
     */
    public static boolean canPowerRotor(BlockPos pos, BlockState state, BlockPos rotorPos, BlockState rotorState) {
        if (!(rotorState.getBlock() instanceof AlternatorRotorBlock))
            return false;
        return pos.relative(state.getValue(FACING)).equals(rotorPos)
                && rotorState.getValue(AlternatorRotorBlock.AXIS) == getRotorAxis(state);
    }

    /**
     * 由 FACING + ROLL 推导本定子服务的转子轴。
     * 与原版定子的推导逻辑一致，保证两种定子对同一台转子的判定结果相同。
     */
    public static Direction.Axis getRotorAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean roll = state.getValue(ROLL);
        if (facing.getAxis().isHorizontal())
            return roll ? facing.getClockWise().getAxis() : Direction.Axis.Y;
        return roll ? Direction.Axis.X : Direction.Axis.Z;
    }

    /**
     * 判断本定子是否应处于"满环"状态：以 FACING 指向的转子为中心，
     * 检查垂直于转子轴的其余 4 个方向是否都被"能对该转子供电的励磁定子"占据。
     * 只有四台定子围满一圈才算满环（用于外观表现）。
     */
    private static boolean shouldBeFull(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction.Axis rotorAxis = getRotorAxis(state);

        // 先确认 FACING 指向处确实有一台轴匹配的转子，否则必然不是满环
        BlockPos rotorPos = pos.relative(facing);
        BlockState rotorState = level.getBlockState(rotorPos);

        if (!(rotorState.getBlock() instanceof AlternatorRotorBlock)
                || rotorState.getValue(AlternatorRotorBlock.AXIS) != rotorAxis)
            return false;

        // 遍历 6 个方向，跳过与转子轴平行的两端，剩下的 4 个方向都必须是
        // 朝向该转子的励磁定子，缺一不可
        for (Direction dir : Iterate.directions) {
            if (dir.getAxis() == rotorAxis)
                continue;
            BlockPos otherPos = rotorPos.relative(dir);
            BlockState otherState = level.getBlockState(otherPos);
            if (!(otherState.getBlock() instanceof ExcitationStatorBlock)
                    || !canPowerRotor(otherPos, otherState, rotorPos, rotorState))
                return false;
        }
        return true;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        // 邻块变化时重新评估满环状态（服务端与客户端都会走到这里，保证外观同步）
        BlockPos rotorPos = pos.relative(state.getValue(FACING));
        BlockState rotorState = level.getBlockState(rotorPos);
        boolean full = shouldBeFull(state, level, pos);
        if (full != state.getValue(FULL)) {
            level.setBlockAndUpdate(pos, state.setValue(FULL, full));
            // 状态变化后主动通知转子一侧刷新，避免转子的外观判定滞后
            level.updateNeighborsAtExceptFromFacing(rotorPos, rotorState.getBlock(), state.getValue(FACING).getOpposite());
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // 方块放置/形状更新时同样重算满环标志，覆盖"刚摆上去就围满一圈"的情况
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        return updated.setValue(FULL, shouldBeFull(state, level, pos));
    }

    @Override
    public Class<ExcitationStatorBlockEntity> getBlockEntityClass() {
        // IBE 接口：声明本方块对应的 BlockEntity 类型
        return ExcitationStatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExcitationStatorBlockEntity> getBlockEntityType() {
        // IBE 接口：返回注册表中对应的 BlockEntityType
        return EPGBlockEntityTypes.EXCITATION_STATOR.get();
    }
}
