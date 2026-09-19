package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FactoryLightLightBlock extends Block implements IBE<FactoryLightLightBlockEntity> {
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 1);

    public FactoryLightLightBlock(Properties properties) {
        super(properties
                .noCollission()
                .noLootTable()
                .noOcclusion()
                .replaceable()
                .noTerrainParticles()
                .pushReaction(PushReaction.DESTROY)
                .air());
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POWER) == 1 ? ILightBulb.LIGHT_LEVEL_FULL_POWER : ILightBulb.LIGHT_LEVEL_LOW_POWER;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public Class<FactoryLightLightBlockEntity> getBlockEntityClass() {
        return FactoryLightLightBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FactoryLightLightBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.FACTORY_LIGHT_LIGHT.get();
    }
}
