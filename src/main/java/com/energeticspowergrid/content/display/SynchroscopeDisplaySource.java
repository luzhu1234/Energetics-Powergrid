package com.energeticspowergrid.content.display;

import com.energeticspowergrid.content.excitation.mixin.SynchroscopeBlockEntityAccessor;
import com.george_vi.electroenergetics.content.synchroscope.SynchroscopeBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 同步钟的显示连接器数据源：把相位差表盘读成 12 小时钟面时间（xx h xx min）。
 * <p>
 * 同步钟表盘等效一个 12 小时的钟面——360° 相位差对应 12 小时（每度 2 分钟）：
 * 0°（同相）读作 12 h 0 min，90° 读作 3 h 0 min，180° 读作 6 h 0 min，
 * 270° 读作 9 h 0 min。连接无效（未对准两路电网）时输出空白。
 */
public class SynchroscopeDisplaySource extends NumericSingleLineDisplaySource {
    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof SynchroscopeBlockEntity be))
            return ZERO.copy();
        SynchroscopeBlockEntityAccessor accessor = (SynchroscopeBlockEntityAccessor) be;
        if (!accessor.epg$isValidConnection())
            return ZERO.copy();

        float phaseOffset = accessor.epg$getPhaseOffset();
        // 归一化到 [0, 360) 后换算成 12 小时钟面的分钟数（每度 2 分钟）
        float totalMinutes = ((phaseOffset % 360) + 360) % 360 * 2f;
        int hours = (int) (totalMinutes / 60);
        if (hours == 0)
            hours = 12;
        int minutes = (int) (totalMinutes % 60);
        return Component.literal(hours + " h " + minutes + " min");
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }
}
