package com.energeticspowergrid.content.excitation.mixin;

import com.energeticspowergrid.EPGBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;

/**
 * 让原版转子的放置助手（PlacementHelper）接受励磁定子物品。
 * <p>
 * 这个助手实现了"瞄准转子右键即吸附"的体验：定子会被贴到转子上，
 * {@code FACING} 指回转子，并自动给出正确的 {@code ROLL}。但它原本只匹配
 * electroenergetics 自家的定子物品，励磁定子会退化为普通放置——
 * 而 {@code FACING} 会背对转子。这恰好导致"模型反向、永远无法耦合"的 bug：
 * 同一状态下的模型与原版定子完全相同，问题只是放置时朝向反了。
 * <p>
 * 此外无需任何其他修改：助手的 transform 会设置 {@code FACING}/{@code ROLL}
 * （励磁定子与原版定子共用同一批属性实例），而 {@code placeInWorld} 会放置
 * 手持物品对应的方块，励磁定子自然能正确落到转子上。
 */
@Mixin(targets = "com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlock$PlacementHelper")
public abstract class AlternatorRotorBlockPlacementHelperMixin {
    /**
     * 在 RETURN 注入：拿到原判定谓词后，包一层"或"逻辑——
     * 原判定通过，或手持物品是励磁定子，都视为可吸附放置。
     * 用 @Mixin(targets=) 是因为 PlacementHelper 是原版类的内部类，
     * 无法直接以类型方式引用。
     */
    @Inject(method = "getItemPredicate", at = @At("RETURN"), cancellable = true, remap = false)
    private void epg$alsoMatchExcitationStator(CallbackInfoReturnable<Predicate<ItemStack>> cir) {
        Predicate<ItemStack> original = cir.getReturnValue();
        // 保留原判定结果，仅追加对励磁定子物品的匹配，不影响原版定子的行为
        cir.setReturnValue(stack -> original.test(stack)
                || stack.getItem() == EPGBlocks.EXCITATION_STATOR.get().asItem());
    }
}
