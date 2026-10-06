package com.energeticspowergrid.content.mosfet;

import java.util.Map;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDevice;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * MOS 管方块（N 沟道变体基类，P 沟道由 {@link PMosfetBlock} 子类化）。
 * <p>
 * 继承方向+可滚转的器件方块基类，负责三件事：
 * <ul>
 * <li>提供方块碰撞箱（随朝向与滚转旋转）；</li>
 * <li>声明三个电气端子（0=栅极 G、1=漏极 D、2=源极 S）的节点位置；</li>
 * <li>绑定对应的方块实体与模拟器件类型。</li>
 * </ul>
 * 电气模型本身在 {@link MosfetDevice} / {@link MosfetElectricalProperties} 中实现。
 */
public class MosfetBlock extends DirectionalRolledDeviceBlock<MosfetDevice> implements IBE<MosfetBlockEntity> {
    /** 扁平贴地的芯片形状：本体 4-12 × 0-2 × 3-13（与 Blockbench 原始模型一致）。 */
    private static final VoxelShaper SHAPE = new com.simibubi.create.AllShapes.Builder(
            Shapes.box(4 / 16d, 0, 3 / 16d, 12 / 16d, 2 / 16d, 13 / 16d)).forDirectional();
    /** 滚转（ROLL）状态下的形状：本体沿 X 向延伸，与未滚转形状互为 90° 旋转。 */
    private static final VoxelShaper SHAPE_ROLL = new com.simibubi.create.AllShapes.Builder(
            Shapes.box(3 / 16d, 0, 4 / 16d, 13 / 16d, 2 / 16d, 12 / 16d)).forDirectional();

    /**
     * 三个端子都位于模型底面，坐标来自设计笔记（接线点.txt）：
     * 栅极 (13, 0, 8) 单独一侧，漏极 (3, 0, 5) 与源极 (3, 0, 11) 在另一侧——
     * 栅极与沟道端子之间的空隙正是 MOS 管符号里绝缘栅的表达。
     */
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(13f, 0f, 8f)
            .add(3f, 0f, 5f)
            .add(3f, 0f, 11f)
            .simple(Direction.UP);

    public MosfetBlock(Properties properties) {
        super(properties);
    }

    /** N 沟道变体返回 false；P 沟道子类覆写为 true，用于设备极性判定。 */
    public boolean isPChannel() {
        return false;
    }

    /** 绑定 N 沟道模拟器件类型；P 沟道子类覆写为 P_MOSFET。 */
    @Override
    public SimulatedDeviceType<MosfetDevice> getDevice() {
        return EPGSimulatedDevices.N_MOSFET.get();
    }

    /**
     * 提供器件的默认数据：从方块实体读取 V_GS(th)、R_DS(on)、I_D max 三个
     * 可调参数，写入设备存档。这样设备侧与方块实体侧的参数保持同步。
     */
    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        if (level.getBlockEntity(pos) instanceof MosfetBlockEntity be) {
            tag.putDouble("Vth", be.getVth());
            tag.putDouble("RdsOn", be.getRdsOn());
            tag.putDouble("IdMax", be.getIdMax());
        }
        return tag;
    }

    /** 按滚转状态选用对应碰撞箱，并随朝向旋转（贴地方块放墙面时自动立起）。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(ROLL) ? SHAPE_ROLL : SHAPE).get(state.getValue(FACING));
    }

    /** 返回全部三个端子的世界坐标映射（随朝向/滚转旋转）。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodes(state).getNodes(state.getValue(FACING));
    }

    /** 返回单个端子的世界坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodes(state).getNodePos(state.getValue(FACING), id);
    }

    /** 端子名称：0=栅极、1=漏极，其余（2）=源极，用于客户端提示。 */
    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return switch (id) {
            case 0 -> Component.translatable("energeticspowergrid.nodes.gate");
            case 1 -> Component.translatable("energeticspowergrid.nodes.drain");
            default -> Component.translatable("energeticspowergrid.nodes.source");
        };
    }

    /** 端子节点渲染尺寸统一为 2/16 格，方便玩家点击接线。 */
    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2 / 16f;
    }

    /** 滚转时把节点配置绕 Y 轴旋转 -90°，使端子跟随模型姿态。 */
    private static NodeConfigurator nodes(BlockState state) {
        return state.getValue(ROLL) ? NODES.rotate(new Vec3(0, -90, 0)) : NODES;
    }

    /** 绑定方块实体类型，供 IBE（IBlockEntityExtension 体系）使用。 */
    @Override
    public Class<MosfetBlockEntity> getBlockEntityClass() {
        return MosfetBlockEntity.class;
    }

    /** 返回注册表中的 MOSFET 方块实体类型。 */
    @Override
    public BlockEntityType<? extends MosfetBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.MOSFET.get();
    }
}
