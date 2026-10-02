package com.energeticspowergrid.content.excitation;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlock;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 励磁测量工具：汇总转子四周四个槽位上的励磁贡献。
 * <p>
 * 原版定子也纳入同一套励磁体系：每台贡献固定场强（默认 100，可配置），
 * 不再使用它原本的特殊削弱逻辑，因此两种定子混用时表现为同一个统一的
 * "励磁池"。1000 场强等价于一台原版定子的磁铁拉力，与旧的 1/10 系数完全吻合。
 */
public final class ExcitationFields {
    // 纯工具类，禁止实例化
    private ExcitationFields() {
    }

    /**
     * 对所有"能给该转子供电"的定子求带符号的励磁和：
     * 励磁定子贡献其实时场强，原版定子贡献其固定场强。
     * 先求和再取模，因此相对放置的两台定子会互相抵消，
     * 一对反向定子的读数为 0（符合物理直觉）。
     *
     * 仅限服务端调用：励磁定子的场强保存在服务端设备数据（DevicesSavedData）里，
     * 客户端拿不到真实值。
     */
    public static double excitationSum(ServerLevel serverLevel, BlockPos rotorPos, BlockState rotorState) {
        // 转子轴方向上的两个位置属于"同轴转子/对轮"，不是定子槽位，跳过
        Direction.Axis axis = rotorState.getValue(AlternatorRotorBlock.AXIS);
        double vanillaField = EPGConfigs.server().vanillaStatorField.getF();
        // DevicesSavedData 按需加载：只有真的遇到励磁定子时才读一次存档数据
        DevicesSavedData deviceSD = null;

        double sum = 0;
        // 扫描转子四周的 4 个非轴向槽位
        for (Direction direction : Iterate.directions) {
            if (direction.getAxis() == axis)
                continue;
            BlockPos statorPos = rotorPos.relative(direction);
            BlockState statorState = serverLevel.getBlockState(statorPos);

            // 分支一：励磁定子——除位置/朝向校验外，还要从设备数据读取实时场强
            if (statorState.getBlock() instanceof ExcitationStatorBlock) {
                // 位置与朝向必须真正对准该转子才计入
                if (!ExcitationStatorBlock.canPowerRotor(statorPos, statorState, rotorPos, rotorState))
                    continue;
                if (deviceSD == null)
                    deviceSD = DevicesSavedData.load(serverLevel);
                ExcitationStatorDevice device = deviceSD.getDevice(statorPos, ExcitationStatorDevice.class);
                if (device != null)
                    // 带符号累加：反极性定子自然抵消
                    sum += device.getFieldStrength();
                continue;
            }

            // 分支二：原版定子——按配置的固定场强计入，行为与旧版 1/10 削弱系数等价
            if (CEEBlocks.STATOR.has(statorState)
                    && StatorBlock.canPowerRotor(statorPos, statorState, rotorPos, rotorState))
                sum += vanillaField;
        }
        return sum;
    }
}
