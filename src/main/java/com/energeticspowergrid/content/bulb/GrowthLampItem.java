package com.energeticspowergrid.content.bulb;

import com.energeticspowergrid.EPGTags;
import com.energeticspowergrid.config.EPGConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 植物生长灯物品。
 * <p>
 * 继承灯泡物品，额外提供 {@link #tickCrops} 方法：
 * 通电时对周围处于 {@code affected_by_lamp} 标签中的方块
 * （作物等）执行随机刻，从而催熟农作物。
 */
public class GrowthLampItem extends LightBulbItem {
    /**
     * 构造植物生长灯物品。
     *
     * @param properties 物品属性
     */
    public GrowthLampItem(Properties properties) {
        super(properties);
    }

    /**
     * 催熟处理：遍历灯泡周围的作用范围内的方块，
     * 按配置概率对属于 AFFECTED_BY_LAMP 标签的方块执行 randomTick。
     * <p>
     * 作用范围与概率由服务端配置 growthLampRadius / growthLampChance 决定；
     * 当灯泡存在朝向时，范围会朝朝向方向单侧扩展（背朝向一侧不生效）。
     *
     * @param level 服务端世界
     * @param origin 灯泡所在位置
     * @param facing 灯泡朝向（可为 null，为 null 时各方向均匀扩展）
     */
    public void tickCrops(ServerLevel level, BlockPos origin, Direction facing) {
        // 读取配置的作用半径
        int radius = EPGConfigs.server().growthLampRadius.get();
        int xMin = -radius, xMax = radius;
        int yMin = -radius, yMax = radius;
        int zMin = -radius, zMax = radius;
        // 有朝向时，仅朝朝向一侧扩展（收缩背向半区）
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

        // 读取催熟概率分母
        int chanceValue = EPGConfigs.server().growthLampChance.get();
        var random = level.random;
        // 三重循环遍历作用范围内所有方块
        for (int x = xMin; x <= xMax; x++) {
            for (int y = yMin; y <= yMax; y++) {
                for (int z = zMin; z <= zMax; z++) {
                    // 按概率随机跳过（分母为 1 时全部生效）
                    if (chanceValue > 1 && random.nextInt(chanceValue) != 0)
                        continue;
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    // 仅对受生长灯影响的方块（作物等）执行随机刻
                    if (state.is(EPGTags.AFFECTED_BY_LAMP))
                        state.randomTick(level, pos, random);
                }
            }
        }
    }
}
