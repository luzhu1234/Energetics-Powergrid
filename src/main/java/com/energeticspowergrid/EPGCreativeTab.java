package com.energeticspowergrid;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EPGCreativeTab {
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EnergeticsPowerGrid.ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = REGISTER.register("base",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.energeticspowergrid"))
                    .icon(EPGBlocks.LIGHT_FIXTURE::asStack)
                    .displayItems((parameters, output) -> {
                        output.accept(EPGBlocks.LIGHT_FIXTURE.asStack());
                        output.accept(EPGItems.LV_LIGHT_BULB.asStack());
                        output.accept(EPGItems.LIGHT_BULB.asStack());
                        output.accept(EPGItems.GROWTH_LAMP.asStack());
                        output.accept(EPGBlocks.FACTORY_LIGHT.asStack());
                        output.accept(EPGBlocks.CONDUCTIVE_CASING.asStack());
                        // output.accept(EPGBlocks.ELECTRIC_FAN.asStack());
                        output.accept(EPGBlocks.HEATING_COIL.asStack());
                        output.accept(EPGBlocks.REVERSING_SWITCH.asStack());
                        output.accept(EPGBlocks.EXCITATION_STATOR.asStack());
                        output.accept(EPGBlocks.INVERTER.asStack());
                    })
                    .build());

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
