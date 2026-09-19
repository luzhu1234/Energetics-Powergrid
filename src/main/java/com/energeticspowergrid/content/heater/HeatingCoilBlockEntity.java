package com.energeticspowergrid.content.heater;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class HeatingCoilBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public enum State {
        COLD,
        SMOKING,
        BLASTING
    }

    private float temperature = HeatingCoilDevice.AMBIENT_TEMPERATURE;
    private State state = State.COLD;

    public HeatingCoilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void setTemperature(float temperature) {
        boolean changed = Math.abs(this.temperature - temperature) > 0.5f;
        this.temperature = temperature;
        State next = stateOf(temperature);
        if (this.state != next) {
            this.state = next;
            if (level != null)
                level.blockUpdated(worldPosition, getBlockState().getBlock());
            sendData();
        } else if (changed)
            sendData();
    }

    public State getState() {
        return state;
    }

    public float getTemperature() {
        return temperature;
    }

    private static State stateOf(float temperature) {
        if (temperature < 200f)
            return State.COLD;
        if (temperature < 400f)
            return State.SMOKING;
        return State.BLASTING;
    }

    private static ChatFormatting temperatureColor(float value) {
        if (value < 200f)
            return ChatFormatting.DARK_GRAY;
        if (value < 400f)
            return ChatFormatting.GREEN;
        if (value < 550f)
            return ChatFormatting.YELLOW;
        return ChatFormatting.RED;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder("energeticspowergrid").translate("gui.heater.info_header").forGoggles(tooltip);
        Lang.builder("energeticspowergrid").translate("gui.heater.title")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        float shown = Math.round(temperature * 100f) / 100f;
        Lang.builder("energeticspowergrid")
                .add(Component.literal(String.format("%.2f °C", shown)))
                .style(temperatureColor(shown))
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        temperature = tag.getFloat("Temperature");
        state = stateOf(temperature);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Temperature", temperature);
    }
}
