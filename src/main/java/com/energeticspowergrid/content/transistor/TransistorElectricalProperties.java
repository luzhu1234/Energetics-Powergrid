package com.energeticspowergrid.content.transistor;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;

/**
 * One element of the NPN transistor model, driven from a shared operating-point state:
 * <p>
 * - Base-emitter (junction): a piecewise diode. Off below the switch-on voltage (near-open),
 * on above it, modeled as the Thevenin equivalent of {@code V_BE(on)} in series with the
 * internal base spreading resistance - solved in Norton form like the alternator's windings,
 * which clamps the junction at {@code V_BE(on) + I_B * R_BE} exactly like a real diode drop.
 * <p>
 * Collector-emitter: a Norton source of {@code I_C = min(beta * I_B, I_C max)} whose parallel
 * resistance linearizes around the previous micro tick's operating point
 * ({@code R = clamp(V_CE / I_C, R_sat, R_off)}). Heavy loading naturally slides it into
 * saturation (the {@code R_sat} floor), an open collector stays bounded at the previous
 * voltage instead of exploding, and reverse bias blocks.
 * <p>
 * The shared state is written in {@code afterTick} and read in {@code tick}, so each solve
 * runs on the previous micro tick's operating point - one sub-step of lag, no feedback loops.
 * Only primitives cross the thread boundary; the solver runs off thread.
 */
public class TransistorElectricalProperties extends MicroTickingElectricalProperties {

    /** Operating point shared by both elements of one transistor. */
    public static class SharedState {
        public double vbe;
        public double vce;
    }

    private static final double BASE_RESISTANCE = 10;
    private static final double SATURATION_RESISTANCE = 2;
    private static final double OFF_RESISTANCE = 1e6;
    private static final double EMF_EPSILON = 1e-9;

    private final boolean junction;
    private final SharedState state;
    private double beta = 100;
    private double vbeOn = 0.7;
    private double icMax = 1;

    public TransistorElectricalProperties(boolean junction, SharedState state) {
        this.junction = junction;
        this.state = state;
    }

    public void configure(double beta, double vbeOn, double icMax) {
        this.beta = beta;
        this.vbeOn = vbeOn;
        this.icMax = icMax;
        this.voltageSource = 0;
    }

    private double baseCurrent() {
        return state.vbe > vbeOn ? (state.vbe - vbeOn) / BASE_RESISTANCE : 0;
    }

    @Override
    public void tick(double[] allVoltages, int microTick, int totalMicroTicks, int n1, int n2) {
        if (junction) {
            resistance = state.vbe > vbeOn ? BASE_RESISTANCE : OFF_RESISTANCE;
            currentSource = state.vbe > vbeOn ? vbeOn / BASE_RESISTANCE : 0;
            return;
        }

        double ic = Math.min(beta * baseCurrent(), icMax);
        if (state.vce < 0 || ic < EMF_EPSILON) {
            // Reverse biased or no drive: blocking.
            resistance = OFF_RESISTANCE;
            currentSource = 0;
            return;
        }
        // Active region, linearized around the last operating point; the R_sat floor is what
        // saturation looks like in this model - a heavy load drags V_CE down to a couple volts
        // of equivalent series resistance instead of following the current source down.
        resistance = Math.max(SATURATION_RESISTANCE, Math.min(OFF_RESISTANCE, state.vce / ic));
        currentSource = ic;
    }

    @Override
    public void afterTick(double[] allVoltages, int n1, int n2, int microTick, int totalMicroTicks) {
        double vd = allVoltages[n1 * totalMicroTicks + microTick]
                - allVoltages[n2 * totalMicroTicks + microTick];
        if (junction)
            state.vbe = vd;
        else
            state.vce = vd;
    }
}
