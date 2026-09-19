package com.energeticspowergrid.content.fixture;

import com.energeticspowergrid.content.bulb.GrowthLampItem;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class LightFixtureDevice extends SimpleElectricalDevice {
    public static final float AMBIENT_TEMPERATURE = 22f;

    @Nullable
    private Item bulbItem = Items.AIR;
    private float temperature = AMBIENT_TEMPERATURE;
    private boolean burned;
    @Nullable
    private DyeColor color;
    private int overheatTicks;
    private boolean playEffect;

    public LightFixtureBlockEntity be;

    public LightFixtureDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
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
        setPowerLevel(0);
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
        if (!hasBulb() || burned)
            return;
        ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
        if (properties == null)
            return;
        double resistance = Math.max(0.1, ((ILightBulb) bulbItem).resistanceFunction(temperature));
        bridges.builder(pos).resistor(0, 1, resistance);
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
            setPowerLevel(0);
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
                setPowerLevel(0);
                spawnBurnParticles();
                syncToBlockEntity(true);
                return;
            }
        } else {
            overheatTicks = 0;
        }

        int powerLevel = 0;
        double rated = properties.ratedVoltage();
        if (rated > 0 && voltage >= rated * 0.85)
            powerLevel = 2;
        else if (rated > 0 && voltage >= rated * 0.45)
            powerLevel = 1;
        powerLevel = Math.min(powerLevel, properties.maxPowerLevel());
        setPowerLevel(powerLevel);

        if (powerLevel > 0 && bulbItem instanceof GrowthLampItem growthLamp && level instanceof ServerLevel serverLevel) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LightFixtureBlock)
                growthLamp.tickCrops(serverLevel, pos, state.getValue(LightFixtureBlock.FACING));
        }

        syncToBlockEntity(false);
    }

    private void setPowerLevel(int powerLevel) {
        if (!level.isLoaded(pos))
            return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LightFixtureBlock))
            return;
        if (state.getValue(LightFixtureBlock.POWER) != powerLevel)
            level.setBlockAndUpdate(pos, state.setValue(LightFixtureBlock.POWER, powerLevel));
    }

    private void spawnBurnParticles() {
        if (level instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    private void syncToBlockEntity(boolean force) {
        if (!level.isLoaded(pos))
            return;
        if (be == null && level.getBlockEntity(pos) instanceof LightFixtureBlockEntity found)
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
        if (tag.contains("Bulb")) {
            bulbItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Bulb")));
        } else {
            bulbItem = Items.AIR;
        }
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        if (tag.contains("Color"))
            color = DyeColor.byId(tag.getInt("Color"));
        else
            color = null;
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
        return !(newState.getBlock() instanceof LightFixtureBlock);
    }

    public CompoundTag writeClientTag() {
        CompoundTag tag = new CompoundTag();
        write(tag);
        if (playEffect)
            tag.putBoolean("Effect", true);
        return tag;
    }

    @SuppressWarnings("unused")
    public void readClientTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag);
    }
}
