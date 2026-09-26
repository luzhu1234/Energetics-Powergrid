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

public class EPGSimulatedDevices {
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES =
            DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, EnergeticsPowerGrid.ID);

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<LightFixtureDevice>> LIGHT_FIXTURE =
            DEVICES.register("light_fixture", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("light_fixture"),
                    (type, level, pos, sd) -> new LightFixtureDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<FactoryLightDevice>> FACTORY_LIGHT =
            DEVICES.register("factory_light", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("factory_light"),
                    (type, level, pos, sd) -> new FactoryLightDevice(level, pos, sd, type)));

    // Disabled: duplicates electroenergetics:electric_fan
    // public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ElectricFanDevice>> ELECTRIC_FAN =
    //         DEVICES.register("electric_fan", () -> new SimulatedDeviceType<>(
    //                 EnergeticsPowerGrid.rl("electric_fan"),
    //                 (type, level, pos, sd) -> new ElectricFanDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<HeatingCoilDevice>> HEATING_COIL =
            DEVICES.register("heating_coil", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("heating_coil"),
                    (type, level, pos, sd) -> new HeatingCoilDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ReversingSwitchDevice>> REVERSING_SWITCH =
            DEVICES.register("reversing_switch", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("reversing_switch"),
                    (type, level, pos, sd) -> new ReversingSwitchDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ExcitationStatorDevice>> EXCITATION_STATOR =
            DEVICES.register("excitation_stator", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("excitation_stator"),
                    (type, level, pos, sd) -> new ExcitationStatorDevice(level, pos, sd, type)));

    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<com.energeticspowergrid.content.inverter.InverterDevice>> INVERTER =
            DEVICES.register("inverter", () -> new SimulatedDeviceType<>(
                    EnergeticsPowerGrid.rl("inverter"),
                    (type, level, pos, sd) -> new com.energeticspowergrid.content.inverter.InverterDevice(level, pos, sd, type)));

    public static void register(IEventBus bus) {
        DEVICES.register(bus);
    }
}
