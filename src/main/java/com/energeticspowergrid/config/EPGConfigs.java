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
        public final ConfigFloat excitationStatorResistance = f(100, 0.1f, 100000, "excitationStatorResistance",
                "Internal resistance of the excitation stator winding");
        public final ConfigFloat excitationStatorFieldFactor = f(1, 0, 1000, "excitationStatorFieldFactor",
                "The y in B = I * U * y, scaling excitation strength per stator");
        public final ConfigFloat inverterResistance = f(0.001f, 0.0001f, 10, "inverterResistance",
                "Contact resistance of the inverter's straight-through mode");
        public final ConfigFloat inverterOutputResistance = f(0.5f, 0.01f, 100, "inverterOutputResistance",
                "Internal resistance of the inverter's AC output");
        public final ConfigInt inverterFrequency = i(50, 1, 400, "inverterFrequency",
                "Frequency of the inverter's square wave output in Hz");
        public final ConfigFloat excitationFieldPerStator = f(1000, 1, 100000, "excitationFieldPerStator",
                "Excitation strength that matches one vanilla stator's pull on a rotor");
        public final ConfigFloat vanillaStatorStressFactor = f(0.1f, 0, 1, "vanillaStatorStressFactor",
                "Multiplier applied to what a vanilla stator contributes to a rotor - both its stress draw and the brushes' power output read this weakened count");
        public final ConfigFloat hertzPerRpm = f(6.4f, 0.01f, 100, "hertzPerRpm",
                "Rotational speed to AC frequency, matching electroenergetics' own value");
        public final ConfigFloat frequencyDipMaxHz = f(0.08f, 0, 5, "frequencyDipMaxHz",
                "Upper bound on the frequency dip or lift caused by a load change");
        public final ConfigFloat frequencyDipHzPerKilowatt = f(0.02f, 0, 10, "frequencyDipHzPerKilowatt",
                "Frequency dip per kilowatt of load step, before the cap is applied");
        public final ConfigInt frequencyDipDecayTicks = i(100, 1, 600, "frequencyDipDecayTicks",
                "Ticks over which a frequency dip decays back to the attainable frequency, 100 = 5 seconds");

        @Override
        public String getName() {
            return "server";
        }
    }
}
