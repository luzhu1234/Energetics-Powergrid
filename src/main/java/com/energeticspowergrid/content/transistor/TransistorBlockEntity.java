package com.energeticspowergrid.content.transistor;

import java.util.List;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;

public class TransistorBlockEntity extends SmartBlockEntity {
    protected TransistorScrollValues.Beta beta;
    protected TransistorScrollValues.Vbe vbe;
    protected TransistorScrollValues.IcMax icMax;

    public TransistorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Three parameter menus on the model's sides, one per slot.
        beta = new TransistorScrollValues.Beta(
                Component.translatable("energeticspowergrid.gui.transistor.beta"), this, new ParamSlot(0));
        beta.withCallback(i -> updateTransistor());
        behaviours.add(beta);

        vbe = new TransistorScrollValues.Vbe(
                Component.translatable("energeticspowergrid.gui.transistor.vbe"), this, new ParamSlot(1));
        vbe.withCallback(i -> updateTransistor());
        behaviours.add(vbe);

        icMax = new TransistorScrollValues.IcMax(
                Component.translatable("energeticspowergrid.gui.transistor.icmax"), this, new ParamSlot(2));
        icMax.withCallback(i -> updateTransistor());
        behaviours.add(icMax);
    }

    public double getBeta() {
        return beta.getBeta();
    }

    public double getVbeOn() {
        return vbe.getVbeOn();
    }

    public double getIcMax() {
        return icMax.getIcMax();
    }

    private void updateTransistor() {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        TransistorDevice device = DevicesSavedData.load(serverLevel).getDevice(worldPosition, TransistorDevice.class);
        if (device != null) {
            device.beta = getBeta();
            device.vbeOn = getVbeOn();
            device.icMax = getIcMax();
        }
    }

    /**
     * One parameter slot per pin, hovering just above the model's body. The x coordinates match
     * the pin centres in {@link TransistorBlock#NODES} (5, 8, 11) so each menu sits above its
     * pin; reuses the node configurator's rotation so the slots follow the model through facing
     * and roll exactly like the pins do.
     */
    private static final com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator SLOTS =
            new com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator.Builder()
                    .add(5f, 7.5f, 9.75f)
                    .add(8f, 7.5f, 9.75f)
                    .add(11f, 7.5f, 9.75f)
                    .simple(Direction.UP);

    private static class ParamSlot extends ValueBoxTransform {
        private final int index;

        ParamSlot(int index) {
            this.index = index;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            boolean roll = state.getValue(com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock.ROLL);
            com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator rotated = roll
                    ? SLOTS.rotate(new Vec3(0, -90, 0))
                    : SLOTS;
            return rotated.getNodePos(state.getValue(CreativeBatteryBlock.FACING), index);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction facing = state.getValue(CreativeBatteryBlock.FACING);
            // The text plane sits on the model's flat face: south when facing up, above the
            // body for horizontal facings, north when facing down.
            Direction face = facing == Direction.UP ? Direction.SOUTH
                    : facing == Direction.DOWN ? Direction.NORTH : Direction.UP;
            float yRot = (face.get2DDataValue() & 3) * 90f + 180f;
            float xRot = face == Direction.UP ? 90 : face == Direction.DOWN ? 270 : 0;
            TransformStack.of(ms)
                    .rotateYDegrees(yRot)
                    .rotateXDegrees(xRot);
        }
    }
}
