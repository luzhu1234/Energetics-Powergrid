package com.energeticspowergrid.content.fixture;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGSimulatedDevices;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 灯具（Light Fixture）方块。
 * <p>
 * 属于本模组的电气照明设备：作为 CEE（Create: Electro Energetics）模拟电网中的一个
 * 电阻性负载接入电路，通电后灯泡发热发光，根据两端电压分三档亮度（POWER 0~2）。
 * <p>
 * 方块本体只负责：外观/碰撞箱、光照等级、节点接线位置、玩家交互（换灯泡、染色）、
 * 掉落物处理；实际的电气行为（电阻、发热、烧毁判定）由 {@link LightFixtureDevice} 完成，
 * 灯泡状态（灯泡物品、温度、颜色）由 {@link LightFixtureBlockEntity} 保存并同步给渲染器。
 */
public class LightFixtureBlock extends DirectionalRolledDeviceBlock<LightFixtureDevice> implements IBE<LightFixtureBlockEntity> {
    /** 亮度档位方块状态属性：0 = 熄灭，1 = 低亮度（电压约 45%~85% 额定），2 = 满亮度（电压 >= 85% 额定）。 */
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 2);
    /** 灯泡子模型相对方块模型原点的偏移量（向上 3/16 格），渲染灯泡时使用。 */
    public static final Vec3 BULB_MODEL_OFFSET = new Vec3(0, 3 / 16f, 0);

    /** 灯具本体碰撞箱：底部一块 3 格厚（实际 0~3 像素）的吸顶底座，按朝向旋转。 */
    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(box(3.5, 0, 3.5, 12.5, 3, 12.5), Direction.UP);
    public LightFixtureBlock(Properties properties) {
        super(properties);
        // 默认状态：熄灭（POWER = 0）
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    /** 注册方块状态属性（FACING、ROLL 来自父类，这里追加 POWER）。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    /** 声明本方块光照等级会动态变化（随 POWER 状态改变），以便光照引擎正确刷新。 */
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    /** 根据亮度档位返回实际光照等级：低档/满档取灯泡接口定义的等级，熄灭时为 0。 */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (state.getValue(POWER)) {
            case 1 -> ILightBulb.LIGHT_LEVEL_LOW_POWER;
            case 2 -> ILightBulb.LIGHT_LEVEL_FULL_POWER;
            default -> 0;
        };
    }

    /** 碰撞/轮廓箱随朝向 FACING 旋转。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    /** 返回本方块对应的模拟设备类型（注册表中注册的 LIGHT_FIXTURE）。 */
    @Override
    public SimulatedDeviceType<LightFixtureDevice> getDevice() {
        return EPGSimulatedDevices.LIGHT_FIXTURE.get();
    }

    /**
     * 接线点坐标表：与手写的 light_fixture_h（墙面）/ light_fixture_v（吸顶）模型中
     * 端子几何的实际位置一一对应。灯具的方块状态使用手写的旋转组合（墙体模型
     * 绕 Z 轴直立 + 按朝向的 Y 轴旋转），与 {@link NodeConfigurator} 的通用旋转
     * 约定不一致——此前用 NodeConfigurator 旋转节点坐标，导致墙面放置时接线点
     * 与模型上显示的端子错位，故改为显式查表。
     */
    /** v 模型（吸顶）的两个端子：底座两端沿 X 分布。 */
    private static final Vec3 V_T1 = voxelOf(3.5, 2.5, 8), V_T2 = voxelOf(12.5, 2.5, 8);
    /** v 模型滚转后：端子沿 Z 分布。 */
    private static final Vec3 V_T1R = voxelOf(8, 2.5, 3.5), V_T2R = voxelOf(8, 2.5, 12.5);
    /** h 模型（墙面，作者基准朝向 = 西）的两个端子：背板上竖直分布。 */
    private static final Vec3 H_T1 = voxelOf(13.5, 3.5, 8), H_T2 = voxelOf(13.5, 12.5, 8);
    /** h 模型滚转后：端子变为水平分布。 */
    private static final Vec3 H_T1R = voxelOf(13.5, 8, 12.5), H_T2R = voxelOf(13.5, 8, 3.5);

    /** 像素坐标转方块坐标（/16）。 */
    private static Vec3 voxelOf(double x, double y, double z) {
        return new Vec3(x / 16f, y / 16f, z / 16f);
    }

    /**
     * 按朝向与滚转状态返回指定编号的接线点坐标（查表，见上方注释）。
     */
    private static Vec3 nodeOffset(Direction facing, boolean roll, int id) {
        boolean first = id == 0;
        return switch (facing) {
            // 吸顶：v 模型；滚转后端子沿 Z 分布
            case UP -> first ? (roll ? V_T1R : V_T1) : (roll ? V_T2R : V_T2);
            // 朝下：v 模型绕 X 轴 180°，端子移到顶部（y=13.5）；滚转后再绕 Y 轴 90°
            case DOWN -> first ? (roll ? voxelOf(8, 13.5, 3.5) : voxelOf(3.5, 13.5, 8))
                               : (roll ? voxelOf(8, 13.5, 12.5) : voxelOf(12.5, 13.5, 8));
            // 墙面：h 模型，端子在背板上竖直分布；滚转后水平分布
            case WEST -> first ? (roll ? H_T1R : H_T1) : (roll ? H_T2R : H_T2);
            // 朝东：h 模型绕 Y 轴 180°，端子对侧镜像
            case EAST -> first ? (roll ? voxelOf(2.5, 8, 3.5) : voxelOf(2.5, 3.5, 8))
                               : (roll ? voxelOf(2.5, 8, 12.5) : voxelOf(2.5, 12.5, 8));
            // 朝北：h 模型绕 Y 轴 90°
            case NORTH -> first ? (roll ? voxelOf(12.5, 8, 13.5) : voxelOf(8, 3.5, 13.5))
                                : (roll ? voxelOf(3.5, 8, 13.5) : voxelOf(8, 12.5, 13.5));
            // 朝南：h 模型绕 Y 轴 -90°
            case SOUTH -> first ? (roll ? voxelOf(3.5, 8, 2.5) : voxelOf(8, 3.5, 2.5))
                                : (roll ? voxelOf(12.5, 8, 2.5) : voxelOf(8, 12.5, 2.5));
        };
    }

    /**
     * 直接以方块状态查询接线点的<b>绝对世界坐标</b>：
     * 在 {@link #nodeOffset} 给出的方块内相对偏移基础上叠加方块自身坐标。
     * 供需要绝对坐标的场合（调试、渲染、外部工具）一步到位地调用，
     * 与 CEE 节点框架计算实际接线位置用的是同一份查表数据，不会脱节。
     *
     * @param pos   方块的世界坐标
     * @param state 灯具方块状态（提供 FACING 与 ROLL）
     * @param id    节点编号（0/1，两个接线端子）
     */
    public static Vec3 absoluteNodePosition(BlockPos pos, BlockState state, int id) {
        return nodeOffset(state.getValue(FACING), state.getValue(ROLL), id)
                .add(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * 两个接线点的绝对坐标映射（编号 → 绝对坐标），见 {@link #absoluteNodePosition}。
     */
    public static Map<Integer, Vec3> absoluteNodePositions(BlockPos pos, BlockState state) {
        Map<Integer, Vec3> map = new java.util.HashMap<>();
        for (int id = 0; id < 2; id++)
            map.put(id, absoluteNodePosition(pos, state, id));
        return map;
    }

    /** 返回按朝向与滚动（ROLL）对齐模型端子后的全部节点坐标（相对偏移，框架叠加方块坐标）。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> map = new java.util.HashMap<>();
        for (int id = 0; id < 2; id++)
            map.put(id, nodeOffset(state.getValue(FACING), state.getValue(ROLL), id));
        return map;
    }

    /** 返回指定编号节点的世界相对坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodeOffset(state.getValue(FACING), state.getValue(ROLL), id);
    }

    /** 空手右键：若主手无物品，则从方块实体侧执行“取下灯泡”逻辑。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        return onBlockEntityUse(level, pos, be ->
                be.replaceBulb(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.FAIL);
    }

    /**
     * 手持物品右键：
     * <ul>
     *   <li>主手且物品是灯泡（ILightBulb）→ 尝试安装/替换灯泡；</li>
     *   <li>物品是染料 → 给可染色的灯泡染色。</li>
     * </ul>
     * 其余情况交给默认交互处理（例如扳手旋转）。
     */
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

    /**
     * 掉落物处理：在原版掉落基础上追加内部安装的灯泡（未烧毁时），
     * 保证拆掉灯具时灯泡不会凭空消失。
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof LightFixtureBlockEntity fixture) {
            ItemStack bulb = fixture.copyBulbStack();
            if (!bulb.isEmpty()) {
                var drops = new ArrayList<>(super.getDrops(state, params));
                drops.add(bulb);
                return drops;
            }
        }
        return super.getDrops(state, params);
    }

    /** 方块实体类型 Class，供 Create 的 IBE 机制使用。 */
    @Override
    public Class<LightFixtureBlockEntity> getBlockEntityClass() {
        return LightFixtureBlockEntity.class;
    }

    /** 方块实体类型注册项。 */
    @Override
    public BlockEntityType<? extends LightFixtureBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.LIGHT_FIXTURE.get();
    }
}
