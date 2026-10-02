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
    /**
     * 电气节点配置：定义两个接线点在方块内的相对坐标（x=3.5 和 x=12.5，y=2.5，z=8），
     * 模拟电路通过这两个节点把灯具作为电阻接入电网。
     */
    public static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(3.5f, 2.5f, 8f)
            .add(12.5f, 2.5f, 8f)
            .simple(Direction.UP);

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

    /** 返回按朝向与滚动（ROLL）旋转后的全部节点坐标。 */
    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return NODES.getNodes(state.getValue(FACING), state.getValue(ROLL));
    }

    /** 返回指定编号节点的世界相对坐标。 */
    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return NODES.getNodePos(state.getValue(FACING), state.getValue(ROLL), id);
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
