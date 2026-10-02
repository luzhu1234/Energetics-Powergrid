package com.energeticspowergrid.content.transistor;

import java.util.List;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

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
     * 三个参数槽位，每个引脚上方一个，悬浮在模型本体之上。
     * x 坐标与 {@link TransistorBlock#NODES} 中的引脚中心（5、8、11）一致，
     * 使每个菜单正对其引脚；复用节点配置器的旋转逻辑，菜单随方块朝向
     * 与滚转完全同步移动，就像引脚一样。
     */
    private static final com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator SLOTS =
            new com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator.Builder()
                    .add(5f, 7.5f, 9.75f)
                    .add(8f, 7.5f, 9.75f)
                    .add(11f, 7.5f, 9.75f)
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
            boolean roll = state.getValue(com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock.ROLL);
            com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator rotated = roll
                    ? SLOTS.rotate(new Vec3(0, -90, 0))
                    : SLOTS;
            return rotated.getNodePos(state.getValue(CreativeBatteryBlock.FACING), index);
        }

        /** 决定菜单文本面的朝向与旋转角度。 */
        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction facing = state.getValue(CreativeBatteryBlock.FACING);
            // 文本面贴在模型的平面上：朝上时在南面，水平朝向时在模型本体上方，
            // 朝下时在北面。
            Direction face = facing == Direction.UP ? Direction.SOUTH
                    : facing == Direction.DOWN ? Direction.NORTH : Direction.UP;
            float yRot = (face.get2DDataValue() & 3) * 90f + 180f;
            float xRot = face == Direction.UP ? 90 : face == Direction.DOWN ? 270 : 0;
            TransformStack.of(ms)
                    .rotateYDegrees(yRot)
                    .rotateXDegrees(xRot);
        }
    }
}
