package com.energeticspowergrid.content.inverter;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
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
 * Pass-through / inverter. Terminals 1/2 (ids 0/1) are the DC input, 3/4 (ids 2/3) the AC output.
 * <p>
 * Blade open: the input is wired straight through to the output with a contact resistance.
 * Blade closed: the output carries a fixed-frequency square wave whose amplitude follows the
 * input voltage, and the input side is loaded with a resistor sized from the power the output
 * actually delivered (accumulated inside the solver), so nothing is created for free and an
 * unloaded inverter costs nothing.
 * <p>
 * All the per-tick state is two doubles plus a boolean; the wave itself is evaluated inside the
 * solver's micro ticks from captured primitives, so the per-tick cost on the main thread is a
 * handful of arithmetic operations.
 */
public class InverterDevice extends SimpleElectricalDevice {
    public boolean closed;

    /** Input power measured last tick, drives the input load resistor. */
    private double inputPower;
    /** |input voltage| measured last tick, sets the output amplitude. */
    private double targetVoltage;

    private final InverterElectricalProperties outputSource = new InverterElectricalProperties();

    public InverterDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof InverterBlock)
                closed = state.getValue(CutOffSwitchBlock.CLOSED);
        }

        var builder = bridges.builder(pos);
        if (!closed) {
            // 打开: straight through.
            double r = Math.max(0.0001, EPGConfigs.server().inverterResistance.getF());
            builder.resistor(0, 2, r);
            builder.resistor(1, 3, r);
            return;
        }

        // 闭合: inverter. Input draw follows last tick's delivered power; unloaded means a
        // megaohm, i.e. effectively nothing. The 10k cap keeps a mostly-idle input resistor
        // from amplifying stray coupling into a bogus voltage reading.
        double rIn = inputPower > 0.01 && targetVoltage > 0.01
                ? Math.min(10_000, Math.max(0.1, targetVoltage * targetVoltage / inputPower))
                : 1e6;
        builder.resistor(0, 1, rIn);

        outputSource.configure(targetVoltage,
                EPGConfigs.server().inverterOutputResistance.getF(),
                EPGConfigs.server().inverterFrequency.get(),
                level.getGameTime(), bridges.microTicks());
        builder.connect(2, 3, outputSource);
    }

    @Override
    public void postTick(SimulationResults results) {
        if (!closed) {
            inputPower = 0;
            targetVoltage = 0;
            return;
        }
        // The output element accumulated the power it actually delivered inside the solver;
        // sizing the input draw from it keeps energy honest. The input-voltage reading is only
        // trusted while power flows - across an idle megaohm resistor any stray coupling looks
        // like a huge voltage, which used to ratchet the output amplitude up until devices blew.
        inputPower = outputSource.pollDeliveredPower();
        if (inputPower > 0.01)
            targetVoltage = Math.abs(results.getVoltageAt(pos, 0, 1));
    }

    @Override
    public void read(CompoundTag tag) {
        closed = tag.getBoolean("Closed");
        inputPower = tag.getDouble("InputPower");
        targetVoltage = tag.getDouble("TargetVoltage");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putBoolean("Closed", closed);
        tag.putDouble("InputPower", inputPower);
        tag.putDouble("TargetVoltage", targetVoltage);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof InverterBlock);
    }
}
