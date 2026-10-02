package com.energeticspowergrid.content.transistor;

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
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 三极管方块（NPN 变体基类，PNP 由 {@link PnpTransistorBlock} 子类化）。
 * <p>
 * 继承方向+可滚转的器件方块基类，负责三件事：
 * <ul>
 * <li>提供方块碰撞箱（随朝向与滚转旋转）；</li>
 * <li>声明三个电气端子（0=基极B、1=集电极C、2=发射极E）的节点位置；</li>
 * <li>绑定对应的方块实体与模拟器件类型。</li>
 * </ul>
 * 电气模型本身在 {@link TransistorDevice} / {@link TransistorElectricalProperties} 中实现。
 */
public class TransistorBlock extends DirectionalRolledDeviceBlock<TransistorDevice> implements IBE<TransistorBlockEntity> {
    /** 引脚朝下、本体沿 Z 向的薄板形状（与 Blockbench 原始模型一致）。 */
    private static final VoxelShaper SHAPE = new com.simibubi.create.AllShapes.Builder(
            net.minecraft.world.phys.shapes.Shapes.box(3 / 16d, 0, 7 / 16d, 13 / 16d, 13 / 16d, 9 / 16d)).forDirectional();
    /** 滚转（ROLL）状态下的形状：本体沿 X 向变薄，与未滚转形状互为 90° 旋转。 */
    private static final VoxelShaper SHAPE_ROLL = new com.simibubi.create.AllShapes.Builder(
            net.minecraft.world.phys.shapes.Shapes.box(7 / 16d, 0, 3 / 16d, 9 / 16d, 13 / 16d, 13 / 16d)).forDirectional();

    /**
     * 三个端子 B、C、E 都位于模型底面，各自对准一根引脚的中心。
     * 引脚在 x 方向占据 4-6、7-9、10-12 像素，因此中心位于 x = 5、8、11——
     * 相邻间隔 3 像素，而不是设计笔记里写的 2 像素（以实际模型为准）。
     */
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(5f, 0f, 8f)
            .add(8f, 0f, 8f)
            .add(11f, 0f, 8f)
            .simple(Direction.UP);

    public TransistorBlock(Properties properties) {
        super(properties);
    }

    /** NPN 变体返回 false；PNP 子类覆写为 true，用于设备极性判定。 */
    public boolean isPnp() {
        return false;
    }

    /** 绑定 NPN 模拟器件类型；PNP 子类覆写为 PNP_TRANSISTOR。 */
    @Override
    public SimulatedDeviceType<TransistorDevice> getDevice() {
        return EPGSimulatedDevices.NPN_TRANSISTOR.get();
    }

    /**
     * 提供器件的默认数据：从方块实体读取 β、V_BE(on)、I_C max 三个可调参数，
     * 写入设备存档。这样设备侧与方块实体侧的参数保持同步。
     */
    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        if (level.getBlockEntity(pos) instanceof TransistorBlockEntity be) {
            tag.putDouble("Beta", be.getBeta());
            tag.putDouble("VbeOn", be.getVbeOn());
            tag.putDouble("IcMax", be.getIcMax());
        }
        return tag;
    }

    /** 按滚转状态选用对应碰撞箱，并随朝向旋转。 */
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

    /** 端子名称：0=基极、1=集电极，其余（2）=发射极，用于客户端提示。 */
    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return switch (id) {
            case 0 -> Component.translatable("energeticspowergrid.nodes.base");
            case 1 -> Component.translatable("energeticspowergrid.nodes.collector");
            default -> Component.translatable("energeticspowergrid.nodes.emitter");
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
    public Class<TransistorBlockEntity> getBlockEntityClass() {
        return TransistorBlockEntity.class;
    }

    /** 返回注册表中的 TRANSISTOR 方块实体类型。 */
    @Override
    public BlockEntityType<? extends TransistorBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.TRANSISTOR.get();
    }
}
