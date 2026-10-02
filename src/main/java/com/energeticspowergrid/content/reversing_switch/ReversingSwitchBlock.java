package com.energeticspowergrid.content.reversing_switch;

import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEENodeConfigurations;
import com.george_vi.electroenergetics.CEEShapes;
import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * 换向开关（Reversing Switch）方块。
 * <p>
 * 复用 CEE 断路开关（CutOffSwitch）的 CLOSED 方块状态与双刀开关造型，
 * 是一个四端子的“电流换向”开关：闭合（CLOSED=true）时端子 0→3、1→2 导通；
 * 断开时 0→2、1→3 导通——即两组负载的极性/通路被对调。
 * 玩家手持非工具物品（扳手/线轴除外）右键即可切换状态，并播放扳手旋转音效。
 * 电气建模见 {@link ReversingSwitchDevice#preTick}。
 */
public class ReversingSwitchBlock extends DirectionalRolledDeviceBlock<ReversingSwitchDevice> {
    public ReversingSwitchBlock(Properties properties) {
        super(properties);
        // 默认状态：断开（不换向）
        registerDefaultState(defaultBlockState().setValue(CutOffSwitchBlock.CLOSED, false));
    }

    /** 返回对应的模拟设备类型（REVERSING_SWITCH）。 */
    @Override
    public SimulatedDeviceType<ReversingSwitchDevice> getDevice() {
        return EPGSimulatedDevices.REVERSING_SWITCH.get();
    }

    /** 放置设备时的初始设备数据：按当前方块状态写入闭合标记。 */
    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Closed", state.getValue(CutOffSwitchBlock.CLOSED));
        return tag;
    }

    /** 注册 CLOSED 方块状态属性（复用断路开关的属性实例）。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CutOffSwitchBlock.CLOSED);
    }

    /**
     * 右键交互：扳手、线轴、空线轴不拦截（交给导线连接逻辑）；
     * 其他物品右键则在服务端翻转设备的 closed 标记、播放旋转音效，
     * 并循环切换方块的 CLOSED 状态。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 工具类物品放行：扳手（旋转/配置）、线轴与空线轴（连接导线）
        if (AllItems.WRENCH.isIn(stack) || stack.getItem() instanceof WireSpoolItem || CEEItems.EMPTY_SPOOL.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (level instanceof ServerLevel serverLevel) {
            // 同步翻转设备侧的闭合标记（设备据此重建电路连接）
            ReversingSwitchDevice device = DevicesSavedData.load(serverLevel).getDevice(pos, ReversingSwitchDevice.class);
            if (device != null)
                device.closed = !state.getValue(CutOffSwitchBlock.CLOSED);
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        }
        // 切换方块状态
        level.setBlockAndUpdate(pos, state.cycle(CutOffSwitchBlock.CLOSED));
        return ItemInteractionResult.SUCCESS;
    }

    /** 碰撞箱使用 CEE 的双刀开关造型，随朝向旋转。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CEEShapes.DOUBLE_SWITCH.get(state.getValue(FACING));
    }

    /** 返回按朝向与滚动（ROLL）旋转后的全部节点坐标。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodes(state).getNodes(state.getValue(FACING));
    }

    /** 返回指定编号节点的坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodes(state).getNodePos(state.getValue(FACING), id);
    }

    /** 按滚动状态选择节点配置（ROLL=true 时绕 Y 轴旋转 90° 的双刀开关配置）。 */
    private static com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator nodes(BlockState state) {
        return state.getValue(ROLL)
                ? CEENodeConfigurations.DOUBLE_SWITCH.rotate(new Vec3(0, 90, 0))
                : CEENodeConfigurations.DOUBLE_SWITCH;
    }

    /** 遮挡形状为空，保证导线等邻居的模型面不被剔除。 */
    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
