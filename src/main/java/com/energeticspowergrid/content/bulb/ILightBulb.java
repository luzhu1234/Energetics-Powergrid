package com.energeticspowergrid.content.bulb;

import net.minecraft.world.item.Item;

/**
 * 灯泡行为接口。
 * <p>
 * 定义灯泡的热学特性记录与由额定参数推导特性的工厂方法，
 * 电力学模拟设备据此计算灯丝电阻随温度的变化、
 * 发热与过载烧毁行为。
 */
public interface ILightBulb {
    /** 低功率档位的光照等级 */
    int LIGHT_LEVEL_LOW_POWER = 10;
    /** 满功率档位的光照等级 */
    int LIGHT_LEVEL_FULL_POWER = 15;

    /**
     * 灯丝热学特性记录。
     *
     * @param dissipationFactor 散热系数（额定功率 / 工作温差，决定散热速率）
     * @param thermalMass 热质量（越大升降温越慢）
     * @param overheatTemperature 过热温度（°C，超过即烧毁，默认为工作温度 +400）
     * @param minResistance 最小电阻（Ω，冷态灯丝电阻下限）
     * @param maxResistance 最大电阻（Ω，由额定电压²/额定功率推导的工作电阻）
     * @param operatingTemperature 工作温度（°C，默认 1450）
     * @param ratedPower 额定功率（W）
     * @param ratedVoltage 额定电压（V）
     * @param breakdownVoltage 击穿电压（V，默认为额定电压的 1.5 倍，超过即烧毁）
     * @param maxPowerLevel 最大发光功率档位
     * @param dyeable 是否可染色
     */
    record ThermalProperties(float dissipationFactor, float thermalMass, float overheatTemperature, float minResistance, float maxResistance, float operatingTemperature, float ratedPower, float ratedVoltage, float breakdownVoltage, int maxPowerLevel, boolean dyeable) {
    }

    /**
     * 获取该灯泡的热学特性。
     *
     * @return 热学特性记录
     */
    ThermalProperties thermalProperties();

    /**
     * 灯丝电阻随温度的线性函数（模拟金属正温度系数）。
     * 电阻从冷态最小电阻随温度线性增长到工作温度下的最大电阻。
     *
     * @param temperature 当前温度（°C）
     * @return 该温度下的灯丝电阻（Ω）
     */
    default float resistanceFunction(float temperature) {
        ThermalProperties p = thermalProperties();
        return p.minResistance() + ((p.maxResistance() - p.minResistance()) / p.operatingTemperature()) * temperature;
    }

    /**
     * 由额定参数推导完整热学特性的工厂方法。
     * 推导规则：
     * <ul>
     *   <li>最大电阻 = 额定电压² / 额定功率（工作点电阻）</li>
     *   <li>工作温度固定为 1450°C（白炽灯丝典型温度）</li>
     *   <li>散热系数 = 额定功率 / (工作温度 - 22°C 环境温度)</li>
     *   <li>过热温度 = 工作温度 + 400°C</li>
     *   <li>击穿电压 = 额定电压 × 1.5</li>
     * </ul>
     *
     * @param ratedPower 额定功率（W）
     * @param ratedVoltage 额定电压（V）
     * @param minResistance 最小电阻（Ω）
     * @param thermalMass 热质量
     * @param maxPowerLevel 最大发光功率档位
     * @param dyeable 是否可染色
     * @return 完整的热学特性记录
     */
    static ThermalProperties fromRated(float ratedPower, float ratedVoltage, float minResistance, float thermalMass, int maxPowerLevel, boolean dyeable) {
        float rMax = ratedVoltage * ratedVoltage / ratedPower;
        float operatingTemperature = 1450f;
        float dissipationFactor = ratedPower / (operatingTemperature - 22f);
        return new ThermalProperties(dissipationFactor, thermalMass, operatingTemperature + 400f, minResistance, rMax, operatingTemperature, ratedPower, ratedVoltage, ratedVoltage * 1.5f, maxPowerLevel, dyeable);
    }
}
