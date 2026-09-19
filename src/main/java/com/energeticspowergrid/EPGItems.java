package com.energeticspowergrid;

import com.energeticspowergrid.content.bulb.GrowthLampItem;
import com.energeticspowergrid.content.bulb.LightBulbItem;
import com.energeticspowergrid.content.bulb.LvLightBulbItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.energeticspowergrid.EnergeticsPowerGrid.REGISTRATE;

public class EPGItems {
    static {
        REGISTRATE.defaultCreativeTab((net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) null);
    }

    public static final ItemEntry<LvLightBulbItem> LV_LIGHT_BULB = REGISTRATE.item("lv_light_bulb", LvLightBulbItem::new)
            .transform(LightBulbItem.setRated(4, 12, 12, 0.001f, 1, true))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/lamps/light_bulb")))
            .lang("LV Incandescent Lamp")
            .register();

    public static final ItemEntry<LightBulbItem> LIGHT_BULB = REGISTRATE.item("light_bulb", LightBulbItem::new)
            .transform(LightBulbItem.setRated(11, 220, 1467, 0.005f, 2, true))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/lamps/light_bulb")))
            .lang("Incandescent Lamp")
            .register();

    public static final ItemEntry<GrowthLampItem> GROWTH_LAMP = REGISTRATE.item("growth_lamp", GrowthLampItem::new)
            .transform(LightBulbItem.setRated(110, 220, 147, 0.01f, 2, false))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/lamps/growth_lamp")))
            .register();

    public static void register() {
    }
}
