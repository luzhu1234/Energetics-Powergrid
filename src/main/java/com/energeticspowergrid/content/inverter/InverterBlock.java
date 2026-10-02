package com.energeticspowergrid.content.inverter;

import com.energeticspowergrid.EPGSimulatedDevices;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * 逆变器方块，复用双闸刀开关的模型与碰撞箱。
 * <p>
 * 端子 1/2 是直流输入，3/4 是交流输出。闸刀断开时输入直通输出；
 * 闸刀闭合时进入逆变模式，输出端发出幅值跟随实测输入电压的方波，
 * 同时输入侧抽取与输出实际发出的功率相匹配的功率。
 */
public class InverterBlock extends DirectionalRolledDeviceBlock<InverterDevice> {
    /** 构造时默认闸刀断开（CLOSED=false），即初始为直通模式。 */
    public InverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CutOffSwitchBlock.CLOSED, false));
    }

    /** 绑定 INVERTER 模拟器件类型。 */
    @Override
    public SimulatedDeviceType<InverterDevice> getDevice() {
        return EPGSimulatedDevices.INVERTER.get();
    }

    /** 提供器件默认数据：把方块的 CLOSED 状态写入设备存档，保证初始一致。 */
    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Closed", state.getValue(CutOffSwitchBlock.CLOSED));
        return tag;
    }

    /** 在方块状态中注册借用的 CLOSED 属性（来自双闸刀开关）。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CutOffSwitchBlock.CLOSED);
    }

    /**
     * 玩家用手（非工具）交互时切换闸刀状态：
     * 同步设备侧的 closed 字段、播放旋转音效，并翻转方块状态。
     * 扳手/线轴放行给默认交互（拆卸、接线），避免误触切换。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(stack) || stack.getItem() instanceof WireSpoolItem || com.george_vi.electroenergetics.CEEItems.EMPTY_SPOOL.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (level instanceof ServerLevel serverLevel) {
            // 服务端同步设备状态，保证下一次求解立刻按新模式执行。
            InverterDevice device = DevicesSavedData.load(serverLevel).getDevice(pos, InverterDevice.class);
            if (device != null)
                device.closed = !state.getValue(CutOffSwitchBlock.CLOSED);
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        }
        level.setBlockAndUpdate(pos, state.cycle(CutOffSwitchBlock.CLOSED));
        return ItemInteractionResult.SUCCESS;
    }

    /** 复用双闸刀开关的碰撞箱，随朝向旋转。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CEEShapes.DOUBLE_SWITCH.get(state.getValue(FACING));
    }

    /** 返回全部四个端子的世界坐标映射（随朝向/滚转旋转）。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodes(state).getNodes(state.getValue(FACING));
    }

    /** 返回单个端子的世界坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodes(state).getNodePos(state.getValue(FACING), id);
    }

    /** 端子名称：0=输入+、1=输入−、2=输出+、3=输出−，用于客户端提示。 */
    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return switch (id) {
            case 0 -> Component.translatable("energeticspowergrid.nodes.input_positive");
            case 1 -> Component.translatable("energeticspowergrid.nodes.input_negative");
            case 2 -> Component.translatable("energeticspowergrid.nodes.output_positive");
            default -> Component.translatable("energeticspowergrid.nodes.output_negative");
        };
    }

    /** 滚转时把双闸刀节点配置绕 Y 轴旋转 90°，使端子跟随模型姿态。 */
    private static com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator nodes(BlockState state) {
        return state.getValue(ROLL)
                ? CEENodeConfigurations.DOUBLE_SWITCH.rotate(new Vec3(0, 90, 0))
                : CEENodeConfigurations.DOUBLE_SWITCH;
    }

    /**
     * 遮挡形状返回空：本方块视觉上是开放结构，
     * 不应遮挡相邻方块面（也利于光照与渲染合并）。
     */
    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
