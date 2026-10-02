package com.energeticspowergrid.content.excitation.mixin;

import com.george_vi.electroenergetics.content.frequency_meter.FrequencyMeterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 频率表方块实体的访问器：暴露包级私有的 {@code frequency} 字段。
 * 供显示连接器数据源读取全精度频率（护目镜只显示 2 位小数，
 * 显示连接器需要 3 位）。
 */
@Mixin(FrequencyMeterBlockEntity.class)
public interface FrequencyMeterBlockEntityAccessor {
    @Accessor("frequency")
    float epg$getFrequency();
}
