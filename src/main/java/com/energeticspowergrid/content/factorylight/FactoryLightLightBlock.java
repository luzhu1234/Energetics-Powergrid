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

/**
 * 工厂灯投射光照方块（Factory Light Light）。
 * <p>
 * 由 {@link FactoryLightBlockEntity#projectDown} 在工厂灯正下方的空气列中
 * 自动放置/移除的“虚拟光照方块”，用于把照明投射到下方空间。
 * 它完全不是真实设备：无碰撞、无掉落表、不可见（空碰撞箱）、可被任意替换、
 * 被活塞推动时直接销毁，表现为“类空气”方块。
 * POWER 属性 0/1 分别对应低/满两档光照等级。
 */
public class FactoryLightLightBlock extends Block implements IBE<FactoryLightLightBlockEntity> {
    /** 光照档位：0 = 低亮度，1 = 满亮度。 */
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 1);

    public FactoryLightLightBlock(Properties properties) {
        super(properties
                .noCollission()      // 无碰撞
                .noLootTable()       // 无掉落表
                .noOcclusion()       // 不遮挡邻方面
                .replaceable()       // 可被放置的方块直接替换
                .noTerrainParticles()// 不产生地形破坏粒子
                .pushReaction(PushReaction.DESTROY) // 被活塞推动时销毁
                .air());             // 视为空气（影响寻路/光照等判定）
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    /** 注册 POWER 属性。 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    /** 声明光照等级动态变化。 */
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    /** 档 1 → 满亮度光照，档 0 → 低亮度光照。 */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POWER) == 1 ? ILightBulb.LIGHT_LEVEL_FULL_POWER : ILightBulb.LIGHT_LEVEL_LOW_POWER;
    }

    /** 碰撞箱为空——完全不可交互、不阻挡。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** 方块实体类型 Class。 */
    @Override
    public Class<FactoryLightLightBlockEntity> getBlockEntityClass() {
        return FactoryLightLightBlockEntity.class;
    }

    /** 方块实体类型注册项。 */
    @Override
    public BlockEntityType<? extends FactoryLightLightBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.FACTORY_LIGHT_LIGHT.get();
    }
}
