package com.energeticspowergrid.content.transistor;

import java.util.Map;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGSimulatedDevices;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDevice;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TransistorBlock extends DirectionalRolledDeviceBlock<TransistorDevice> implements IBE<TransistorBlockEntity> {
    /** Pins down, body slab thin along Z (matches the raw Blockbench model). */
    private static final VoxelShaper SHAPE = new com.simibubi.create.AllShapes.Builder(
            net.minecraft.world.phys.shapes.Shapes.box(3 / 16d, 0, 7 / 16d, 13 / 16d, 13 / 16d, 9 / 16d)).forDirectional();
    private static final VoxelShaper SHAPE_ROLL = new com.simibubi.create.AllShapes.Builder(
            net.minecraft.world.phys.shapes.Shapes.box(7 / 16d, 0, 3 / 16d, 9 / 16d, 13 / 16d, 13 / 16d)).forDirectional();

    /**
     * B, C, E on the model's bottom face, centred on each pin. The pins span x 4-6, 7-9 and
     * 10-12, so their centres sit at x = 5, 8, 11 - 3 px apart, not the 2 px the design note
     * suggested.
     */
    private static final NodeConfigurator NODES = new NodeConfigurator.Builder()
            .add(5f, 0f, 8f)
            .add(8f, 0f, 8f)
            .add(11f, 0f, 8f)
            .simple(Direction.UP);

    public TransistorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public SimulatedDeviceType<TransistorDevice> getDevice() {
        return EPGSimulatedDevices.TRANSISTOR.get();
    }

    @Override
    public CompoundTag getDefaultDeviceData(Level level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        if (level.getBlockEntity(pos) instanceof TransistorBlockEntity be) {
            tag.putDouble("Beta", be.getBeta());
            tag.putDouble("VbeOn", be.getVbeOn());
            tag.putDouble("IcMax", be.getIcMax());
        }
        return tag;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(ROLL) ? SHAPE_ROLL : SHAPE).get(state.getValue(FACING));
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
            case 0 -> Component.translatable("energeticspowergrid.nodes.base");
            case 1 -> Component.translatable("energeticspowergrid.nodes.collector");
            default -> Component.translatable("energeticspowergrid.nodes.emitter");
        };
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return 2 / 16f;
    }

    private static NodeConfigurator nodes(BlockState state) {
        return state.getValue(ROLL) ? NODES.rotate(new Vec3(0, -90, 0)) : NODES;
    }

    @Override
    public Class<TransistorBlockEntity> getBlockEntityClass() {
        return TransistorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TransistorBlockEntity> getBlockEntityType() {
        return EPGBlockEntityTypes.TRANSISTOR.get();
    }
}
