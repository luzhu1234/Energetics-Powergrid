package com.energeticspowergrid.content.excitation.mixin;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice;
import com.george_vi.electroenergetics.content.rotor.VirtualRotor;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the three-phase brushes droop when the load steps, the way a real alternator does, and
 * recover over a few seconds.
 * <p>
 * The offset lands in {@code virtualRotor.rpm} after the device has written it, so every micro tick
 * of the coming solve - the AC over-20 Hz simulation included - sees the shifted frequency. Only
 * values the device already maintains are read, the whole thing is a handful of float operations
 * per simulation tick, and it never touches the level or any block entity, so it is safe on the
 * off-thread simulator and costs nothing measurable.
 */
@Mixin(ThreePhaseAlternatorBrushesDevice.class)
public abstract class ThreePhaseAlternatorBrushesDeviceMixin {
    /** Load steps below this many watts are drift, not a step worth reacting to. */
    @Unique
    private static final float EPG$LOAD_STEP_DEADBAND = 1f;

    @Unique
    private float epg$dipHz;
    @Unique
    private float epg$dipPeak;
    @Unique
    private float epg$lastStress;
    @Unique
    private boolean epg$stressKnown;

    @Shadow(remap = false)
    public float rpmSpeed;
    @Shadow(remap = false)
    public float stress;
    @Shadow(remap = false)
    public VirtualRotor virtualRotor;

    @Inject(method = "preTick", at = @At("TAIL"), remap = false)
    private void epg$loadDependentFrequencyDip(CallbackInfo ci) {
        if (virtualRotor == null)
            return;

        float delta = epg$stressKnown ? stress - epg$lastStress : 0;
        epg$lastStress = stress;
        epg$stressKnown = true;

        float maxHz = Math.abs(EPGConfigs.server().frequencyDipMaxHz.getF());
        int decayTicks = Math.max(1, EPGConfigs.server().frequencyDipDecayTicks.get());

        if (Math.abs(delta) < EPG$LOAD_STEP_DEADBAND) {
            // Settled: bleed the offset away over the configured recovery window.
            if (epg$dipHz != 0) {
                float decay = epg$dipPeak / decayTicks;
                if (Math.abs(epg$dipHz) <= decay) {
                    epg$dipHz = 0;
                    epg$dipPeak = 0;
                } else {
                    epg$dipHz -= Math.signum(epg$dipHz) * decay;
                }
            }
        } else {
            // More generated power -> frequency sags; less -> it lifts.
            float step = -delta * EPGConfigs.server().frequencyDipHzPerKilowatt.getF() / 1000f;
            epg$dipHz = Mth.clamp(epg$dipHz + step, -maxHz, maxHz);
            epg$dipPeak = Math.max(epg$dipPeak, Math.abs(epg$dipHz));
        }

        if (epg$dipHz == 0)
            return;
        // A stationary machine is not generating, so it has no frequency to shift.
        if (rpmSpeed == 0)
            return;

        // virtualRotor.rpm is stored as an absolute value and hertz = rpm / hertzPerRPM, so shifting
        // the frequency is a scaled rpm shift. Clamping at zero keeps the frequency non-negative.
        float hertzPerRpm = Math.max(0.01f, EPGConfigs.server().hertzPerRpm.getF());
        float shifted = virtualRotor.rpm + epg$dipHz * hertzPerRpm;
        virtualRotor.rpm = Float.isFinite(shifted) ? Math.max(0, shifted) : Math.abs(rpmSpeed);
    }
}
