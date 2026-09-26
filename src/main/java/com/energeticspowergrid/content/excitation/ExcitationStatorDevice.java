package com.energeticspowergrid.content.excitation;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A resistive winding between its two terminals. Applied voltage and current build an excitation
 * field {@code B = I * U * y} (y from config), which ramps up the same way the electric fan's speed
 * does. Averaging the sign-preserving I*U over the micro ticks makes an AC feed cancel itself out,
 * and the ramp keeps whatever ripple is left from doing any useful work.
 */
public class ExcitationStatorDevice extends SimpleElectricalDevice {
    /** Contact resistance joining the matching terminals of two in-line stators. */
    private static final double SHARE_RESISTANCE = 0.001;
    /** Below this the reading is noise, not real current. */
    private static final double IDLE_THRESHOLD = 0.1;

    public ExcitationStatorBlockEntity be;

    private float field;
    private double[] voltagesPositive;
    private double[] voltagesNegative;

    public ExcitationStatorDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public float getFieldStrength() {
        return field;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        double resistance = Math.max(0.1, EPGConfigs.server().excitationStatorResistance.getF());
        bridges.builder(pos).resistor(0, 1, resistance);

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ExcitationStatorBlock))
            return;
        Direction.Axis axis = ExcitationStatorBlock.terminalAxis(state);
        for (Direction.AxisDirection direction : Direction.AxisDirection.values())
            shareWith(bridges, pos.relative(axis, direction.getStep()));
    }

    /**
     * Parallels the same-numbered terminals of the neighbouring stator in the line, so a row of
     * stators stacks as one winding rather than a series chain.
     */
    private void shareWith(BridgeCollector bridges, BlockPos neighbour) {
        if (!level.isLoaded(neighbour))
            return;
        BlockState other = level.getBlockState(neighbour);
        if (!(other.getBlock() instanceof ExcitationStatorBlock))
            return;
        BlockState self = level.getBlockState(pos);
        if (other.getValue(ExcitationStatorBlock.FACING) != self.getValue(ExcitationStatorBlock.FACING)
                || other.getValue(ExcitationStatorBlock.ROLL) != self.getValue(ExcitationStatorBlock.ROLL))
            return;

        // Both overloads of bridge(Node, Node, double) exist on 1.1.x and later.
        ElectricalProperties share = ElectricalProperties.resistor(SHARE_RESISTANCE);
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(0, neighbour), share);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(1, neighbour), share);
    }

    @Override
    public void postTick(SimulationResults results) {
        double resistance = Math.max(0.1, EPGConfigs.server().excitationStatorResistance.getF());
        voltagesPositive = results.getVoltages(new InWorldNode(0, pos), voltagesPositive);
        voltagesNegative = results.getVoltages(new InWorldNode(1, pos), voltagesNegative);

        double target = 0;
        int samples = Math.min(voltagesPositive.length, voltagesNegative.length);
        if (samples > 0) {
            double sum = 0;
            for (int i = 0; i < samples; i++) {
                double voltage = voltagesPositive[i] - voltagesNegative[i];
                // Signed winding current, times the terminal voltage magnitude. Sign of the result
                // follows the polarity of the feed, exactly like the fan's signed power reading, so
                // reversing the supply reverses the field.
                double current = voltage / resistance;
                sum += current * Math.abs(voltage);
            }
            target = sum / samples * EPGConfigs.server().excitationStatorFieldFactor.getF();
        }
        if (Math.abs(target) < IDLE_THRESHOLD)
            target = 0;

        // Fan-style easing, as specified: 10% per tick toward the target, so the field builds in a
        // couple of seconds instead of snapping. The AC rejection happens before this, in the
        // micro-tick average, so the ramp never gets anything to chew on with an AC feed.
        field = Mth.lerp(0.1f, field, (float) target);
        if (!Float.isFinite(field))
            field = 0;

        if (be == null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ExcitationStatorBlockEntity found)
            be = found;
        if (be != null) {
            if (be.isRemoved())
                be = null;
            else
                be.setFieldStrength(field);
        }
    }

    @Override
    public void read(CompoundTag tag) {
        field = tag.getFloat("Field");
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Field", field);
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof ExcitationStatorBlock);
    }
}
