package com.energeticspowergrid.content.heater;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class HeaterSmokingType extends AllFanProcessingTypes.SmokingType {
    @Override
    public int getPriority() {
        return 250;
    }

    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HeatingCoilBlockEntity coil)
            return coil.getState() == HeatingCoilBlockEntity.State.SMOKING;
        return false;
    }
}
