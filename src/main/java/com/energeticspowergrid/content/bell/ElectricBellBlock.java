package com.energeticspowergrid.content.bell;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * 电铃方块（移植自电气时代 PowerGrid 的报警铃）：
 * 挂墙安装的两端子纯电阻器件——通电后电流流过铃线圈，电磁铁敲击铃壳发声。
 * <p>
 * 电气上就是一个 20Ω 的电阻（{@link ElectricBellDevice}）；
 * 声音表现由方块实体读取解算电流后驱动（音量随电流增大、音调随电流升高，
 * 断电时播放铃声收尾音效），详见 {@link ElectricBellBlockEntity}。
 * <p>
 * 方块朝向 = 铃开口的朝向（背面底板贴在被点击的墙面上）；
 * 两个接线端子位于底板顶部（背面上缘）。
 */
public class ElectricBellBlock extends SimpleElectricalDeviceBlock<ElectricBellDevice> implements IBE<ElectricBellBlockEntity> {
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 铃本体形状：背面底板贴墙。模型的未旋转姿态（blockstate y=0，开口朝南）
     * 中底板位于 z=0 侧（北），因此碰撞箱同样以 SOUTH 为基准朝向生成——
     * 此前误用 NORTH 作为基准，导致碰撞箱与模型/接线点绕中心相差 180°。
     */
    private static final VoxelShaper SHAPE = VoxelShaper.forHorizontal(
            Shapes.box(4 / 16d, 4 / 16d, 0 / 16d, 12 / 16d, 12 / 16d, 6 / 16d), Direction.SOUTH);

    /**
     * 两个接线端子：底板顶部边缘（背面 z=0.5px 处，y=12px），
     * 与碰撞箱一致以 SOUTH 为基准朝向（见上）。
     */
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(6.5f, 12f, 0.5f)
            .add(9.5f, 12f, 0.5f)
            .simple(Direction.SOUTH);

    public ElectricBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    /** 是否为彩蛋电铃（决定客户端播放的音效）；普通电铃恒为 false。 */
    public boolean isIceBell() {
        return false;
    }

    /** 是否为奶龙电铃（决定客户端播放的音效）；普通电铃恒为 false。 */
    public boolean isNailongBell() {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    /**
     * 放置：水平面被点击时铃贴在该面上（背面贴墙、开口朝房间）；
     * 上下表面被点击时按玩家水平朝向放置。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    /** 按朝向取铃本体碰撞箱。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(HORIZONTAL_FACING));
    }

    /** 绑定电铃模拟设备类型（20Ω 电阻元件）。 */
    @Override
    public SimulatedDeviceType<ElectricBellDevice> getDevice() {
        return EPGSimulatedDevices.ELECTRIC_BELL.get();
    }

    /** 返回两个接线端子的世界坐标映射（随朝向旋转）。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.getNodes(state.getValue(HORIZONTAL_FACING));
    }

    /** 返回单个接线端子的世界坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return NODES.getNodePos(state.getValue(HORIZONTAL_FACING), id);
    }

    /** 端子名称：两个均为接线端子。 */
    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return Component.translatable("energeticspowergrid.nodes.bell_terminal");
    }

    /** 端子节点渲染尺寸 2/16 格。 */
    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2 / 16f;
    }

    /** 绑定方块实体类型，供 IBE 使用。 */
    @Override
    public Class<ElectricBellBlockEntity> getBlockEntityClass() {
        return ElectricBellBlockEntity.class;
    }

    /** 返回注册表中的电铃方块实体类型。 */
    @Override
    public BlockEntityType<? extends ElectricBellBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.ELECTRIC_BELL.get();
    }
}
