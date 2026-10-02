package com.energeticspowergrid.content.transistor;

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
 * 三极管器件（NPN 或 PNP）。端子 0=基极、1=集电极、2=发射极。
 * <p>
 * 两个微刻驱动的元件共享同一份工作点状态：基射结（一个带可配置导通电压的
 * 分段二极管）和集射输出（一个 {@code beta * I_B} 的受限电流源，上限为
 * 配置的最大电流）。模型细节见 {@link TransistorElectricalProperties}。
 * PNP 变体运行同一套数学，只是所有极性取反。
 */
public class TransistorDevice extends SimpleElectricalDevice {
    /** 电流放大倍数 β（玩家可调，1..1000）。 */
    public double beta = 100;
    /** 基射极导通电压 V_BE(on)（单位：伏）。 */
    public double vbeOn = 0.7;
    /** 最大集电极电流 I_C max（单位：安），用于限制输出电流源。 */
    public double icMax = 1;
    /** PNP 变体为 true；由器件类型工厂一次性设定。 */
    public boolean pnp;

    /** 两个元件共享的工作点（上一微刻的 V_BE / V_CE）。 */
    private final TransistorElectricalProperties.SharedState state = new TransistorElectricalProperties.SharedState();
    /** 基射结元件（junction=true 表示结二极管模型）。 */
    private final TransistorElectricalProperties beJunction = new TransistorElectricalProperties(true, state);
    /** 集射输出元件（受控电流源模型）。 */
    private final TransistorElectricalProperties ceOutput = new TransistorElectricalProperties(false, state);

    public TransistorDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /**
     * 每个游戏刻开始前把三个可调参数下发到两个元件，并建立电路桥接：
     * 基射结桥接 B-E，集射输出桥接 C-E。
     * 使用普通桥接而不设 defaultZeroPotential：三极管不能抢占其所在电路的
     * 地参考——如果把错误的端子（比如基极）接地，驱动信号会被直接吞掉。
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        state.pnp = pnp;
        beJunction.configure(beta, vbeOn, icMax);
        ceOutput.configure(beta, vbeOn, icMax);
        // 普通桥接，不设 defaultZeroPotential：三极管不能声明自己所在电路的地参考
        // ——把错误的端子（如基极）接地会毁掉驱动信号。
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(2, pos), beJunction);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(2, pos), ceOutput);
    }

    /** 从 NBT 读取参数并用 Mth.clamp 钳制到合法区间，防止存档被改出异常值。 */
    @Override
    public void read(CompoundTag tag) {
        beta = Mth.clamp(tag.getDouble("Beta"), 1, 1000);
        vbeOn = Mth.clamp(tag.getDouble("VbeOn"), 0.05, 3);
        icMax = Mth.clamp(tag.getDouble("IcMax"), 0.001, 1000);
    }

    /** 把三个参数写入 NBT 持久化。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Beta", beta);
        tag.putDouble("VbeOn", vbeOn);
        tag.putDouble("IcMax", icMax);
    }

    /**
     * 判断方块变化时是否需要重建器件：
     * 换成 NPN <-> PNP 时必须重建，让极性跟随方块本身。
     */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        // 在 NPN <-> PNP 之间切换时必须重建器件，让极性跟随方块。
        return !(newState.getBlock() instanceof TransistorBlock block) || block.isPnp() != pnp;
    }
}