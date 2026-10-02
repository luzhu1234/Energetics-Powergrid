package com.energeticspowergrid.content.heater;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 加热线圈提供的 Create 风扇“鼓风焙烧（Blasting）”加工类型。
 * <p>
 * 继承 Create 原版的 BlastingType，但把有效性判定从“气流末端的火焰/岩浆”
 * 替换为：气流命中位置是加热线圈方块实体且其温度已达 BLASTING 档（≥400°C）。
 * 这样机械风扇吹到通电加热的线圈上即可执行熔炼/焙烧类配方，
 * 而无需原版热源方块。
 * <p>
 * 优先级 150 高于 Create 内建的水/烟熏等类型，保证与 Smoking 判定互斥时
 * 本类型按温度正确胜出（Smoking 类型优先级为 250 更高，见 {@link HeaterSmokingType}）。
 */
public class HeaterBlastingType extends AllFanProcessingTypes.BlastingType {
    /** 处理优先级：数值越大越先参与判定。 */
    @Override
    public int getPriority() {
        return 150;
    }

    /** 仅当目标位置是处于 BLASTING（≥400°C）状态的加热线圈时有效。 */
    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HeatingCoilBlockEntity coil)
            return coil.getState() == HeatingCoilBlockEntity.State.BLASTING;
        return false;
    }
}
