package com.energeticspowergrid.content.heater;

import com.energeticspowergrid.config.EPGConfigs;
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
 * 加热线圈（Heating Coil）模拟电气设备。
 * <p>
 * 电气行为：preTick 把线圈建模为固定阻值的电阻（配置 heatingCoilResistance），
 * 通电后电阻热损耗（功率）转化为温度——postTick 读取 heat 并做热学积分：
 * 温度 += (发热 - 散热) / 20 / 热容。散热功率与温差成正比，
 * 比例系数按“最大加热功率时恰好稳定在过热温度”反推，因此温度有自然上限
 * {@link #OVERHEAT_TEMPERATURE}（600°C），不会无限升温。
 * <p>
 * 温度达到 200°C / 400°C 分别解锁烟熏 / 鼓风焙烧两档 Create 风扇加工
 * （状态判定与显示在 {@link HeatingCoilBlockEntity}）。
 * 温度上限与热容、最大功率均可在服务端配置中调整。
 */
public class HeatingCoilDevice extends SimpleElectricalDevice {
    /** 环境温度基准（°C）。 */
    public static final float AMBIENT_TEMPERATURE = 22f;
    /** 过热温度上限（°C）：散热系数按此值与最大功率标定，温度自然趋近但不超过它。 */
    public static final float OVERHEAT_TEMPERATURE = 600f;

    /** 缓存的方块实体引用，用于推送温度。 */
    public HeatingCoilBlockEntity be;
    /** 线圈温度（°C）。 */
    private float temperature = AMBIENT_TEMPERATURE;

    public HeatingCoilDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /**
     * 电路构建阶段：把节点 0-1 之间接入固定阻值电阻
     * （阻值取服务端配置 heatingCoilResistance，下限保护由配置端保证）。
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(pos).resistor(0, 1, EPGConfigs.server().heatingCoilResistance.getF());
    }

    /**
     * 模拟结果处理阶段：
     * <ol>
     *   <li>读取节点 0-1 的电阻热损耗 heat（异常值按 0 处理）；</li>
     *   <li>热学积分：散热 = (最大功率 / (600-22)) × 温差，即功率达上限时
     *       稳态温度恰为 600°C；温度再被钳制在 [22, 600] 区间；</li>
     *   <li>惰性查找 BE 并推送温度（BE 内部负责状态切换与风扇 blockUpdated）。</li>
     * </ol>
     */
    @Override
    public void postTick(SimulationResults results) {
        // 读取电阻热损耗功率，非有限值（未接入/数值异常）按 0 处理
        double heat = results.getHeatLoss(pos, 0, 1);
        if (!Double.isFinite(heat))
            heat = 0;
        float maxPower = EPGConfigs.server().heatingCoilMaxPower.getF();
        float mass = Math.max(0.01f, EPGConfigs.server().heatingCoilMass.getF());
        // 散热功率与温差成正比；比例系数使满功率时稳态温度恰为 600°C
        float dissipation = (maxPower / (OVERHEAT_TEMPERATURE - AMBIENT_TEMPERATURE)) * (temperature - AMBIENT_TEMPERATURE);
        temperature += (float) ((heat - dissipation) / 20.0 / mass);
        if (!Float.isFinite(temperature) || temperature < AMBIENT_TEMPERATURE)
            temperature = AMBIENT_TEMPERATURE;
        if (temperature > OVERHEAT_TEMPERATURE)
            temperature = OVERHEAT_TEMPERATURE;

        // 惰性查找并校验 BE 引用，然后推送温度
        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof HeatingCoilBlockEntity found)
            be = found;
        if (be != null) {
            if (be.isRemoved())
                be = null;
            else
                be.setTemperature(temperature);
        }
    }

    /** 从存档 NBT 读取温度。 */
    @Override
    public void read(CompoundTag tag) {
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT_TEMPERATURE;
    }

    /** 写入存档 NBT。 */
    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Temperature", temperature);
    }

    /** 方块不再是加热线圈时移除设备。 */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof HeatingCoilBlock);
    }
}
