package com.energeticspowergrid;

import com.energeticspowergrid.content.factorylight.FactoryLightBlock;
import com.energeticspowergrid.content.factorylight.FactoryLightLightBlock;
// import com.energeticspowergrid.content.fan.ElectricFanBlock;
import com.energeticspowergrid.content.fixture.LightFixtureBlock;
import com.energeticspowergrid.content.heater.HeatingCoilBlock;
import com.energeticspowergrid.content.reversing_switch.ReversingSwitchBlock;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.client.ElectricStatsTooltipModifier;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import static com.energeticspowergrid.EnergeticsPowerGrid.REGISTRATE;
import static com.simibubi.create.foundation.data.CreateRegistrate.casingConnectivity;
import static com.simibubi.create.foundation.data.CreateRegistrate.connectedTextures;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

public class EPGBlocks {
    static {
        REGISTRATE.defaultCreativeTab((net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) null);
    }

    public static final BlockEntry<LightFixtureBlock> LIGHT_FIXTURE = REGISTRATE.block("light_fixture", LightFixtureBlock::new)
            .tag(CEETags.LIGHT, EPGTags.SABLE_LIGHT)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion()
                    .lightLevel(s -> switch (s.getValue(LightFixtureBlock.POWER)) {
                        case 1 -> 10;
                        case 2 -> 15;
                        default -> 0;
                    }))
            .blockstate((c, p) -> p.getVariantBuilder(c.getEntry()).forAllStates(state -> {
                Direction facing = state.getValue(LightFixtureBlock.FACING);
                boolean roll = state.getValue(DirectionalRolledDeviceBlock.ROLL);
                boolean vertical = facing.getAxis().isVertical();
                int x = 0, y = 0;
                switch (facing) {
                    case UP -> {
                    }
                    case DOWN -> x = 180;
                    case EAST -> y = 180;
                    case NORTH -> y = 90;
                    case SOUTH -> y = -90;
                    default -> {
                    }
                }
                if (roll && vertical)
                    y = 90;
                else if (roll && !vertical)
                    x = -90;
                String suffix = vertical ? "_v" : "_h";
                return ConfiguredModel.builder()
                        .modelFile(p.models().getExistingFile(p.modLoc("block/fixtures/light_fixture" + suffix)))
                        .rotationX(x)
                        .rotationY(y)
                        .build();
            }))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/fixtures/light_fixture_v")))
            .onRegister(i -> ElectricStatsTooltipModifier.ALL_ENTRIES.register(i, new ElectricStatsTooltipModifier.ElectricStatSet()
                    .addResistance(() -> 1)))
            .build()
            .register();

    public static final BlockEntry<FactoryLightBlock> FACTORY_LIGHT = REGISTRATE.block("factory_light", FactoryLightBlock::new)
            .tag(CEETags.LIGHT, EPGTags.SABLE_LIGHT)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion()
                    .lightLevel(s -> switch (s.getValue(FactoryLightBlock.POWER)) {
                        case 2 -> 10;
                        case 3 -> 15;
                        default -> 0;
                    }))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/factory_light/factorylight")))
            .build()
            .register();

    public static final BlockEntry<FactoryLightLightBlock> FACTORY_LIGHT_LIGHT = REGISTRATE.block("factory_light_light", FactoryLightLightBlock::new)
            .properties(p -> p.lightLevel(s -> s.getValue(FactoryLightLightBlock.POWER) == 1 ? 15 : 10))
            .register();

    public static final BlockEntry<CasingBlock> CONDUCTIVE_CASING = REGISTRATE.block("conductive_casing", CasingBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.METAL))
            .onRegister(connectedTextures(() -> new EncasedCTBehaviour(EPGPartialModels.CONDUCTIVE_CASING)))
            .onRegister(casingConnectivity((block, cc) -> cc.makeCasing(block, EPGPartialModels.CONDUCTIVE_CASING)))
            .transform(pickaxeOnly())
            .simpleItem()
            .register();

    // Disabled: duplicates electroenergetics:electric_fan
    // public static final BlockEntry<ElectricFanBlock> ELECTRIC_FAN = REGISTRATE.block("electric_fan", ElectricFanBlock::new)
    //         .initialProperties(SharedProperties::softMetal)
    //         .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion())
    //         .transform(pickaxeOnly())
    //         .item()
    //         .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/electric_fan/item")))
    //         .onRegister(i -> ElectricStatsTooltipModifier.ALL_ENTRIES.register(i, new ElectricStatsTooltipModifier.ElectricStatSet()
    //                 .addResistance(() -> com.energeticspowergrid.config.EPGConfigs.server().electricFanResistance.getF())))
    //         .build()
    //         .register();

    public static final BlockEntry<HeatingCoilBlock> HEATING_COIL = REGISTRATE.block("heating_coil", HeatingCoilBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(AllTags.AllBlockTags.FAN_TRANSPARENT.tag)
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/heating_coil")))
            .onRegister(i -> ElectricStatsTooltipModifier.ALL_ENTRIES.register(i, new ElectricStatsTooltipModifier.ElectricStatSet()
                    .addResistance(() -> com.energeticspowergrid.config.EPGConfigs.server().heatingCoilResistance.getF())
                    .addMaxPower(() -> com.energeticspowergrid.config.EPGConfigs.server().heatingCoilMaxPower.getF())))
            .build()
            .register();

    public static final BlockEntry<ReversingSwitchBlock> REVERSING_SWITCH = REGISTRATE.block("reversing_switch", ReversingSwitchBlock::new)
            .tag(CEETags.LIGHT)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).noOcclusion())
            .blockstate((c, p) -> DirectionalRolledDeviceBlock.generateBlockState(c, p,
                    bs -> ResourceLocation.fromNamespaceAndPath("electroenergetics",
                            bs.getValue(com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock.CLOSED)
                                    ? "block/double_switch/block_closed"
                                    : "block/double_switch/block")))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(),
                    ResourceLocation.fromNamespaceAndPath("electroenergetics", "block/double_switch/block")))
            .build()
            .register();

    public static void register() {
    }
}
