package com.energeticspowergrid;

import com.energeticspowergrid.content.display.FrequencyMeterDisplaySource;
import com.energeticspowergrid.content.display.SynchroscopeDisplaySource;
import com.george_vi.electroenergetics.CEEBlockEntityTypes;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import static com.energeticspowergrid.EnergeticsPowerGrid.REGISTRATE;

/**
 * 显示连接器数据源注册。
 * <p>
 * 为电力学的频率表与同步钟补上显示连接器支持（与电压表/电流表的
 * VoltageDisplaySource/AmperageDisplaySource 同一套机制）：
 * 频率表输出三位小数频率，同步钟把相位差表盘读成 12 小时钟面时间。
 * <p>
 * 电力学的 BE 类型注册无法事后追加 .displaySource(...) 构建器，因此
 * 注册完成后在公共初始化阶段通过 Create 的 {@code BY_BLOCK_ENTITY}
 * 注册表把数据源补挂到对应的方块实体类型上。
 */
public class EPGDisplaySources {
    public static final RegistryEntry<DisplaySource, FrequencyMeterDisplaySource> FREQUENCY_METER =
            REGISTRATE.displaySource("frequency_meter", FrequencyMeterDisplaySource::new).register();
    public static final RegistryEntry<DisplaySource, SynchroscopeDisplaySource> SYNCHROSCOPE =
            REGISTRATE.displaySource("synchroscope", SynchroscopeDisplaySource::new).register();

    public static void register() {
        // 静态字段随类加载完成 Registrate 注册
    }

    /**
     * 在公共初始化阶段把数据源绑到电力学的方块实体类型上。
     * 由主类挂到 FMLCommonSetupEvent（enqueueWork 保证主线程执行）。
     *
     * @param event 公共初始化事件
     */
    public static void attachToCEEBlockEntities(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK_ENTITY.add(CEEBlockEntityTypes.FREQUENCY_METER.get(), FREQUENCY_METER.get());
            DisplaySource.BY_BLOCK_ENTITY.add(CEEBlockEntityTypes.SYNCHROSCOPE.get(), SYNCHROSCOPE.get());
        });
    }
}
