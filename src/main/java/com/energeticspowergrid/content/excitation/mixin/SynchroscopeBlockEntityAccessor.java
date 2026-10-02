package com.energeticspowergrid.content.excitation.mixin;

import com.george_vi.electroenergetics.content.synchroscope.SynchroscopeBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 同步钟方块实体的访问器：暴露包级私有的 {@code phaseOffset} 字段与
 * {@code validConnection} 标志。供显示连接器数据源把相位差表盘读成
 * 12 小时钟面时间（xx h xx min）。
 */
@Mixin(SynchroscopeBlockEntity.class)
public interface SynchroscopeBlockEntityAccessor {
    @Accessor("phaseOffset")
    float epg$getPhaseOffset();

    @Accessor("validConnection")
    boolean epg$isValidConnection();
}
