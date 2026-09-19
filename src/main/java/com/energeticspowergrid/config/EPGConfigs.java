package com.energeticspowergrid.config;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Supplier;

public class EPGConfigs {
    private static CServer server;

    public static CServer server() {
        return server;
    }

    private static <T extends ConfigBase> T register(Supplier<T> factory) {
        Pair<T, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(builder -> {
            T config = factory.get();
            config.registerAll(builder);
            return config;
        });
        T config = specPair.getLeft();
        config.specification = specPair.getRight();
        return config;
    }

    public static void register(ModContainer container) {
        server = register(CServer::new);
        container.registerConfig(ModConfig.Type.SERVER, server.specification);
    }

    public static class CServer extends ConfigBase {
        public final ConfigInt growthLampRadius = i(2, 1, 8, "growthLampRadius",
                "Radius of the area affected by the growth lamp");
        public final ConfigInt growthLampChance = i(50, 0, 400, "growthLampChance",
                "Chance denominator for ticking a crop (lower = more frequent). 1 ticks every block every tick.");
        public final ConfigBool componentDamage = b(true, "componentDamage",
                "Light bulbs burn out when overloaded");
        public final ConfigInt factoryLightProjectionRange = i(16, 0, 32, "factoryLightProjectionRange",
                "Maximum block range of the factory light projected light blocks");
        // Disabled: electric blower duplicates CEE electric fan
        // public final ConfigFloat electricFanResistance = f(25, 0.1f, 10000, "electricFanResistance",
        //         "Series resistance of the electric blower");
        // public final ConfigFloat electricFanCurrentToSpeed = f(64, 1, 1024, "electricFanCurrentToSpeed",
        //         "Kinetic speed = current * this value, clamped to +/- 256");
        public final ConfigFloat heatingCoilResistance = f(25, 0.1f, 10000, "heatingCoilResistance",
                "Series resistance of the heating coil");
        public final ConfigFloat heatingCoilMaxPower = f(1500, 1, 100000, "heatingCoilMaxPower",
                "Power in watts that holds the heating coil at 600 C");
        public final ConfigFloat heatingCoilMass = f(1, 0.01f, 100, "heatingCoilMass",
                "Thermal mass of the heating coil");
        public final ConfigFloat reversingSwitchResistance = f(0.001f, 0.0001f, 10, "reversingSwitchResistance",
                "Contact resistance of the reversing switch");

        @Override
        public String getName() {
            return "server";
        }
    }
}
