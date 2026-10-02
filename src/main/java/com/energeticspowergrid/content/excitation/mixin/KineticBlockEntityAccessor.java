package com.energeticspowergrid.content.excitation.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link KineticBlockEntity} 的访问器：暴露父类的 {@code lastStressApplied}
 * 字段。该字段是 Create 在动力网络重建时重放应力记账的依据，分数版
 * 应力计算必须同步写它，否则重进世界/区块重载后应力会回退到旧值。
 */
@Mixin(value = KineticBlockEntity.class, remap = false)
public interface KineticBlockEntityAccessor {
    @Accessor("lastStressApplied")
    void epg$setLastStressApplied(float value);

    @Accessor("lastStressApplied")
    float epg$getLastStressApplied();
}
