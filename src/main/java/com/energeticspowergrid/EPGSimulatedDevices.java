package com.energeticspowergrid;

import com.energeticspowergrid.content.excitation.ExcitationStatorDevice;
import com.energeticspowergrid.content.factorylight.FactoryLightDevice;
// import com.energeticspowergrid.content.fan.ElectricFanDevice;
import com.energeticspowergrid.content.fixture.LightFixtureDevice;
import com.energeticspowergrid.content.heater.HeatingCoilDevice;
import com.energeticspowergrid.content.reversing_switch.ReversingSwitchDevice;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的电力学模拟设备类型注册类。
 * <p>
 * 向电力学（Electro Energetics）的模拟设备注册表
 * {@code CEERegistries.SIMULATED_DEVICE_TYPE} 注册各电气设备的
 * {@link SimulatedDeviceType}，每种类型携带一个工厂 lambda，
 * 在电路模拟时按位置创建对应的设备实例参与电网求解。
 */
public class EPGSimulatedDevices {
    /** 模拟设备类型的延迟注册器，挂载到电力学的自定义注册表 */
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, EnergeticsPowerGrid.ID);

    /** 灯具固定座设备：模拟灯泡的电阻负载与发光档位 */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<LightFixtureDevice>> LIGHT_FIXTURE =
            DEVICES.register("light_fixture", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("light_fixture"),
                    (type, level, pos, sd) -> new LightFixtureDevice(level, pos, sd, type)));

    /** 工厂灯设备：模拟工厂灯的负载、光照与投射光块行为 */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<FactoryLightDevice>> FACTORY_LIGHT =
            DEVICES.register("factory_light", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("factory_light"),
                    (type, level, pos, sd) -> new FactoryLightDevice(level, pos, sd, type)));

    // Disabled: duplicates electroenergetics:electric_fan
    // 电动鼓风机设备已禁用：与电力学原版的电动风扇功能重复
    // public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ElectricFanDevice>> ELECTRIC_FAN =
    //         DEVICES.register("electric_fan", () -> new SimulatedDeviceType<>(
    //                 EnergeticsPowerGrid.rl("electric_fan"),
    //                 (type, level, pos, sd) -> new ElectricFanDevice(level, pos, sd, type)));

    /** 加热线圈设备：模拟线圈的电阻发热与温度变化 */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<HeatingCoilDevice>> HEATING_COIL =
            DEVICES.register("heating_coil", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("heating_coil"),
                    (type, level, pos, sd) -> new HeatingCoilDevice(level, pos, sd, type)));

    /** 换向开关设备：模拟双掷切换，改变电流通过方向 */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ReversingSwitchDevice>> REVERSING_SWITCH =
            DEVICES.register("reversing_switch", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("reversing_switch"),
                    (type, level, pos, sd) -> new ReversingSwitchDevice(level, pos, sd, type)));

    /** 励磁定子设备：模拟定子绕组通电后为转子提供励磁磁场 */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ExcitationStatorDevice>> EXCITATION_STATOR =
            DEVICES.register("excitation_stator", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("excitation_stator"),
                    (type, level, pos, sd) -> new ExcitationStatorDevice(level, pos, sd, type)));

    /** NPN 三极管设备：模拟 NPN 型半导体开关/放大特性 */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<com.energeticspowergrid.content.transistor.TransistorDevice>> NPN_TRANSISTOR =
            DEVICES.register("npn_transistor", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("npn_transistor"),
                    (type, level, pos, sd) -> new com.energeticspowergrid.content.transistor.TransistorDevice(level, pos, sd, type)));

    /**
     * PNP 三极管设备：与 NPN 共用 TransistorDevice 实现，
     * 工厂 lambda 中将 pnp 标志置为 true 以区分极性。
     */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<com.energeticspowergrid.content.transistor.TransistorDevice>> PNP_TRANSISTOR =
            DEVICES.register("pnp_transistor", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("pnp_transistor"),
                    (type, level, pos, sd) -> {
                        com.energeticspowergrid.content.transistor.TransistorDevice device =
                                new com.energeticspowergrid.content.transistor.TransistorDevice(level, pos, sd, type);
                        device.pnp = true;
                        return device;
                    }));

    /** N 沟道 MOS 管设备：电压驱动开关，栅极绝缘零电流 + 低导通电阻 + 体二极管 */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<com.energeticspowergrid.content.mosfet.MosfetDevice>> N_MOSFET =
            DEVICES.register("n_mosfet", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("n_mosfet"),
                    (type, level, pos, sd) -> new com.energeticspowergrid.content.mosfet.MosfetDevice(level, pos, sd, type)));

    /**
     * P 沟道 MOS 管设备：与 N 沟道共用 MosfetDevice 实现，
     * 工厂 lambda 中将 pChannel 标志置为 true 以区分极性。
     */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<com.energeticspowergrid.content.mosfet.MosfetDevice>> P_MOSFET =
            DEVICES.register("p_mosfet", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("p_mosfet"),
                    (type, level, pos, sd) -> {
                        com.energeticspowergrid.content.mosfet.MosfetDevice device =
                                new com.energeticspowergrid.content.mosfet.MosfetDevice(level, pos, sd, type);
                        device.pChannel = true;
                        return device;
                    }));

    /** 电铃设备：20Ω 纯电阻，通电后按电流大小敲铃发声（移植自电气时代报警铃） */
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<com.energeticspowergrid.content.bell.ElectricBellDevice>> ELECTRIC_BELL =
            DEVICES.register("electric_bell", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("electric_bell"),
                    (type, level, pos, sd) -> new com.energeticspowergrid.content.bell.ElectricBellDevice(level, pos, sd, type)));

    /**
     * 将设备注册器挂载到模组事件总线上。
     *
     * @param bus 模组事件总线
     */
    public static void register(IEventBus bus) {
        DEVICES.register(bus);
    }
}
