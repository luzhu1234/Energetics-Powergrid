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

public class HeatingCoilDevice extends SimpleElectricalDevice {
    public static final float AMBIENT_TEMPERATURE = 22f;
    public static final float OVERHEAT_TEMPERATURE = 600f;

    public HeatingCoilBlockEntity be;
    private float temperature = AMBIENT_TEMPERATURE;

    public HeatingCoilDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(pos).resistor(0, 1, EPGConfigs.server().heatingCoilResistance.getF());
    }

    @Override
    public void postTick(SimulationResults results) {
        double heat = results.getHeatLoss(pos, 0, 1);
        if (!Double.isFinite(heat))
            heat = 0;
        float maxPower = EPGConfigs.server().heatingCoilMaxPower.getF();
        float mass = Math.max(0.01f, EPGConfigs.server().heatingCoilMass.getF());
        float dissipation = (maxPower / (OVERHEAT_TEMPERATURE - AMBIENT_TEMPERATURE)) * (temperature - AMBIENT_TEMPERATURE);
        temperature += (float) ((heat - dissipation) / 20.0 / mass);
        if (!Float.isFinite(temperature) || temperature < AMBIENT_TEMPERATURE)
            temperature = AMBIENT_TEMPERATURE;
        if (temperature > OVERHEAT_TEMPERATURE)
            temperature = OVERHEAT_TEMPERATURE;

        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof HeatingCoilBlockEntity found)
            be = found;
        if (be != null) {
            if (be.isRemoved())
                be = null;
            else
                be.setTemperature(temperature);
        }
    }

    @Override
    public void read(CompoundTag tag) {
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT_TEMPERATURE;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Temperature", temperature);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof HeatingCoilBlock);
    }
}
