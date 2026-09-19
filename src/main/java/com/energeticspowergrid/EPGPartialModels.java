package com.energeticspowergrid;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public class EPGPartialModels {
    public static final PartialModel LIGHT_BULB = model("block/lamps/light_bulb");
    public static final PartialModel LIGHT_BULB_ON = model("block/lamps/light_bulb_on");
    public static final PartialModel LIGHT_BULB_BROKEN = model("block/lamps/light_bulb_broken");
    public static final PartialModel LIGHT_BULB_LIGHT = model("block/lamps/light_bulb_light");

    public static final PartialModel DYED_LIGHT_BULB = model("block/lamps/dyed_light_bulb");
    public static final PartialModel DYED_LIGHT_BULB_ON = model("block/lamps/dyed_light_bulb_on");
    public static final PartialModel DYED_LIGHT_BULB_BROKEN = model("block/lamps/dyed_light_bulb_broken");
    public static final PartialModel DYED_LIGHT_BULB_LIGHT = model("block/lamps/dyed_light_bulb_light");
    public static final PartialModel DYED_LIGHT_BULB_BULB = model("block/lamps/dyed_light_bulb_bulb");

    public static final PartialModel GROWTH_LAMP = model("block/lamps/growth_lamp");
    public static final PartialModel GROWTH_LAMP_ON = model("block/lamps/growth_lamp_on");
    public static final PartialModel GROWTH_LAMP_BROKEN = model("block/lamps/growth_lamp_broken");
    public static final PartialModel GROWTH_LAMP_LIGHT = model("block/lamps/growth_lamp_light");

    public static final PartialModel FL_RAYS_SINGLE = model("block/factory_light/godrayssingular");
    public static final PartialModel FL_RAYS_CENTER = model("block/factory_light/godrayscenter");
    public static final PartialModel FL_RAYS_FRONT = model("block/factory_light/godraysedgefront");
    public static final PartialModel FL_RAYS_BACK = model("block/factory_light/godraysedgeback");

    // Disabled: electric blower duplicates CEE electric fan
    // public static final PartialModel FAN_PROPELLER = model("block/electric_fan/propeller");

    public static final CTSpriteShiftEntry CONDUCTIVE_CASING = CTSpriteShifter.getCT(
            AllCTTypes.OMNIDIRECTIONAL,
            EnergeticsPowerGrid.rl("block/conductive_casing"),
            EnergeticsPowerGrid.rl("block/conductive_casing_connected"));

    private static PartialModel model(String path) {
        return PartialModel.of(EnergeticsPowerGrid.rl(path));
    }

    public static void register() {
    }
}
