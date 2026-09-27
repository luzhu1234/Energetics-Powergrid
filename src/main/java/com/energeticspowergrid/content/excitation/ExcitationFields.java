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
 * Excitation measurements the rotor needs from the four slots around it.
 * <p>
 * Vanilla stators are part of the same excitation system: each one contributes a fixed field
 * strength (default 100, config) instead of the special-cased weakening they used to have, so
 * mixing the two stator kinds behaves as one uniform pool. 1000 field equals one vanilla
 * stator's worth of pull, which matches the old 1/10 factor exactly.
 */
public final class ExcitationFields {
    private ExcitationFields() {
    }

    /**
     * Signed sum of the excitation field of every stator that can power the rotor: excitation
     * stators contribute their live field, vanilla stators their fixed one. Summed before taking
     * the magnitude, so opposing stators cancel and a reversed pair reads zero.
     */
    public static double excitationSum(ServerLevel serverLevel, BlockPos rotorPos, BlockState rotorState) {
        Direction.Axis axis = rotorState.getValue(AlternatorRotorBlock.AXIS);
        double vanillaField = EPGConfigs.server().vanillaStatorField.getF();
        DevicesSavedData deviceSD = null;

        double sum = 0;
        for (Direction direction : Iterate.directions) {
            if (direction.getAxis() == axis)
                continue;
            BlockPos statorPos = rotorPos.relative(direction);
            BlockState statorState = serverLevel.getBlockState(statorPos);

            if (statorState.getBlock() instanceof ExcitationStatorBlock) {
                if (!ExcitationStatorBlock.canPowerRotor(statorPos, statorState, rotorPos, rotorState))
                    continue;
                if (deviceSD == null)
                    deviceSD = DevicesSavedData.load(serverLevel);
                ExcitationStatorDevice device = deviceSD.getDevice(statorPos, ExcitationStatorDevice.class);
                if (device != null)
                    sum += device.getFieldStrength();
                continue;
            }

            if (CEEBlocks.STATOR.has(statorState)
                    && StatorBlock.canPowerRotor(statorPos, statorState, rotorPos, rotorState))
                sum += vanillaField;
        }
        return sum;
    }
}
