package com.energeticspowergrid;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 本模组的方块标签定义类。
 * <p>
 * 集中声明模组使用的方块 TagKey，供代码逻辑与数据包判断方块归属。
 */
public class EPGTags {
    /** 受生长灯影响的方块：属于此标签的方块会被生长灯执行 randomTick（如各类作物） */
    public static final TagKey<Block> AFFECTED_BY_LAMP = TagKey.create(Registries.BLOCK, EnergeticsPowerGrid.rl("affected_by_lamp"));
    /** Sable 模组的光源方块标签：本模组的灯具同时注册进该标签以兼容 Sable 模组 */
    public static final TagKey<Block> SABLE_LIGHT = TagKey.create(Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sable", "light"));
}
