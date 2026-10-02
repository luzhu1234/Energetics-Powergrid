package com.energeticspowergrid.content.inverter;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 直通 / 逆变器。端子 1/2（id 0/1）是直流输入，3/4（id 2/3）是交流输出。
 * <p>
 * 闸刀断开：输入经一个接触电阻直连输出。
 * 闸刀闭合：输出端携带固定频率、幅值跟随输入电压的方波；输入侧则表现为一个
 * 由"输出实际发出的功率"（在求解器内部累计）反推大小的负载电阻——因此
 * 不会凭空发电，空载的逆变器也不消耗任何东西。
 * <p>
 * 每刻状态只有两个 double 和一个 boolean；波形本身在求解器的微刻内用
 * 捕获的原始量计算，主线程每刻的开销只有几次算术运算。
 */
public class InverterDevice extends SimpleElectricalDevice {
    /** 闸刀状态：false=直通模式，true=逆变模式。 */
    public boolean closed;

    /** 上一刻实测的输入功率，决定输入侧负载电阻的大小。 */
    private double inputPower;
    /** 上一刻实测的 |输入电压|，决定输出方波的幅值。 */
    private double targetVoltage;

    /** 输出侧的 Norton 方波源元件（微刻驱动）。 */
    private final InverterElectricalProperties outputSource = new InverterElectricalProperties();

    public InverterDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /**
     * 每刻开始前搭建电路桥接。
     * 先从方块状态同步 closed（方块的 CLOSED 是唯一权威来源，
     * 防止设备存档与方块状态不同步）。
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof InverterBlock)
                closed = state.getValue(CutOffSwitchBlock.CLOSED);
        }

        var builder = bridges.builder(pos);
        if (!closed) {
            // 断开：直通。输出端接两个接触电阻（正负两路各一），
            // 阻值下限 0.0001 Ω 防止除零/数值发散。
            double r = Math.max(0.0001, EPGConfigs.server().inverterResistance.getF());
            builder.resistor(0, 2, r);
            builder.resistor(1, 3, r);
            return;
        }

        // 闭合：逆变。输入侧的抽取功率跟随上一刻实际发出的功率；
        // 空载表现为 1 MΩ，等效于什么都不抽。10 kΩ 上限防止一个近乎空载的
        // 输入电阻把杂散耦合放大成虚假的电压读数。
        double rIn = inputPower > 0.01 && targetVoltage > 0.01
                ? Math.min(10_000, Math.max(0.1, targetVoltage * targetVoltage / inputPower))
                : 1e6;
        builder.resistor(0, 1, rIn);

        // 输出端接入方波源：幅值=上一刻实测输入电压，内阻与频率来自配置；
        // 基准微刻取当前游戏时间，保证频率在全局时间轴上稳定。
        outputSource.configure(targetVoltage,
                EPGConfigs.server().inverterOutputResistance.getF(),
                EPGConfigs.server().inverterFrequency.get(),
                level.getGameTime(), bridges.microTicks());
        builder.connect(2, 3, outputSource);
    }

    /**
     * 每刻结束后从求解结果更新工作点状态。
     * 断开时直接清零两个测量值（下一刻回到直通，无需保留历史）。
     */
    @Override
    public void postTick(SimulationResults results) {
        if (!closed) {
            inputPower = 0;
            targetVoltage = 0;
            return;
        }
        // 输出元件在求解器内部累计了它实际发出的功率；用这个功率来定输入侧
        // 抽取量，能量才守恒。输入电压读数只在有功率流动时才可信——跨一个
        // 空载 MΩ 电阻时任何杂散耦合都会显得像极高的电压，以前这会把输出
        // 幅值一路推高直到器件烧毁。
        inputPower = outputSource.pollDeliveredPower();
        if (inputPower > 0.01)
            targetVoltage = Math.abs(results.getVoltageAt(pos, 0, 1));
    }

    /** 从 NBT 恢复闸刀状态与两个测量值，保证重载后继续正常工作。 */
    @Override
    public void read(CompoundTag tag) {
        closed = tag.getBoolean("Closed");
        inputPower = tag.getDouble("InputPower");
        targetVoltage = tag.getDouble("TargetVoltage");
    }

    /** 把闸刀状态与两个测量值写入 NBT 持久化。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Closed", closed);
        tag.putDouble("InputPower", inputPower);
        tag.putDouble("TargetVoltage", targetVoltage);
    }

    /** 方块不再是逆变器时移除器件。 */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof InverterBlock);
    }
}
