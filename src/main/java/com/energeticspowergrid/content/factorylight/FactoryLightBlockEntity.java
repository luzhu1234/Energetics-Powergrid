package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.EPGBlocks;
import com.energeticspowergrid.config.EPGConfigs;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE;

public class FactoryLightBlockEntity extends SmartBlockEntity {
    private Item bulbItem = Items.AIR;
    private float temperature = FactoryLightDevice.AMBIENT_TEMPERATURE;
    private boolean burned;
    @Nullable
    private DyeColor color;
    private boolean playEffect;
    private BlockPos lastHitBlock;

    public FactoryLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Nullable
    public FactoryLightDevice getDevice() {
        if (!(level instanceof ServerLevel serverLevel))
            return null;
        return DevicesSavedData.load(serverLevel).getDevice(worldPosition, FactoryLightDevice.class);
    }

    public boolean hasBulb() {
        return bulbItem != null && bulbItem != Items.AIR && bulbItem instanceof ILightBulb;
    }

    public Item getBulbItem() {
        return bulbItem == null ? Items.AIR : bulbItem;
    }

    public boolean isBurned() {
        return burned;
    }

    @Nullable
    public DyeColor getColor() {
        return color;
    }

    public float getAlpha() {
        float x = Mth.clamp((temperature - 600f) / (1400f - 600f), 0, 1);
        int powerLevel = Math.max(getBlockState().getValue(FactoryLightBlock.POWER) - 1, 0);
        if (powerLevel == 2)
            return 1;
        if (powerLevel == 1)
            return Math.max(0.5625f, x * x);
        return x * x;
    }

    public int getPowerLevel() {
        return Math.max(getBlockState().getValue(FactoryLightBlock.POWER) - 1, 0);
    }

    public ItemStack copyBulbStack() {
        if (!hasBulb() || burned)
            return ItemStack.EMPTY;
        return new ItemStack(bulbItem);
    }

    public void applyFromDevice(Item item, float temperature, boolean burned, @Nullable DyeColor color, boolean playEffect, boolean force) {
        boolean changed = this.bulbItem != item || this.burned != burned || this.color != color || Math.abs(this.temperature - temperature) > 8f || force;
        this.bulbItem = item;
        this.temperature = temperature;
        this.burned = burned;
        this.color = color;
        this.playEffect = playEffect;
        if (changed && !level.isClientSide)
            sendData();
    }

    public boolean replaceBulb(Player player, InteractionHand hand, ItemStack usedStack) {
        if (level == null)
            return false;
        boolean result = replaceBulbInternal(player, hand, usedStack);
        if (result && !level.isClientSide) {
            FactoryLightDevice device = getDevice();
            if (device != null)
                device.setInstalledBulb(hasBulb() ? bulbItem : Items.AIR, color);
            notifyUpdate();
        }
        return result;
    }

    private boolean replaceBulbInternal(Player player, InteractionHand hand, ItemStack usedStack) {
        if (usedStack == null || usedStack.isEmpty()) {
            if (!hasBulb())
                return false;
            if (!level.isClientSide) {
                if (!burned)
                    player.setItemInHand(hand, new ItemStack(bulbItem));
                clearBulb();
            }
            return true;
        }
        if (!hasBulb()) {
            if (!level.isClientSide && usedStack.getItem() instanceof ILightBulb) {
                bulbItem = usedStack.getItem();
                burned = false;
                temperature = FactoryLightDevice.AMBIENT_TEMPERATURE;
                color = null;
                if (((ILightBulb) bulbItem).thermalProperties() != null && ((ILightBulb) bulbItem).thermalProperties().dyeable()
                        && player.getOffhandItem().getItem() instanceof DyeItem dye)
                    color = dye.getDyeColor();
                if (!player.isCreative())
                    usedStack.shrink(1);
            }
            return true;
        }
        if (burned) {
            if (!level.isClientSide)
                clearBulb();
            return true;
        }
        if (bulbItem == usedStack.getItem() && usedStack.getCount() < usedStack.getMaxStackSize()) {
            if (!level.isClientSide) {
                if (!player.isCreative())
                    usedStack.grow(1);
                clearBulb();
            }
            return true;
        }
        if (player.isCreative()) {
            if (!level.isClientSide)
                clearBulb();
            return true;
        }
        return false;
    }

