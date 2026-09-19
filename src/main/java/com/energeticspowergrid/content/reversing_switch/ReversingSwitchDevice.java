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

public class ReversingSwitchDevice extends SimpleElectricalDevice {
    public boolean closed;

    public ReversingSwitchDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ReversingSwitchBlock)
                closed = state.getValue(CutOffSwitchBlock.CLOSED);
        }

        double r = Math.max(0.0001, EPGConfigs.server().reversingSwitchResistance.getF());
        var builder = bridges.builder(pos);
        if (closed) {
            builder.resistor(0, 3, r);
            builder.resistor(1, 2, r);
        } else {
            builder.resistor(0, 2, r);
            builder.resistor(1, 3, r);
        }
    }

    @Override
    public void read(CompoundTag tag) {
        closed = tag.getBoolean("Closed");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Closed", closed);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof ReversingSwitchBlock);
    }
}
