package com.energeticspowergrid.content.bulb;

import com.energeticspowergrid.EPGTags;
import com.energeticspowergrid.config.EPGConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class GrowthLampItem extends LightBulbItem {
    public GrowthLampItem(Properties properties) {
        super(properties);
    }

    public void tickCrops(ServerLevel level, BlockPos origin, Direction facing) {
        int radius = EPGConfigs.server().growthLampRadius.get();
        int xMin = -radius, xMax = radius;
        int yMin = -radius, yMax = radius;
        int zMin = -radius, zMax = radius;
        if (facing != null) {
            switch (facing) {
                case EAST -> xMin = 0;
                case WEST -> xMax = 0;
                case UP -> yMin = 0;
                case DOWN -> yMax = 0;
                case SOUTH -> zMin = 0;
                case NORTH -> zMax = 0;
            }
        }

        int chanceValue = EPGConfigs.server().growthLampChance.get();
        var random = level.random;
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    if (chanceValue > 1 && random.nextInt(chanceValue) != 0)
                        continue;
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(EPGTags.AFFECTED_BY_LAMP))
                        state.randomTick(level, pos, random);
                }
            }
        }
    }
}
