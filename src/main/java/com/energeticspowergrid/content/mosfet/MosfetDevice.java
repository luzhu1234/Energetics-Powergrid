package com.energeticspowergrid.content.mosfet;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MOS 管器件（N 沟道或 P 沟道）。端子 0=栅极 G、1=漏极 D、2=源极 S。
 * <p>
 * 两个元件共享同一份工作点状态：栅源绝缘（1GΩ——电压驱动的物理来源，
 * 稳态栅极电流约等于零）和漏源沟道（阈值导通 + R_DS(on) 低导通电阻 +
 * 体二极管）。与三极管相比：没有 β、不需要基极电流，控制只看电压；
 * 导通损耗低一个数量级以上，适合低功耗与大电流开关场景。
 * 模型细节见 {@link MosfetElectricalProperties}。
 * P 沟道变体运行同一套数学，只是所有极性取反（源极接高电位、栅极拉低导通）。
 */
public class MosfetDevice extends SimpleElectricalDevice {
    /** 阈值电压 V_GS(th)（单位：伏）。 */
    public double vth = 2;
    /** 导通电阻 R_DS(on)（单位：欧姆）。 */
    public double rdsOn = 0.1;
    /** 最大漏极电流 I_D max（单位：安）。 */
    public double idMax = 10;
    /** P 沟道变体为 true；由器件类型工厂一次性设定。 */
    public boolean pChannel;

    /** 两个元件共享的工作点（上一微刻的 V_GS）。 */
    private final MosfetElectricalProperties.SharedState state = new MosfetElectricalProperties.SharedState();
    /** 栅源绝缘元件。 */
    private final MosfetElectricalProperties gateInsulation = new MosfetElectricalProperties(true, state);
    /** 漏源沟道元件。 */
    private final MosfetElectricalProperties dsChannel = new MosfetElectricalProperties(false, state);

    public MosfetDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /**
     * 每个游戏刻开始前把三个可调参数下发到两个元件，并建立电路桥接：
     * 栅源绝缘桥接 G-S（先桥接，让 V_GS 先于沟道更新），漏源沟道桥接 D-S。
     * 使用普通桥接而不设 defaultZeroPotential：MOS 管不能抢占其所在电路的
     * 地参考——如果把错误的端子（比如栅极）接地，驱动信号会被直接吞掉。
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        state.pChannel = pChannel;
        gateInsulation.configure(vth, rdsOn, idMax);
        dsChannel.configure(vth, rdsOn, idMax);
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(2, pos), gateInsulation);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(2, pos), dsChannel);
    }

    /** 从 NBT 读取参数并用 Mth.clamp 钳制到合法区间，防止存档被改出异常值。 */
    @Override
    public void read(CompoundTag tag) {
        vth = Mth.clamp(tag.getDouble("Vth"), 0.1, 100);
        rdsOn = Mth.clamp(tag.getDouble("RdsOn"), 0.001, 100);
        idMax = Mth.clamp(tag.getDouble("IdMax"), 0.001, 1000);
    }

    /** 把三个参数写入 NBT 持久化。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Vth", vth);
        tag.putDouble("RdsOn", rdsOn);
        tag.putDouble("IdMax", idMax);
    }

    /**
     * 判断方块变化时是否需要重建器件：
     * 换成 N 沟道 <-> P 沟道时必须重建，让极性跟随方块本身。
     */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof MosfetBlock block) || block.isPChannel() != pChannel;
    }
}
