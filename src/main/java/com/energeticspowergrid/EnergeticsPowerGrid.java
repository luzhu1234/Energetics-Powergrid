package com.energeticspowergrid;

import com.energeticspowergrid.client.EPGClient;
import com.energeticspowergrid.config.EPGConfigs;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(EnergeticsPowerGrid.ID)
public class EnergeticsPowerGrid {
    public static final String ID = "energeticspowergrid";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static CreateRegistrate REGISTRATE;

    public EnergeticsPowerGrid(IEventBus modEventBus, ModContainer modContainer) {
        REGISTRATE = CreateRegistrate.create(ID)
                .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                        .andThen(TooltipModifier.mapNull(new com.george_vi.electroenergetics.client.ElectricStatsTooltipModifier(item))));
        REGISTRATE.registerEventListeners(modEventBus);

        EPGItems.register();
        EPGBlocks.register();
        EPGPartialModels.register();
        EPGBlockEntityTypes.register();
        EPGSimulatedDevices.register(modEventBus);
        EPGFanProcessingTypes.register(modEventBus);
        EPGCreativeTab.register(modEventBus);
        EPGConfigs.register(modContainer);

        if (FMLEnvironment.dist == Dist.CLIENT)
            EPGClient.init();
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
