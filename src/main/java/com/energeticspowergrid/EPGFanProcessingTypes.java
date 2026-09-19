package com.energeticspowergrid;

import com.energeticspowergrid.content.heater.HeaterBlastingType;
import com.energeticspowergrid.content.heater.HeaterSmokingType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EPGFanProcessingTypes {
    private static final DeferredRegister<FanProcessingType> TYPES =
            DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, EnergeticsPowerGrid.ID);

    static {
        TYPES.register("heater_blasting", HeaterBlastingType::new);
        TYPES.register("heater_smoking", HeaterSmokingType::new);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
