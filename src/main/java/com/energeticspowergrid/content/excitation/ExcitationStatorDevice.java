package com.energeticspowergrid.content.excitation;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 励磁定子的电气设备：一个接在两个端子之间的电阻绕组。
 * <p>
 * 施加电压产生电流后，按 {@code B = I · U · y}（y 为配置项）建立励磁场强。交流馈电时，
 * 对"带符号的 I×U"做微刻平均会正负抵消——交流自动无效；而场强爬升发生在平均之后，
 * 残留的纹波也不会被爬升放大。场强上升曲线与电动风扇转速一致（每 tick 逼近 10%）。
 */
public class ExcitationStatorDevice extends SimpleElectricalDevice {
    /** 相邻两个共线定子的对应端子之间的接触电阻（并联用，越小越接近直连）。 */
    private static final double SHARE_RESISTANCE = 0.001;
    /** 平均场强低于该值视为噪声（而非真实电流），直接归零。 */
    private static final double IDLE_THRESHOLD = 0.1;

    /** 对应的方块实体（护目镜数据显示用），postTick 时惰性获取。 */
    public ExcitationStatorBlockEntity be;

    /** 当前励磁场强（带符号：馈电反接则为负），随爬升曲线逼近目标值。 */
    private float field;
    /** 两个端子在各微刻下的电压数组（postTick 从求解结果读取，复用缓冲避免每 tick 分配）。 */
    private double[] voltagesPositive;
    private double[] voltagesNegative;

    public ExcitationStatorDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public float getFieldStrength() {
        return field;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        // 两个端子之间放一个绕组电阻（配置值，下限 0.1 防短路）。
        double resistance = Math.max(0.1, EPGConfigs.server().excitationStatorResistance.getF());
        bridges.builder(pos).resistor(0, 1, resistance);

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ExcitationStatorBlock))
            return;
        // 端子朝向的轴线：向轴线两侧各看一格，尝试与相邻定子并联。
        Direction.Axis axis = ExcitationStatorBlock.terminalAxis(state);
        for (Direction.AxisDirection direction : Direction.AxisDirection.values())
            shareWith(bridges, pos.relative(axis, direction.getStep()));
    }

    /**
     * 与轴线方向相邻的定子"同名端子并联"（0 接 0、1 接 1），这样一排定子等效为一个绕组
     * （场强相加）而不是串联链（电压分压）。只有朝向与滚转完全一致的邻居才参与并联。
     */
    private void shareWith(BridgeCollector bridges, BlockPos neighbour) {
        if (!level.isLoaded(neighbour))
            return;
        BlockState other = level.getBlockState(neighbour);
        if (!(other.getBlock() instanceof ExcitationStatorBlock))
            return;
        BlockState self = level.getBlockState(pos);
        if (other.getValue(ExcitationStatorBlock.FACING) != self.getValue(ExcitationStatorBlock.FACING)
                || other.getValue(ExcitationStatorBlock.ROLL) != self.getValue(ExcitationStatorBlock.ROLL))
            return;

        // 同名端子之间用极低接触电阻桥接（该 bridge 重载在电力学 1.1.x+ 均存在）。
        ElectricalProperties share = ElectricalProperties.resistor(SHARE_RESISTANCE);
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(0, neighbour), share);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(1, neighbour), share);
    }

    @Override
    public void postTick(SimulationResults results) {
        double resistance = Math.max(0.1, EPGConfigs.server().excitationStatorResistance.getF());
        // 读取两个端子在本 tick 全部微刻下的电压（数组复用，避免每 tick 分配）。
        voltagesPositive = results.getVoltages(new InWorldNode(0, pos), voltagesPositive);
        voltagesNegative = results.getVoltages(new InWorldNode(1, pos), voltagesNegative);

        // 目标场强 = 微刻平均( 带符号电流 × 端子电压幅值 ) × 配置系数 y。
        // 符号跟随馈电极性（与风扇的带符号功率读数同理），反接电源则场强为负；
        // 交流馈电时正负半周在该平均中相互抵消，实现"交流不产生励磁"。
        double target = 0;
        int samples = Math.min(voltagesPositive.length, voltagesNegative.length);
        if (samples > 0) {
            double sum = 0;
            for (int i = 0; i < samples; i++) {
                double voltage = voltagesPositive[i] - voltagesNegative[i];
                double current = voltage / resistance;
                sum += current * Math.abs(voltage);
            }
            target = sum / samples * EPGConfigs.server().excitationStatorFieldFactor.getF();
        }
        // 低于噪声阈值直接归零，避免杂散耦合读数被缓慢爬升放大。
        if (Math.abs(target) < IDLE_THRESHOLD)
            target = 0;

        // 与电动风扇一致的缓动：每 tick 向目标逼近 10%，场强 2~3 秒内平滑建立。
        // 交流抑制发生在上面的微刻平均阶段，爬升本身拿到的一直是直流目标值。
        field = Mth.lerp(0.1f, field, (float) target);
        if (!Float.isFinite(field))
            field = 0;

        // 惰性绑定方块实体，把场强同步给它（护目镜 tooltip 显示用）；差值过小时 BE 自己会忽略。
        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ExcitationStatorBlockEntity found)
            be = found;
        if (be != null) {
            if (be.isRemoved())
                be = null;
            else
                be.setFieldStrength(field);
        }
    }

    @Override
    public void read(CompoundTag tag) {
        // 存档恢复场强，避免每次进世界都重新爬升。
        field = tag.getFloat("Field");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Field", field);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof ExcitationStatorBlock);
    }
}
