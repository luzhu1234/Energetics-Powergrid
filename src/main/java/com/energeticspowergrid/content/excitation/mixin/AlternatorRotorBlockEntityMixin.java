package com.energeticspowergrid.content.excitation.mixin;

import com.energeticspowergrid.config.EPGConfigs;
import com.energeticspowergrid.content.excitation.ExcitationFields;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetwork;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rewrites the magnet count the rotor carries so that it is the *effective* count: vanilla
 * stators count for a tenth (configurable), and surrounding excitation stators add their field
 * equivalent, 1000 field per stator.
 * <p>
 * The effective count lives in the real {@code magnets} field, so every consumer agrees by
 * construction with zero extra bookkeeping: the vanilla stress formula (and its
 * {@code lastStressApplied} record, which Create replays on network rebuilds after reloads), the
 * brushes' power output, and the NBT round trip all read one and the same number - stress and
 * generation weaken together, and save/load can never desync them.
 * <p>
 * The client never rescans: it would only see the vanilla stators and clobber the excitation
 * share (excitation lives in server-side device data), so it relies on the synced count the
 * server pushes whenever the number moves. Both sides then run the identical vanilla formulas
 * over identical data.
 */
@Mixin(AlternatorRotorBlockEntity.class)
public abstract class AlternatorRotorBlockEntityMixin {
    /** Vanilla adds 3 magnets per stator, so this rate makes 1000 field equal one stator. */
    @Unique
    private static final double EPG$MAGNETS_PER_STATOR = 3;

    @Shadow(remap = false)
    int magnets;

    @Inject(method = "lazyTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void epg$excitationLazyTick(CallbackInfo ci) {
        ci.cancel();
        AlternatorRotorBlockEntity self = (AlternatorRotorBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel))
            return; // client: keep the synced count, never overwrite it with a vanilla-only scan

        double effectiveMagnets = ExcitationFields.vanillaMagnets(serverLevel, self.getBlockPos(), self.getBlockState())
                * EPGConfigs.server().vanillaStatorStressFactor.getF()
                + EPG$MAGNETS_PER_STATOR
                * Math.abs(ExcitationFields.excitationSum(serverLevel, self.getBlockPos(), self.getBlockState()))
                / Math.max(1, EPGConfigs.server().excitationFieldPerStator.getF());

        int magnetCount = effectiveMagnets >= Integer.MAX_VALUE / 2d
                ? Integer.MAX_VALUE / 2
                : (int) Math.max(0, Math.round(effectiveMagnets));
        // Mirrors vanilla: while settled, no network churn at all.
        if (this.magnets == magnetCount)
            return;

        this.magnets = magnetCount;
        if (self.hasNetwork()) {
            self.getOrCreateNetwork().updateStressFor(self, self.calculateStressApplied());
            // Vanilla sends this alongside a stress update so the client cannot believe the rotor
            // is overstressed when the server says otherwise.
            self.sendData();
        }
    }
}
