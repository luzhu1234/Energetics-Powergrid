package com.energeticspowergrid.content.heater;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 加热线圈提供的 Create 风扇“烟熏（Smoking）”加工类型。
 * <p>
 * 继承 Create 原版的 SmokingType，但把有效性判定替换为：
 * 气流命中位置是加热线圈方块实体且其温度处于 SMOKING 档（200~400°C）。
 * 线圈温度继续升高到 BLASTING 档后本类型失效，由
 * {@link HeaterBlastingType} 接管（焙烧优先级 250 更高且 Coil 状态已切换），
 * 从而实现“温度档位决定风扇加工类型”的电气化热源。
 */
public class HeaterSmokingType extends AllFanProcessingTypes.SmokingType {
    /** 处理优先级：高于 Blasting（150），保证烟熏档优先判定。 */
    @Override
    public int getPriority() {
        return 250;
    }

    /** 仅当目标位置是处于 SMOKING（200~400°C）状态的加热线圈时有效。 */
    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HeatingCoilBlockEntity coil)
            return coil.getState() == HeatingCoilBlockEntity.State.SMOKING;
        return false;
    }
}
