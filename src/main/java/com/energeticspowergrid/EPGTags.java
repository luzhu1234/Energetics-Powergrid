package com.energeticspowergrid;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class EPGTags {
    public static final TagKey<Block> AFFECTED_BY_LAMP = TagKey.create(Registries.BLOCK, EnergeticsPowerGrid.rl("affected_by_lamp"));
    public static final TagKey<Block> SABLE_LIGHT = TagKey.create(Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sable", "light"));
}