    private void clearBulb() {
        bulbItem = Items.AIR;
        burned = false;
        color = null;
        temperature = FactoryLightDevice.AMBIENT_TEMPERATURE;
    }

    public ItemInteractionResult setColor(DyeColor color) {
        if (!hasBulb())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
        if (properties == null || !properties.dyeable())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        this.color = color;
        if (!level.isClientSide) {
            FactoryLightDevice device = getDevice();
            if (device != null)
                device.trySetColor(color);
            notifyUpdate();
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide && playEffect) {
            var center = worldPosition.getCenter();
            level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0, 0, 0);
            playEffect = false;
        }
        if (level instanceof ServerLevel && getDevice() instanceof FactoryLightDevice device)
            device.be = this;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (!level.isClientSide)
            projectDown(getPowerLevel());
    }

    public void projectDown(int bulbPower) {
        if (level == null || level.isClientSide)
            return;
        int range = EPGConfigs.server().factoryLightProjectionRange.get();
        if (lastHitBlock == null)
            lastHitBlock = worldPosition;
        if (bulbPower > 0) {
            for (int i = 1; i < range; ++i) {
                var pos = worldPosition.below(i);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof FactoryLightLightBlock) {
                    if (state.getValue(FactoryLightLightBlock.POWER) != bulbPower - 1)
                        level.setBlock(pos, state.setValue(FactoryLightLightBlock.POWER, bulbPower - 1), UPDATE_ALL_IMMEDIATE);
                } else if (state.isAir()) {
                    level.setBlock(pos, EPGBlocks.FACTORY_LIGHT_LIGHT.getDefaultState()
                            .setValue(FactoryLightLightBlock.POWER, bulbPower - 1), UPDATE_ALL_IMMEDIATE);
                } else {
                    if (!lastHitBlock.equals(pos)) {
                        lastHitBlock = pos;
                        removeLights();
                    }
                    break;
                }
            }
        } else {
            for (int i = 1; i < range; ++i) {
                var pos = worldPosition.below(i);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof FactoryLightLightBlock)
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL_IMMEDIATE);
                else
                    break;
            }
        }
    }

    public void removeLights() {
        if (level == null)
            return;
        int range = EPGConfigs.server().factoryLightProjectionRange.get();
        for (int i = 0; i < range; ++i) {
            var pos = worldPosition.below(i);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof FactoryLightLightBlock) {
                if (level.getBlockEntity(pos) instanceof FactoryLightLightBlockEntity light)
                    light.onDelete();
            }
            if (state.getBlock() instanceof FactoryLightBlock && !this.getBlockPos().equals(pos))
                break;
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0, EPGConfigs.server().factoryLightProjectionRange.get(), 0);
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        if (hasBulb() && !burned)
            return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, bulbItem);
        return ItemRequirement.NONE;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (hasBulb())
            tag.putString("Bulb", BuiltInRegistries.ITEM.getKey(bulbItem).toString());
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Burned", burned);
        if (color != null)
            tag.putInt("Color", color.getId());
        if (playEffect) {
            tag.putBoolean("Effect", true);
            playEffect = false;
        }
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        if (hasBulb())
            tag.putString("Bulb", BuiltInRegistries.ITEM.getKey(bulbItem).toString());
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Burned", burned);
        if (color != null)
            tag.putInt("Color", color.getId());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Bulb"))
            bulbItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Bulb")));
        else
            bulbItem = Items.AIR;
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : FactoryLightDevice.AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        color = tag.contains("Color") ? DyeColor.byId(tag.getInt("Color")) : null;
        playEffect = tag.getBoolean("Effect");
    }
}
