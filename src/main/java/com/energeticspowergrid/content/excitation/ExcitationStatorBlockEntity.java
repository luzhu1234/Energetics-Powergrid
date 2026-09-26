package com.energeticspowergrid.content.excitation;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ExcitationStatorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private float fieldStrength;

    public ExcitationStatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void setFieldStrength(float fieldStrength) {
        if (Math.abs(this.fieldStrength - fieldStrength) < 0.5f)
            return;
        this.fieldStrength = fieldStrength;
        sendData();
    }

    public float getFieldStrength() {
        return fieldStrength;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder("energeticspowergrid").translate("gui.excitation.title")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        Lang.builder("energeticspowergrid")
                .text(LangNumberFormat.format(Math.round(fieldStrength)))
                .style(fieldStrength < 0 ? ChatFormatting.RED : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        fieldStrength = tag.getFloat("Field");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Field", fieldStrength);
    }
}
