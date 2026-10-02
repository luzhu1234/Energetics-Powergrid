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

/**
 * 工厂灯（Factory Light）方块。
 * <p>
 * 可沿水平轴拼接成长条灯组：PART 属性 0 为单体，1/2/3 为 Z 轴灯组的负端/中段/正端，
 * 4/5/6 为 X 轴灯组的负端/中段/正端。相邻工厂灯会通过 updateShape 自动改写 PART
 * 使灯组形状保持一致；拆除相邻段时也会自动收缩。
 * <p>
 * 电气上每个方块是一个独立电阻负载（亮度档 POWER 0~3，其中 1 表示“装了灯但未通电”），
 * 同轴相邻灯组之间通过极低阻值互连以共享电流（见 {@link FactoryLightDevice#shareWith}）。
 * 点亮时向下投射生成虚拟光照方块 {@link FactoryLightLightBlock} 提供照明。
 */
public class FactoryLightBlock extends SimpleElectricalDeviceBlock<FactoryLightDevice> implements IBE<FactoryLightBlockEntity> {
    /** 水平轴属性：灯组延伸方向（X 或 Z）。 */
    public static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** 灯组段位属性：0=单体，1/2/3=Z轴（负端/中段/正端），4/5/6=X轴（负端/中段/正端）。 */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 6);
    /**
     * 亮度/状态档位：0 = 无灯泡，1 = 有灯泡但熄灭，2 = 低亮度，3 = 满亮度。
     * （注意与设备 glow 值差 1，glow 0~2 映射到 POWER 1~3。）
     */
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 3);

    /** 单体灯的碰撞箱（下半部悬空的方形灯罩）。 */
    public static final VoxelShape SHAPE_SINGLE = box(2, 8, 2, 14, 16, 14);
    /** 灯组端部段碰撞箱：比单体更长（延伸一格），按水平朝向整形。 */
    public static final VoxelShaper SHAPER_ENDS = VoxelShaper.forHorizontal(box(2, 8, 2, 14, 16, 16), Direction.NORTH);
    /** 灯组中段碰撞箱：沿轴向贯穿整格。 */
    public static final VoxelShaper SHAPER_MIDDLE = VoxelShaper.forHorizontalAxis(box(2, 8, 0, 14, 16, 16), Direction.Axis.Z);

    /** Z 轴灯组的节点配置：两个接线点位于顶面 x=5 与 x=11 处。 */
    public static final NodeConfigurator NODES_Z = new NodeConfigurator.Builder()
            .add(5f, 16f, 8f)
            .add(11f, 16f, 8f)
            .simple(Direction.UP);
    /** X 轴灯组的节点配置：由 Z 轴配置绕 Y 轴旋转 90° 得到。 */
    public static final NodeConfigurator NODES_X = NODES_Z.rotate(new Vec3(0, 90, 0));

    public FactoryLightBlock(Properties properties) {
        super(properties);
        // 默认状态：Z 轴单体、熄灭
        registerDefaultState(defaultBlockState()
                .setValue(HORIZONTAL_AXIS, Direction.Axis.Z)
                .setValue(PART, 0)
                .setValue(POWER, 0));
    }

    /** 注册方块状态属性（仅轴/段位/亮度，本方块无 FACING）。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_AXIS, PART, POWER);
    }

    /** 声明光照等级动态变化。 */
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    /** 按亮度档位返回光照等级：档 2 → 低亮度光照，档 3 → 满亮度光照。 */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (state.getValue(POWER)) {
            case 2 -> ILightBulb.LIGHT_LEVEL_LOW_POWER;
            case 3 -> ILightBulb.LIGHT_LEVEL_FULL_POWER;
            default -> 0;
        };
    }

    /** 按段位返回对应碰撞箱：单体 / 某方向端部 / 某轴中段。 */
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

    /**
     * 判断邻居灯段能否沿 dir 方向与本方块连接（拼接合法性检查）：
     * 单体总可连；轴不一致、中段、端部朝向不符的情况均不可连。
     */
    private static boolean canConnect(Direction dir, BlockState neighbour) {
        int part = neighbour.getValue(PART);
        var axis = neighbour.getValue(HORIZONTAL_AXIS);
        if (part == 0)
            return true;
        boolean isZ = axis == Direction.Axis.Z;
        boolean isX = axis == Direction.Axis.X;
        // 段位与轴不匹配 → 不可连
        if (isZ && (part < 1 || part > 3))
            return false;
        if (isX && (part < 4 || part > 6))
            return false;
        // 中段不能作为连接起点
        if ((isZ && part == 2) || (isX && part == 5))
            return false;
        // 轴向必须一致
        if (axis != dir.getAxis())
            return false;
        // 端部的“开口”方向必须与连接方向一致
        if ((part == 1 || part == 4) && dir.getAxisDirection() == Direction.AxisDirection.POSITIVE)
            return false;
        if ((part == 3 || part == 6) && dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE)
            return false;
        return true;
    }

    /**
     * 放置时的初始状态：
     * <ul>
     *   <li>若点击的面贴着另一个工厂灯且可连接（非上下方向）→ 作为该灯组的延伸段放置，
     *       段位由方向正负确定（Z 轴 1/3，X 轴 4/6）；</li>
     *   <li>否则放置为单体，轴取玩家水平朝向的轴（潜行时旋转 90°）。</li>
     * </ul>
     */
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

    /** 是否中段（Z 轴 2 / X 轴 5）。 */
    public static boolean isCenter(int part) {
        return part == 2 || part == 5;
    }

    /** 是否负向端部（Z 轴 1 / X 轴 4）。 */
    public static boolean isNegativeEdge(int part) {
        return part == 1 || part == 4;
    }

    /** 是否正向端部（Z 轴 3 / X 轴 6）。 */
    public static boolean isPositiveEdge(int part) {
        return part == 3 || part == 6;
    }

    /** 取指定轴的中段段位。 */
    private static int getCenter(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 2 : 5;
    }

    /** 取指定轴的负向端部段位。 */
    private static int getNegativeEdge(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 1 : 4;
    }

    /** 取指定轴的正向端部段位。 */
    private static int getPositiveEdge(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 3 : 6;
    }

    /**
     * 邻居方块变化时的形状维护（灯组自动拼接/收缩核心逻辑）：
     * <ul>
     *   <li>邻居是同轴工厂灯：若自己原来在灯组尾部而新邻居继续向外延伸，
     *       则把自己从单体/端部升级为中段或另一端；两端相接时端部升级为中段；</li>
     *   <li>邻居变为空气：若原来连接方向悬空，则从中段退化为对应端部，
     *       或从端部退化为单体。</li>
     * </ul>
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        int part = state.getValue(PART);
        if (neighborState.is(this)) {
            if (neighborState.getValue(HORIZONTAL_AXIS) == direction.getAxis()) {
                int nPart = neighborState.getValue(PART);
                var axis = direction.getAxis();
                var axisDir = direction.getAxisDirection();
                // 单体 + 正向出现新灯段 → 自己变成负向端部
                if (part == 0 && axisDir == Direction.AxisDirection.POSITIVE && (isPositiveEdge(nPart) || isCenter(nPart)))
                    return state.setValue(HORIZONTAL_AXIS, axis).setValue(PART, getNegativeEdge(axis));
                // 单体 + 负向出现新灯段 → 自己变成正向端部
                if (part == 0 && axisDir == Direction.AxisDirection.NEGATIVE && (isNegativeEdge(nPart) || isCenter(nPart)))
                    return state.setValue(HORIZONTAL_AXIS, axis).setValue(PART, getPositiveEdge(axis));
                // 负端 + 负向继续延伸 → 升级为中段
                if (isNegativeEdge(part) && axisDir == Direction.AxisDirection.NEGATIVE && (isNegativeEdge(nPart) || isCenter(nPart)))
                    return state.setValue(PART, getCenter(axis));
                // 正端 + 正向继续延伸 → 升级为中段
                if (isPositiveEdge(part) && axisDir == Direction.AxisDirection.POSITIVE && (isPositiveEdge(nPart) || isCenter(nPart)))
                    return state.setValue(PART, getCenter(axis));
            }
        } else if (neighborState.is(Blocks.AIR)) {
            var axis = state.getValue(HORIZONTAL_AXIS);
            if (axis == direction.getAxis()) {
                // 连接方向悬空 → 降级：端部变单体、中段变端部
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

    /** 空手右键：取下灯泡（同灯具）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        return onBlockEntityUse(level, pos, be ->
                be.replaceBulb(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.FAIL);
    }

    /** 手持物品右键：灯泡安装/替换、染料染色，其余交给默认交互。 */
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

    /** 拆除方块时清除其向下投射的全部虚拟光照方块。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            var be = getBlockEntity(level, pos);
            if (be != null)
                be.removeLights();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 掉落物处理：追加安装的灯泡（未烧毁时），同灯具。 */
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

    /** 结构旋转：轴随旋转方向变换。 */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(HORIZONTAL_AXIS);
        return state.setValue(HORIZONTAL_AXIS,
                rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE, axis)).getAxis());
    }

    /** 镜像不改变状态（灯组沿轴对称）。 */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    /** 返回对应的模拟设备类型（FACTORY_LIGHT）。 */
    @Override
    public SimulatedDeviceType<FactoryLightDevice> getDevice() {
        return EPGSimulatedDevices.FACTORY_LIGHT.get();
    }

    /** 返回按灯组轴向选择的节点坐标表。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodesFor(state).getNodes(Direction.UP);
    }

    /** 返回指定编号节点的坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodesFor(state).getNodePos(Direction.UP, id);
    }

    /** 按灯组轴向选择节点配置（X 轴用旋转版）。 */
    private static NodeConfigurator nodesFor(BlockState state) {
        return state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X ? NODES_X : NODES_Z;
    }

    /** 方块实体类型 Class。 */
    @Override
    public Class<FactoryLightBlockEntity> getBlockEntityClass() {
        return FactoryLightBlockEntity.class;
    }

    /** 方块实体类型注册项。 */
    @Override
    public BlockEntityType<? extends FactoryLightBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.FACTORY_LIGHT.get();
    }
}
