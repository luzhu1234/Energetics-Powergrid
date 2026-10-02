package com.energeticspowergrid;

import com.energeticspowergrid.content.heater.HeaterBlastingType;
import com.energeticspowergrid.content.heater.HeaterSmokingType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的 Create 风扇处理类型注册类。
 * <p>
 * 向 Create 的风扇处理类型注册表注册加热线圈相关的两种
 * 气流处理效果：当气流经过通电的加热线圈时，分别获得
 * 熔炼（blasting）与烟熏（smoking）效果。
 */
public class EPGFanProcessingTypes {
    /** 风扇处理类型的延迟注册器，挂载到 Create 的自定义注册表 */
    private static final DeferredRegister<FanProcessingType> TYPES =
            DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, EnergeticsPowerGrid.ID);

    static {
        // 加热熔炼类型：气流经加热线圈后可将生矿/生铁类物品熔炼为锭
        TYPES.register("heater_blasting", HeaterBlastingType::new);
        // 加热烟熏类型：气流经低温加热线圈后可将食物烟熏为熟食
        TYPES.register("heater_smoking", HeaterSmokingType::new);
    }

    /**
     * 将风扇处理类型注册器挂载到模组事件总线上。
     *
     * @param bus 模组事件总线
     */
    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
