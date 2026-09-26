package com.energeticspowergrid.content.excitation;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlock;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Read-only measurements the rotor needs from the four slots around it. Kept out of the mixin so
 * the loop can be reused; everything here is plain block queries, driven from the rotor's existing
 * lazy tick.
 */
public final class ExcitationFields {
    private ExcitationFields() {
    }

    /**
     * The vanilla stator count the rotor would have seen on its own, matching electroenergetics
     * exactly: 3 per stator that can power this rotor.
     */
    public static int vanillaMagnets(BlockGetter level, BlockPos rotorPos, BlockState rotorState) {
        Direction.Axis axis = rotorState.getValue(AlternatorRotorBlock.AXIS);
        int magnets = 0;
        for (Direction direction : Iterate.directions) {
            if (direction.getAxis() == axis)
                continue;
            BlockPos statorPos = rotorPos.relative(direction);
            BlockState statorState = level.getBlockState(statorPos);
            if (CEEBlocks.STATOR.has(statorState)
                    && StatorBlock.canPowerRotor(statorPos, statorState, rotorPos, rotorState))
                magnets += 3;
        }
        return magnets;
    }

    /**
     * Signed sum of the excitation field of every excitation stator that can power the rotor.
     * Summed before taking the magnitude, so opposing stators cancel and a reversed pair reads zero.
     */
    public static double excitationSum(ServerLevel serverLevel, BlockPos rotorPos, BlockState rotorState) {
        Direction.Axis axis = rotorState.getValue(AlternatorRotorBlock.AXIS);
        DevicesSavedData deviceSD = DevicesSavedData.load(serverLevel);
        double sum = 0;
        for (Direction direction : Iterate.directions) {
            if (direction.getAxis() == axis)
                continue;
            BlockPos statorPos = rotorPos.relative(direction);
            BlockState statorState = serverLevel.getBlockState(statorPos);
            if (!(statorState.getBlock() instanceof ExcitationStatorBlock))
                continue;
            if (!ExcitationStatorBlock.canPowerRotor(statorPos, statorState, rotorPos, rotorState))
                continue;
            ExcitationStatorDevice device = deviceSD.getDevice(statorPos, ExcitationStatorDevice.class);
            if (device != null)
                sum += device.getFieldStrength();
        }
        return sum;
    }
}
