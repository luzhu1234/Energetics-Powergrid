package com.energeticspowergrid.content.mosfet;

import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;

/**
 * P 沟道 MOS 管方块：端子与参数与 N 沟道完全相同，只是所有极性取反——
 * 源极处于高电位，栅极被拉低于源极超过 V_GS(th) 才导通，
 * 电流从源极流向漏极（体二极管方向也随之镜像）。
 */
public class PMosfetBlock extends MosfetBlock {
    public PMosfetBlock(Properties properties) {
        super(properties);
    }

    /** 覆写为 P 沟道：设备侧据此在 N <-> P 沟道切换时重建器件。 */
    @Override
    public boolean isPChannel() {
        return true;
    }

    /** 绑定 P 沟道模拟器件类型（数学模型与 N 沟道相同，仅极性取反）。 */
    @Override
    public SimulatedDeviceType<MosfetDevice> getDevice() {
        return EPGSimulatedDevices.P_MOSFET.get();
    }
}
