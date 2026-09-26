package com.energeticspowergrid.content.inverter;

import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.CEENodeConfigurations;
import com.george_vi.electroenergetics.CEEShapes;
import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Inverter, reusing the double knife switch's model and shape.
 * <p>
 * Terminals 1/2 are the DC input, 3/4 the AC output. With the blade open the input passes
 * straight through to the output; with the blade closed the device switches to inverter mode
 * and emits a square wave on the output whose amplitude follows the measured input voltage,
 * while the input side draws power matching what the output delivers.
 */
public class InverterBlock extends DirectionalRolledDeviceBlock<InverterDevice> {
    public InverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CutOffSwitchBlock.CLOSED, false));
    }

    @Override
    public SimulatedDeviceType<InverterDevice> getDevice() {
        return EPGSimulatedDevices.INVERTER.get();
    }

    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Closed", state.getValue(CutOffSwitchBlock.CLOSED));
        return tag;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CutOffSwitchBlock.CLOSED);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(stack) || stack.getItem() instanceof WireSpoolItem || com.george_vi.electroenergetics.CEEItems.EMPTY_SPOOL.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (level instanceof ServerLevel serverLevel) {
            InverterDevice device = DevicesSavedData.load(serverLevel).getDevice(pos, InverterDevice.class);
            if (device != null)
                device.closed = !state.getValue(CutOffSwitchBlock.CLOSED);
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        }
        level.setBlockAndUpdate(pos, state.cycle(CutOffSwitchBlock.CLOSED));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CEEShapes.DOUBLE_SWITCH.get(state.getValue(FACING));
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return nodes(state).getNodes(state.getValue(FACING));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return nodes(state).getNodePos(state.getValue(FACING), id);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int id) {
        return switch (id) {
            case 0 -> Component.translatable("energeticspowergrid.nodes.input_positive");
            case 1 -> Component.translatable("energeticspowergrid.nodes.input_negative");
            case 2 -> Component.translatable("energeticspowergrid.nodes.output_positive");
            default -> Component.translatable("energeticspowergrid.nodes.output_negative");
        };
    }

    private static com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator nodes(BlockState state) {
        return state.getValue(ROLL)
                ? CEENodeConfigurations.DOUBLE_SWITCH.rotate(new Vec3(0, 90, 0))
                : CEENodeConfigurations.DOUBLE_SWITCH;
    }

    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
