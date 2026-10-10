package com.energeticspowergrid.client;

import com.energeticspowergrid.EPGSounds;
import com.energeticspowergrid.content.bell.IceBellBlock;
import com.energeticspowergrid.content.bell.NailongBellBlock;
import com.energeticspowergrid.content.bell.ElectricBellBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 电铃客户端音频处理（仅客户端加载）：维护铃声循环实例，
 * 根据方块实体同步来的音量/音调实时调整，断电时播放收尾音效。
 * 普通电铃与彩蛋电铃（{@link IceBellBlock}）共用本处理器，
 * 循环音效按方块类型选择；收尾音效仅普通电铃播放。
 */
public final class BellSoundHandler {
    private BellSoundHandler() {
    }

    /** 每刻驱动的入口（由 EPGClientHooks.bellAudio 注入调用）。 */
    public static void tickAudio(ElectricBellBlockEntity be) {
        SoundEvent loop = loopSoundOf(be);
        if (be.getVolume() > 0 && !be.hasSoundInstance()) {
            Minecraft.getInstance().getSoundManager().play(new BellSoundInstance(be, loop));
            be.markSoundStarted();
        } else if (be.getVolume() == 0 && be.hasSoundInstance()) {
            if (!isEasterEgg(be)) {
                // 收尾"叮"声仅普通电铃播放；彩蛋/奶龙电铃直接静默停止
                var center = be.getBlockPos().getCenter();
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                        EPGSounds.ALARM_BELL_END.get(), SoundSource.BLOCKS, be.getPrevVolume(), be.getPrevPitch(),
                        be.getLevel().random, center.x, center.y, center.z));
            }
            be.markSoundStopped();
        }
    }

    /** 按方块类型选择循环音效：奶龙 > 彩蛋 > 普通铃声。 */
    private static SoundEvent loopSoundOf(ElectricBellBlockEntity be) {
        if (be.getBlockState().getBlock() instanceof NailongBellBlock)
            return EPGSounds.NAILONG_BELL.get();
        if (be.getBlockState().getBlock() instanceof IceBellBlock)
            return EPGSounds.ICE_BELL.get();
        return EPGSounds.ALARM_BELL.get();
    }

    /** 是否为彩蛋系电铃（无收尾音效）。 */
    private static boolean isEasterEgg(ElectricBellBlockEntity be) {
        return be.getBlockState().getBlock() instanceof IceBellBlock
                || be.getBlockState().getBlock() instanceof NailongBellBlock;
    }

    /** 客户端循环铃声实例：跟随方块实体的音量/音调，方块移除或断电时停止。 */
    static class BellSoundInstance extends AbstractTickableSoundInstance {
        private final ElectricBellBlockEntity be;

        BellSoundInstance(ElectricBellBlockEntity be, SoundEvent sound) {
            super(sound, SoundSource.BLOCKS, be.getLevel().random);
            this.be = be;
            var center = be.getBlockPos().getCenter();
            this.x = center.x;
            this.y = center.y;
            this.z = center.z;
            this.attenuation = Attenuation.LINEAR;
            this.looping = true;
            this.delay = 0;
            this.volume = 0f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (be.isRemoved()) {
                stop();
            } else {
                if (be.getVolume() == 0) {
                    stop();
                    return;
                }
                volume = be.getVolume();
                pitch = be.getPitch();
            }
        }
    }
}
