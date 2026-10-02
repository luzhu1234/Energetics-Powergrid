package com.energeticspowergrid.content.display;

import com.energeticspowergrid.content.excitation.mixin.FrequencyMeterBlockEntityAccessor;
import com.george_vi.electroenergetics.content.frequency_meter.FrequencyMeterBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * 频率表的显示连接器数据源：输出三位小数的当前频率（xx.xxx Hz）。
 * <p>
 * 电力学频率表的护目镜读数只保留 2 位小数，但内部 {@code frequency} 字段
 * 本身是全精度 float——这里经由访问器 mixin 直接读取原始值并格式化为
 * 3 位小数，分辨率高于方块自带的显示。
 */
public class FrequencyMeterDisplaySource extends NumericSingleLineDisplaySource {
    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof FrequencyMeterBlockEntity be))
            return ZERO.copy();
        // 电力学的 goggles 只显示 2 位小数；这里读内部 float 输出 3 位
        float frequency = ((FrequencyMeterBlockEntityAccessor) be).epg$getFrequency();
        return Component.literal(String.format(Locale.ROOT, "%.3f", frequency) + " Hz");
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }
}
