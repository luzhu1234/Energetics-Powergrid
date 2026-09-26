package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.content.bulb.GrowthLampItem;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FactoryLightDevice extends SimpleElectricalDevice {
    public static final float AMBIENT_TEMPERATURE = 22f;
    private static final double SHARE_RESISTANCE = 0.001;

    @Nullable
    private Item bulbItem = Items.AIR;
    private float temperature = AMBIENT_TEMPERATURE;
    private boolean burned;
    @Nullable
    private DyeColor color;
    private int overheatTicks;
    private boolean playEffect;
    public FactoryLightBlockEntity be;

    public FactoryLightDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    public boolean hasBulb() {
        return bulbItem != null && bulbItem != Items.AIR && bulbItem instanceof ILightBulb;
    }

    public Item getBulbItem() {
        return bulbItem == null ? Items.AIR : bulbItem;
    }

    public float getTemperature() {
        return temperature;
    }

    public boolean isBurned() {
        return burned;
    }

    @Nullable
    public DyeColor getColor() {
        return color;
    }

    public void setInstalledBulb(@Nullable Item item, @Nullable DyeColor color) {
        this.bulbItem = item == null ? Items.AIR : item;
        this.color = color;
        this.temperature = AMBIENT_TEMPERATURE;
        this.burned = false;
        this.overheatTicks = 0;
        this.playEffect = false;
        setPowerLevel(hasBulb() ? 1 : 0);
        syncToBlockEntity(true);
    }

    public boolean trySetColor(DyeColor color) {
        if (!hasBulb())
            return false;
        ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
        if (properties == null || !properties.dyeable())
            return false;
        this.color = color;
        syncToBlockEntity(true);
        return true;
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FactoryLightBlock))
            return;
        Direction.Axis axis = state.getValue(FactoryLightBlock.HORIZONTAL_AXIS);
        shareWith(bridges, pos.relative(axis, 1), state);
        if (hasBulb() && !burned) {
            ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
            if (properties != null) {
                double resistance = Math.max(0.1, ((ILightBulb) bulbItem).resistanceFunction(temperature));
                bridges.builder(pos).resistor(0, 1, resistance);
            }
        }
    }

    private void shareWith(BridgeCollector bridges, BlockPos neighbor, BlockState self) {
        if (!level.isLoaded(neighbor))
            return;
        BlockState other = level.getBlockState(neighbor);
        if (!(other.getBlock() instanceof FactoryLightBlock))
            return;
        if (other.getValue(FactoryLightBlock.HORIZONTAL_AXIS) != self.getValue(FactoryLightBlock.HORIZONTAL_AXIS))
            return;
        // CEE 1.1.1 has bridge(Node, Node, ElectricalProperties) but not the double overload added in 1.2.0.
        ElectricalProperties share = ElectricalProperties.resistor(SHARE_RESISTANCE);
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(0, neighbor), share);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(1, neighbor), share);
    }

    @Override
    public void postTick(SimulationResults results) {
        if (!hasBulb()) {
            setPowerLevel(0);
            return;
        }
        ILightBulb bulb = (ILightBulb) bulbItem;
        ILightBulb.ThermalProperties properties = bulb.thermalProperties();
        if (properties == null)
            return;
        if (burned) {
            setPowerLevel(1);
            return;
        }

        double voltage = Math.abs(results.getVoltageAt(pos, 0, 1));
        double heat = results.getHeatLoss(pos, 0, 1);
        float dissipatedPower = properties.dissipationFactor() * (temperature - AMBIENT_TEMPERATURE);
        temperature += (float) ((heat - dissipatedPower) / 20.0 / properties.thermalMass());
        if (!Float.isFinite(temperature) || temperature < AMBIENT_TEMPERATURE)
            temperature = AMBIENT_TEMPERATURE;

        boolean overvoltage = voltage >= properties.breakdownVoltage();
        boolean overheated = temperature >= properties.overheatTemperature();
        if (EPGConfigs.server().componentDamage.get() && (overvoltage || overheated)) {
            if (++overheatTicks >= 4) {
                burned = true;
                playEffect = true;
                temperature = AMBIENT_TEMPERATURE;
                setPowerLevel(1);
                spawnBurnParticles();
                syncToBlockEntity(true);
                return;
            }
        } else {
            overheatTicks = 0;
        }

        int glow = 0;
        double rated = properties.ratedVoltage();
        if (rated > 0 && voltage >= rated * 0.85)
            glow = 2;
        else if (rated > 0 && voltage >= rated * 0.45)
            glow = 1;
        glow = Math.min(glow, properties.maxPowerLevel());
        setPowerLevel(glow + 1);

        if (glow > 0 && bulbItem instanceof GrowthLampItem growthLamp && level instanceof ServerLevel serverLevel) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FactoryLightBlock)
                growthLamp.tickCrops(serverLevel, projectionTarget(), Direction.DOWN);
        }
        syncToBlockEntity(false);
    }

    private BlockPos projectionTarget() {
        int range = EPGConfigs.server().factoryLightProjectionRange.get();
        BlockPos last = pos;
        for (int i = 1; i < range; i++) {
            BlockPos below = pos.below(i);
            if (!level.isLoaded(below))
                break;
            BlockState state = level.getBlockState(below);
            if (state.getBlock() instanceof FactoryLightLightBlock)
                last = below;
            else if (!state.isAir())
                break;
            else
                last = below;
        }
        return last;
    }

    private void setPowerLevel(int powerLevel) {
        if (!level.isLoaded(pos))
            return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FactoryLightBlock))
            return;
        if (state.getValue(FactoryLightBlock.POWER) != powerLevel)
            level.setBlockAndUpdate(pos, state.setValue(FactoryLightBlock.POWER, powerLevel));
        if (be != null && !be.isRemoved())
            be.projectDown(Math.max(powerLevel - 1, 0));
    }

    private void spawnBurnParticles() {
        if (level instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    private void syncToBlockEntity(boolean force) {
        if (!level.isLoaded(pos))
            return;
        if (be == null && level.getBlockEntity(pos) instanceof FactoryLightBlockEntity found)
            be = found;
        if (be == null || be.isRemoved()) {
            be = null;
            return;
        }
        be.applyFromDevice(getBulbItem(), temperature, burned, color, playEffect, force);
        playEffect = false;
    }

    @Override
    public void read(CompoundTag tag) {
        if (tag.contains("Bulb"))
            bulbItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Bulb")));
        else
            bulbItem = Items.AIR;
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        color = tag.contains("Color") ? DyeColor.byId(tag.getInt("Color")) : null;
    }

    @Override
    public void write(CompoundTag tag) {
        if (hasBulb())
            tag.putString("Bulb", BuiltInRegistries.ITEM.getKey(bulbItem).toString());
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Burned", burned);
        if (color != null)
            tag.putInt("Color", color.getId());
    }

    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof FactoryLightBlock);
    }

    @SuppressWarnings("unused")
    public void readClientTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag);
    }
}
