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
 * NPN transistor. Node 0 = base, 1 = collector, 2 = emitter.
 * <p>
 * Two micro-ticked elements share one operating-point state: the base-emitter junction (a
 * piecewise diode with a configurable switch-on voltage) and the collector-emitter output
 * (a bounded current source of {@code beta * I_B}, capped at the configured max current).
 * See {@link TransistorElectricalProperties} for the model details.
 */
public class TransistorDevice extends SimpleElectricalDevice {
    public double beta = 100;
    public double vbeOn = 0.7;
    public double icMax = 1;

    private final TransistorElectricalProperties.SharedState state = new TransistorElectricalProperties.SharedState();
    private final TransistorElectricalProperties beJunction = new TransistorElectricalProperties(true, state);
    private final TransistorElectricalProperties ceOutput = new TransistorElectricalProperties(false, state);

    public TransistorDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        beJunction.configure(beta, vbeOn, icMax);
        ceOutput.configure(beta, vbeOn, icMax);
        // Plain bridges, no defaultZeroPotential: the transistor must not claim its circuit's
        // ground reference - grounding the wrong terminal (e.g. the base) would kill the drive.
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(2, pos), beJunction);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(2, pos), ceOutput);
    }

    @Override
    public void read(CompoundTag tag) {
        beta = Mth.clamp(tag.getDouble("Beta"), 1, 1000);
        vbeOn = Mth.clamp(tag.getDouble("VbeOn"), 0.05, 3);
        icMax = Mth.clamp(tag.getDouble("IcMax"), 0.001, 1000);
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putDouble("Beta", beta);
        tag.putDouble("VbeOn", vbeOn);
        tag.putDouble("IcMax", icMax);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof TransistorBlock);
    }
}
