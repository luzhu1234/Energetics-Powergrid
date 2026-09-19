package com.energeticspowergrid;

import com.energeticspowergrid.content.factorylight.FactoryLightBlockEntity;
import com.energeticspowergrid.content.factorylight.FactoryLightLightBlockEntity;
import com.energeticspowergrid.content.factorylight.FactoryLightRenderer;
// import com.energeticspowergrid.content.fan.ElectricFanBlockEntity;
// import com.energeticspowergrid.content.fan.ElectricFanRenderer;
import com.energeticspowergrid.content.fixture.LightFixtureBlockEntity;
import com.energeticspowergrid.content.fixture.LightFixtureRenderer;
import com.energeticspowergrid.content.heater.HeatingCoilBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.energeticspowergrid.EnergeticsPowerGrid.REGISTRATE;

public class EPGBlockEntityTypes {
    public static final BlockEntityEntry<LightFixtureBlockEntity> LIGHT_FIXTURE =
            REGISTRATE.blockEntity("light_fixture", LightFixtureBlockEntity::new)
                    .validBlock(EPGBlocks.LIGHT_FIXTURE)
                    .renderer(() -> LightFixtureRenderer::new)
                    .register();

    public static final BlockEntityEntry<FactoryLightBlockEntity> FACTORY_LIGHT =
            REGISTRATE.blockEntity("factory_light", FactoryLightBlockEntity::new)
                    .validBlock(EPGBlocks.FACTORY_LIGHT)
                    .renderer(() -> FactoryLightRenderer::new)
                    .register();

    public static final BlockEntityEntry<FactoryLightLightBlockEntity> FACTORY_LIGHT_LIGHT =
            REGISTRATE.blockEntity("factory_light_light", FactoryLightLightBlockEntity::new)
                    .validBlock(EPGBlocks.FACTORY_LIGHT_LIGHT)
                    .register();

    // Disabled: duplicates electroenergetics:electric_fan
    // public static final BlockEntityEntry<ElectricFanBlockEntity> ELECTRIC_FAN =
    //         REGISTRATE.blockEntity("electric_fan", ElectricFanBlockEntity::new)
    //                 .validBlock(EPGBlocks.ELECTRIC_FAN)
    //                 .renderer(() -> ElectricFanRenderer::new)
    //                 .register();

    public static final BlockEntityEntry<HeatingCoilBlockEntity> HEATING_COIL =
            REGISTRATE.blockEntity("heating_coil", HeatingCoilBlockEntity::new)
                    .validBlock(EPGBlocks.HEATING_COIL)
                    .register();

    public static void register() {
    }
}
