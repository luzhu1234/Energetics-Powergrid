package com.energeticspowergrid.content.bell;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.energeticspowergrid.EPGClientHooks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 电铃方块实体：承载由服务端解算结果同步来的音量/音调，
 * 并在客户端驱动铃声的播放与停止（移植自电气时代 PowerGrid 报警铃）。
 * <p>
 * 音频逻辑在客户端执行——为避免公共类引用客户端专属类导致专用服务器
 * 崩溃，实际播放入口经 {@link EPGClientHooks#bellAudio} 钩子注入
 * （客户端初始化时由 EPGClient 注册，专用服务器上为空实现）。
 * 音量/音调由服务端的 {@link ElectricBellDevice#postTick} 解算后经
 * sendData → write/read(clientPacket) 同步到客户端。
 */
public class ElectricBellBlockEntity extends SmartBlockEntity {
    /** 当前音量（0~1，服务端解算后同步）。 */
    private float volume;
    /** 当前音调（0.75~1.25，服务端解算后同步）。 */
    private float pitch;
    /** 上一刻的音量/音调（用于断电收尾音效的响度）。 */
    private float prevVolume, prevPitch;
    /** 客户端：循环铃声实例是否已启动。 */
    private boolean hasSoundInstance;

    public ElectricBellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 电铃没有可调参数菜单。 */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /** 客户端每刻：经钩子根据同步来的音量启停铃声。 */
    @Override
    public void tick() {
        if (level != null && level.isClientSide)
            EPGClientHooks.bellAudio.accept(this);
    }

    /** 当前音量（供铃声实例每刻读取）。 */
    public float getVolume() {
        return volume;
    }

    /** 当前音调（供铃声实例每刻读取）。 */
    public float getPitch() {
        return pitch;
    }

    /** 上一刻的音量（供收尾音效读取）。 */
    public float getPrevVolume() {
        return prevVolume;
    }

    /** 上一刻的音调（供收尾音效读取）。 */
    public float getPrevPitch() {
        return prevPitch;
    }

    /** 客户端：循环铃声实例是否已启动（供客户端音频处理读取）。 */
    public boolean hasSoundInstance() {
        return hasSoundInstance;
    }

    /** 客户端：标记循环铃声实例已启动。 */
    public void markSoundStarted() {
        hasSoundInstance = true;
    }

    /** 客户端：标记循环铃声实例已停止。 */
    public void markSoundStopped() {
        hasSoundInstance = false;
    }

    /**
     * 服务端：接收器件解算出的音量/音调。
     * 数值有变化时 sendData 同步到正在观察该方块的客户端。
     */
    public void setAudio(float volume, float pitch) {
        boolean changed = Math.abs(volume - this.volume) > 0.005f || Math.abs(pitch - this.pitch) > 0.005f;
        this.volume = volume;
        this.pitch = pitch;
        if (changed)
            sendData();
    }

    /** 客户端同步：铃声的音量/音调（SmartBlockEntity 的 writeClient 为 final，经此转发）。 */
    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putFloat("Volume", volume);
            tag.putFloat("Pitch", pitch);
        }
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            volume = tag.getFloat("Volume");
            pitch = tag.getFloat("Pitch");
        }
    }
}
