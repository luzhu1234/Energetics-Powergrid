package com.energeticspowergrid.content.transistor;

import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;

/**
 * PNP 对应方块：端子与参数与 NPN 完全相同，只是所有极性取反——
 * 发射极处于高电位，基极被拉低于发射极才导通，
 * 集电极电流从发射极流向集电极。
 */
public class PnpTransistorBlock extends TransistorBlock {
    public PnpTransistorBlock(Properties properties) {
        super(properties);
    }

    /** 覆写为 PNP：设备侧据此在 NPN <-> PNP 切换时重建器件。 */
    @Override
    public boolean isPnp() {
        return true;
    }

    /** 绑定 PNP 模拟器件类型（数学模型与 NPN 相同，仅极性取反）。 */
    @Override
    public SimulatedDeviceType<TransistorDevice> getDevice() {
        return EPGSimulatedDevices.PNP_TRANSISTOR.get();
    }
}
