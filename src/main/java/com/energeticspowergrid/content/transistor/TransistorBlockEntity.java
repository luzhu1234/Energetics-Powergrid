package com.energeticspowergrid.content.transistor;

import java.util.List;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;

/**
 * 三极管方块实体：承载三个可调参数（β、V_BE(on)、I_C max）的滚轮菜单，
 * 并在参数变化时把新值同步到世界存档中的 {@link TransistorDevice}。
 */
public class TransistorBlockEntity extends SmartBlockEntity {
    /** 电流放大倍数 β 菜单。 */
    protected TransistorScrollValues.Beta beta;
    /** 基射极导通电压 V_BE(on) 菜单。 */
    protected TransistorScrollValues.Vbe vbe;
    /** 最大集电极电流 I_C max 菜单。 */
    protected TransistorScrollValues.IcMax icMax;

    public TransistorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 注册三个参数菜单，分别挂在模型三个引脚上方的槽位。
     * 每个菜单的回调都会触发 updateTransistor()，保证设备侧参数即时生效。
     */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 三个参数菜单分别挂在模型侧面的一个槽位上（每根引脚一个）。
        beta = new TransistorScrollValues.Beta(
                Component.translatable("energeticspowergrid.gui.transistor.beta"), this, new ParamSlot(0));
        beta.withCallback(i -> updateTransistor());
        behaviours.add(beta);

        vbe = new TransistorScrollValues.Vbe(
                Component.translatable("energeticspowergrid.gui.transistor.vbe"), this, new ParamSlot(1));
        vbe.withCallback(i -> updateTransistor());
        behaviours.add(vbe);

        icMax = new TransistorScrollValues.IcMax(
                Component.translatable("energeticspowergrid.gui.transistor.icmax"), this, new ParamSlot(2));
        icMax.withCallback(i -> updateTransistor());
        behaviours.add(icMax);
    }

    /** 当前 β 值（供方块/设备读取）。 */
    public double getBeta() {
        return beta.getBeta();
    }

    /** 当前导通电压 V_BE(on)（单位：伏）。 */
    public double getVbeOn() {
        return vbe.getVbeOn();
    }

    /** 当前最大集电极电流 I_C max（单位：安）。 */
    public double getIcMax() {
        return icMax.getIcMax();
    }

    /**
     * 把三个参数同步到存档中的设备实例。
     * 仅在服务端执行（设备数据只存在于服务端的 DevicesSavedData）；
     * 回调在参数滚轮变化时被触发，这样设备下一次求解前就能拿到新参数。
     */
    private void updateTransistor() {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        TransistorDevice device = DevicesSavedData.load(serverLevel).getDevice(worldPosition, TransistorDevice.class);
        if (device != null) {
            device.beta = getBeta();
            device.vbeOn = getVbeOn();
            device.icMax = getIcMax();
        }
    }

    /**
     * 三个参数槽位的模型空间位置（未旋转，正面朝南时的基准坐标，单位：像素）：
     * β（放大倍数）在模型正上方，V_BE 与 I_C 并排放在下方两侧——刻意拉开
     * 间距且允许超出模型包围盒，方便阅读与点击。正面平面与
     * {@link TransistorBlock#NODES} 的引脚平面（z = 9.75）一致。
     */
    private static final NodeConfigurator SLOTS =
            new NodeConfigurator.Builder()
                    .add(8f, 11f, 9.75f)   // β：顶部居中
                    .add(5f, 6f, 9.75f)    // V_BE：下方左侧
                    .add(11f, 6f, 9.75f)   // I_C：下方右侧
                    .simple(Direction.UP);

    /** 单个参数菜单的放置槽位：按索引选取 SLOTS 中的一个偏移点。 */
    private static class ParamSlot extends ValueBoxTransform {
        private final int index;

        ParamSlot(int index) {
            this.index = index;
        }

        /** 根据滚转状态旋转槽位配置，再取本槽位索引对应的局部偏移。 */
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            boolean roll = state.getValue(DirectionalRolledDeviceBlock.ROLL);
            NodeConfigurator rotated = roll
                    ? SLOTS.rotate(new Vec3(0, -90, 0))
                    : SLOTS;
            return rotated.getNodePos(state.getValue(DirectionalRolledDeviceBlock.FACING), index);
        }

        /**
         * 决定菜单文本面的朝向与旋转角度。
         * 文本面的姿态必须与偏移点的旋转保持同一约定，否则文字会"钉死"在
         * 南向不随方块转动。采用 Create 的标准竖直面公式
         * {@code yRot = horizontalAngle(朝向) + 180}：
         * <ul>
         * <li>水平朝向——偏移点旋转后落在模型上方，参数框平躺，阅读方向
         *     随方块正面转动；</li>
         * <li>朝上——偏移点不旋转，参数框立在模型正面（南面），竖直朝向；</li>
         * <li>朝下——偏移点绕 X 轴翻转 180°，参数框立在北面。</li>
         * </ul>
         * 滚转会使正面绕 Y 轴偏转 90°，yRot 相应减 90。
         */
        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction facing = state.getValue(DirectionalRolledDeviceBlock.FACING);
            boolean roll = state.getValue(DirectionalRolledDeviceBlock.ROLL);
            float yRot;
            float xRot;
            if (facing.getAxis().isHorizontal()) {
                yRot = AngleHelper.horizontalAngle(facing) + 180f;
                xRot = 90f;
            } else if (facing == Direction.UP) {
                yRot = 180f;
                xRot = 0f;
            } else {
                yRot = 0f;
                xRot = 0f;
            }
            if (roll)
                yRot -= 90f;
            TransformStack.of(ms)
                    .rotateYDegrees(yRot)
                    .rotateXDegrees(xRot);
        }
    }
}
