package com.energeticspowergrid.content.inverter;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import com.google.common.util.concurrent.AtomicDouble;

/**
 * The inverter's output element: a Norton source whose EMF flips sign on a fixed square wave.
 * Re-evaluated every micro tick by the solver, so the wave keeps its exact frequency in game
 * time no matter how the sub-steps divide - the same mechanism as the alternator's phase
 * windings.
 * <p>
 * The Norton form (current source {@code v/R} in parallel with {@code R}) is not a style choice:
 * stamped as an ideal voltage source the element would have unbounded current and make the
 * solver's matrix non-SPD, which let connected devices get hammered and the solve diverge.
 * Norton keeps the matrix conductance-based and limits the current to {@code v/R}.
 * <p>
 * Only primitives captured at {@code configure} time are touched; the electrical simulation runs
 * off thread and this class must never reach into blocks, block entities or levels.
 */
public class InverterElectricalProperties extends MicroTickingElectricalProperties {
    private double amplitude;
    private double seriesResistance;
    private double halfPeriodMicroTicks;
    private long baseMicroTick;

    /** Energy delivered during the last solve, in joules, accumulated off thread. */
    private final AtomicDouble deliveredEnergy = new AtomicDouble();

    /**
     * @param amplitude        output peak voltage (follows the measured input voltage)
     * @param seriesResistance output internal resistance
     * @param frequency        wave frequency in Hz
     * @param gameTime         the world's game time, captured on the server thread
     * @param totalMicroTicks  micro ticks per game tick
     */
    public void configure(double amplitude, double seriesResistance, double frequency, long gameTime, int totalMicroTicks) {
        this.amplitude = amplitude;
        this.seriesResistance = Math.max(0.01, seriesResistance);
        // One game tick is 0.05 s, so half a wave at <frequency> Hz lasts 10 / frequency game ticks.
        this.halfPeriodMicroTicks = Math.max(0.25, (10.0 / frequency) * totalMicroTicks);
        this.baseMicroTick = gameTime * totalMicroTicks;
        this.voltageSource = 0;
        this.currentSource = 0;
        this.resistance = amplitude > 0.01 ? this.seriesResistance : 1e3;
        deliveredEnergy.set(0);
    }

    /** Average power the output delivered during the last solve, in watts. */
    public double pollDeliveredPower() {
        return deliveredEnergy.getAndSet(0);
    }

    @Override
    public void tick(double[] allVoltages, int microTick, int totalMicroTicks, int n1, int n2) {
        if (amplitude <= 0.01) {
            this.resistance = 1e3;
            this.currentSource = 0;
            return;
        }
        this.resistance = seriesResistance;
        long halfCycle = (long) ((baseMicroTick + microTick) / halfPeriodMicroTicks);
        double emf = (halfCycle & 1) == 0 ? amplitude : -amplitude;
        this.currentSource = emf / seriesResistance;
    }

    @Override
    public void afterTick(double[] allVoltages, int n1, int n2, int microTick, int totalMicroTicks) {
        if (amplitude <= 0.01)
            return;
        long halfCycle = (long) ((baseMicroTick + microTick) / halfPeriodMicroTicks);
        double emf = (halfCycle & 1) == 0 ? amplitude : -amplitude;
        double vd = allVoltages[n1 * totalMicroTicks + microTick]
                - allVoltages[n2 * totalMicroTicks + microTick];
        double current = (emf - vd) / seriesResistance;
        deliveredEnergy.addAndGet(Math.abs(current * vd) / totalMicroTicks);
    }
}
