package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.config.EPGConfigs;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FactoryLightLightBlockEntity extends SmartBlockEntity {
    public FactoryLightLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        setLazyTickRate(10);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void lazyTick() {
        boolean hit = false;
        int range = EPGConfigs.server().factoryLightProjectionRange.get();
        for (int y = 0; y < range; y++) {
            BlockPos pos = worldPosition.above(y);
            if (level.getBlockEntity(pos) instanceof FactoryLightBlockEntity be && be.getPowerLevel() > 0) {
                hit = true;
                break;
            }
        }
        if (!hit)
            onDelete();
        super.lazyTick();
    }

    public void onDelete() {
        if (level != null)
            level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
    }
}
