package com.energeticspowergrid.content.bell;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 电铃器件：电气上就是一个 20Ω 的纯电阻（移植自电气时代 PowerGrid 报警铃，
 * 阻值与其一致）——电流流过铃线圈，电磁铁反复敲击铃壳发声。
 * <p>
 * 解算完成后读取两端电压换算电流，映射为音量与音调推给方块实体：
 * <ul>
 * <li>音量：电流 ≥ 0.25A 才响，音量 = min(I × 2, 1)；</li>
 * <li>音调：电流 &lt; 0.5A 时低沉（0.75），之后随电流升高至 1.25。</li>
 * </ul>
 * 声音的实际播放在客户端（{@link ElectricBellBlockEntity} 的循环音效实例）。
 */
public class ElectricBellDevice extends SimpleElectricalDevice {
    /** 铃线圈电阻（欧姆），与电气时代原版一致。 */
    public static final double RESISTANCE = 20;

    public ElectricBellDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /**
     * 建立电路桥接：两端子之间接一个 20Ω 电阻。
     * 普通桥接，不设 defaultZeroPotential——电铃不能声明自己所在电路的地参考。
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(1, pos),
                ElectricalProperties.resistor(RESISTANCE));
    }

    /**
     * 解算完成后读取两端电压 → 电流 → 音量/音调，推给方块实体。
     * 仅服务端执行；方块实体经 sendData 把数值同步到客户端驱动音效。
     */
    @Override
    public void postTick(SimulationResults results) {
        double v = Math.abs(results.getVoltageAt(pos, 0) - results.getVoltageAt(pos, 1));
        double current = v / RESISTANCE;
        float volume = (float) (current < 0.25 ? 0 : Math.min(current * 2, 1));
        float pitch = (float) (current < 0.5 ? 0.75 : Math.min(0.5 + current * 0.5, 1.25));
        if (level.getBlockEntity(pos) instanceof ElectricBellBlockEntity be)
            be.setAudio(volume, pitch);
    }

    /** 方块不再是电铃时移除器件。 */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof ElectricBellBlock);
    }
}
