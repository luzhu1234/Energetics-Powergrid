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

/**
 * 励磁定子的 BlockEntity。只负责一件事：持有当前励磁场强，并在数值变化时
 * 同步给客户端（供转子 mixin 之外的展示/调试使用，如护目镜提示）。
 * 真正的场强计算与折算逻辑在 {@link ExcitationStatorDevice}（服务端设备数据）中。
 */
public class ExcitationStatorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /**
     * 当前励磁场强（带符号：极性相反时可为负）。
     * 存在 BlockEntity 里随 NBT/客户端包走，因此存档与客户端显示天然一致。
     */
    private float fieldStrength;

    public ExcitationStatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 本 BlockEntity 不需要 Create 的额外 behaviour（如 kinetic/smart 观察器）
    }

    /**
     * 由设备侧在励磁场强变化时调用。
     * 设置 0.5 的死区：微小波动不写入也不 sendData()，避免励磁微调时
     * 向客户端刷大量无意义的同步包。
     */
    public void setFieldStrength(float fieldStrength) {
        if (Math.abs(this.fieldStrength - fieldStrength) < 0.5f)
            return;
        this.fieldStrength = fieldStrength;
        // 超过死区才触发客户端同步
        sendData();
    }

    public float getFieldStrength() {
        return fieldStrength;
    }

    /**
     * 护目镜提示：显示励磁定子名称与当前场强。
     * 负值（反极性）用红色警示，正常值用青色。
     */
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

    /**
     * NBT 读取：从存档/客户端同步包中恢复场强。
     * read/write 使用同一个键 "Field"，存档与同步共用同一份数据，天然不会脱节。
     */
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        fieldStrength = tag.getFloat("Field");
    }

    /**
     * NBT 写入：把当前场强写进存档（以及发送给客户端的同步包）。
     */
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Field", fieldStrength);
    }
}
