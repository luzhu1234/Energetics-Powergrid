package com.energeticspowergrid.content.excitation.mixin;

import com.energeticspowergrid.EPGBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;

/**
 * Lets the vanilla rotor's placement helper accept the excitation stator item.
 * <p>
 * The helper is what makes aiming at a rotor and right-clicking snap a stator onto it with
 * {@code FACING} pointing back at the rotor and the correct {@code ROLL}. It only matched
 * electroenergetics' own stator item, so the excitation stator fell back to plain placement -
 * which points {@code FACING} away from the rotor. That is exactly the reversed-model,
 * never-couples behaviour: the model is identical to the vanilla stator's for the same state,
 * it was only ever placed facing the wrong way.
 * <p>
 * No other change is needed: the helper's transform sets {@code FACING}/{@code ROLL}, which the
 * excitation stator shares (same property instances), and {@code placeInWorld} places whatever
 * block item is being held, so the excitation stator lands on the rotor correctly.
 */
@Mixin(targets = "com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlock$PlacementHelper")
public abstract class AlternatorRotorBlockPlacementHelperMixin {
    @Inject(method = "getItemPredicate", at = @At("RETURN"), cancellable = true, remap = false)
    private void epg$alsoMatchExcitationStator(CallbackInfoReturnable<Predicate<ItemStack>> cir) {
        Predicate<ItemStack> original = cir.getReturnValue();
        cir.setReturnValue(stack -> original.test(stack)
                || stack.getItem() == EPGBlocks.EXCITATION_STATOR.get().asItem());
    }
}
