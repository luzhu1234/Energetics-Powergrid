package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.config.EPGConfigs;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 工厂灯投射光照方块（Factory Light Light）的方块实体。
 * <p>
 * 该方块由工厂灯自动放置，本身不保存任何数据，仅作为“保活哨兵”：
 * lazyTick 时向上检查投射范围内是否仍存在点亮的工厂灯，
 * 若找不到（例如工厂灯被拆除、断电或位置变动）就自毁（把自身位置替换为空气），
 * 防止光照方块在失去来源后残留。
 */
public class FactoryLightLightBlockEntity extends SmartBlockEntity {
    public FactoryLightLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        // 每 10 tick（0.5 秒）自检一次
        setLazyTickRate(10);
    }

    /** 无附加行为。 */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /**
     * 低频自检：沿正向上方逐格扫描至投射范围，
     * 只要找到任何亮度档位 > 0 的工厂灯就继续存活；否则自毁。
     */
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

    /** 自毁：把自身位置替换为空气（flags=3：方块更新 + 发送给客户端）。 */
    public void onDelete() {
        if (level != null)
            level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
    }
}
