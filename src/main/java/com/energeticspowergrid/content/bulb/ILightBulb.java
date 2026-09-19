package com.energeticspowergrid.content.bulb;

import net.minecraft.world.item.Item;

public interface ILightBulb {
    int LIGHT_LEVEL_LOW_POWER = 10;
    int LIGHT_LEVEL_FULL_POWER = 15;

    record ThermalProperties(float dissipationFactor, float thermalMass, float overheatTemperature, float minResistance, float maxResistance, float operatingTemperature, float ratedPower, float ratedVoltage, float breakdownVoltage, int maxPowerLevel, boolean dyeable) {
    }

    ThermalProperties thermalProperties();

    default float resistanceFunction(float temperature) {
        ThermalProperties p = thermalProperties();
        return p.minResistance() + ((p.maxResistance() - p.minResistance()) / p.operatingTemperature()) * temperature;
    }

    static ThermalProperties fromRated(float ratedPower, float ratedVoltage, float minResistance, float thermalMass, int maxPowerLevel, boolean dyeable) {
        float rMax = ratedVoltage * ratedVoltage / ratedPower;
        float operatingTemperature = 1450f;
        float dissipationFactor = ratedPower / (operatingTemperature - 22f);
        return new ThermalProperties(dissipationFactor, thermalMass, operatingTemperature + 400f, minResistance, rMax, operatingTemperature, ratedPower, ratedVoltage, ratedVoltage * 1.5f, maxPowerLevel, dyeable);
    }
}
