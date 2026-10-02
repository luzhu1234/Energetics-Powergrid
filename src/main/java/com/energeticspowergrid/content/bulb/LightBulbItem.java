package com.energeticspowergrid.content.bulb;

import com.george_vi.electroenergetics.client.ElectricStatsTooltipModifier;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;
import net.minecraft.world.item.Item;

/**
 * 白炽灯泡物品基类。
 * <p>
 * 实现 {@link ILightBulb}，保存灯丝热学特性（额定功率/电压、电阻范围、
 * 热质量、工作温度等），供电力学模拟设备在电路求解时计算灯泡的
 * 发热、发光与过载烧毁行为。
 */
public class LightBulbItem extends Item implements ILightBulb {
    /** 灯丝热学特性（注册阶段由 setRated 注入） */
    private ThermalProperties thermalProperties;

    /**
     * 构造灯泡物品。
     *
     * @param properties 物品属性
     */
    public LightBulbItem(Properties properties) {
        super(properties);
    }

    /**
     * 设置该灯泡的热学特性参数（由注册工厂在 onRegister 时调用）。
     *
     * @param thermalProperties 热学特性记录
     */
    public void setThermalProperties(ThermalProperties thermalProperties) {
        this.thermalProperties = thermalProperties;
    }

    /**
     * 获取该灯泡的热学特性参数。
     *
     * @return 热学特性记录；未注册前为 null
     */
    @Override
    public ThermalProperties thermalProperties() {
        return thermalProperties;
    }

    /**
     * Registrate 注册变换器工厂：按额定参数构建热学特性，
     * 并为物品注册电气属性提示（额定电压、击穿电压、额定功率、最大电阻）。
     *
     * @param ratedPower 额定功率（W）
     * @param ratedVoltage 额定电压（V）
     * @param minResistance 最小电阻（Ω，冷态灯丝电阻下限）
     * @param thermalMass 热质量（越大升降温越慢）
     * @param maxPowerLevel 最大发光功率档位
     * @param dyeable 是否可染色
     * @param <I> 灯泡物品类型
     * @param <P> 父构建器类型
     * @return 应用于 ItemBuilder 的变换器
     */
    public static <I extends LightBulbItem, P> NonNullUnaryOperator<ItemBuilder<I, P>> setRated(float ratedPower, float ratedVoltage, float minResistance, float thermalMass, int maxPowerLevel, boolean dyeable) {
        // 由额定参数推导完整热学特性
        ThermalProperties thermal = ILightBulb.fromRated(ratedPower, ratedVoltage, minResistance, thermalMass, maxPowerLevel, dyeable);
        return b -> {
            // 注册时注入热学特性
            b.onRegister(item -> item.setThermalProperties(thermal));
            // 注册电气属性提示：显示额定电压/击穿电压/额定功率/最大电阻
            b.onRegister(item -> ElectricStatsTooltipModifier.ALL_ENTRIES.register(item, new ElectricStatsTooltipModifier.ElectricStatSet()
                    .addVoltage(() -> thermal.ratedVoltage())
                    .addMaxVoltage(() -> thermal.breakdownVoltage())
                    .addPower(() -> thermal.ratedPower())
                    .addResistance(() -> thermal.maxResistance())));
            return b;
        };
    }
}
