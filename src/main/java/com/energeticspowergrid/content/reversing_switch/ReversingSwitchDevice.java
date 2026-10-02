package com.energeticspowergrid.content.reversing_switch;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 换向开关（Reversing Switch）模拟电气设备。
 * <p>
 * 四端子换向开关的电路建模（每 tick 在 preTick 中构建）：
 * <ul>
 *   <li>closed = true（换向）：0→3、1→2 之间各接一个电阻；</li>
 *   <li>closed = false（常规）：0→2、1→3 之间各接一个电阻。</li>
 * </ul>
 * 即通过切换两组电阻的接法，把输出对（2,3）与输入对（0,1）的连接关系对调，
 * 实现电路“换向”（例如反转供电极性或切换两条馈线）。
 * 电阻值取服务端配置 reversingSwitchResistance，下限 0.0001Ω 防止除零。
 */
public class ReversingSwitchDevice extends SimpleElectricalDevice {
    /** 当前是否处于“换向（闭合）”状态；由方块状态 CLOSED 同步。 */
    public boolean closed;

    public ReversingSwitchDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /**
     * 电路构建阶段：
     * 先从方块状态同步 closed 标记（以方块为准，避免不一致），
     * 然后按状态在四个端子间接入两枚等值电阻——
     * 换向时交叉连接（0-3、1-2），常规时平行连接（0-2、1-3）。
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        // 以方块状态为准同步闭合标记
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ReversingSwitchBlock)
                closed = state.getValue(CutOffSwitchBlock.CLOSED);
        }

        // 接触电阻：配置值，下限 0.0001Ω 保护模拟器数值稳定
        double r = Math.max(0.0001, EPGConfigs.server().reversingSwitchResistance.getF());
        var builder = bridges.builder(pos);
        if (closed) {
            // 换向：交叉连接
            builder.resistor(0, 3, r);
            builder.resistor(1, 2, r);
        } else {
            // 常规：平行连接
            builder.resistor(0, 2, r);
            builder.resistor(1, 3, r);
        }
    }

    /** 从存档 NBT 读取闭合标记。 */
    @Override
    public void read(CompoundTag tag) {
        closed = tag.getBoolean("Closed");
    }

    /** 写入存档 NBT。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Closed", closed);
    }

    /** 方块不再是换向开关时移除设备。 */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof ReversingSwitchBlock);
    }
}
