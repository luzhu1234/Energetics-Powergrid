package com.energeticspowergrid;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组音效事件注册：电铃的铃声/收尾音效（移植自电气时代 PowerGrid）
 * 与彩蛋电铃物品的彩蛋音效。实际音频文件位于
 * assets/energeticspowergrid/sounds/，由 sounds.json 索引。
 */
public class EPGSounds {
    /** 音效事件注册表。 */
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, EnergeticsPowerGrid.ID);

    /** 电铃铃声（循环播放的主体）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_BELL =
            SOUND_EVENTS.register("alarm_bell", () -> SoundEvent.createVariableRangeEvent(EnergeticsPowerGrid.rl("alarm_bell")));

    /** 电铃断电后的收尾音效（铃声渐停的最后一下）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_BELL_END =
            SOUND_EVENTS.register("alarm_bell_end", () -> SoundEvent.createVariableRangeEvent(EnergeticsPowerGrid.rl("alarm_bell_end")));

    /** 彩蛋电铃物品的彩蛋音效（冰冰冰）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_BELL =
            SOUND_EVENTS.register("ice_bell", () -> SoundEvent.createVariableRangeEvent(EnergeticsPowerGrid.rl("ice_bell")));

    /** 奶龙电铃的循环音效（奶龙捧腹大笑）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> NAILONG_BELL =
            SOUND_EVENTS.register("nailong_bell", () -> SoundEvent.createVariableRangeEvent(EnergeticsPowerGrid.rl("nailong_bell")));

    /** 注册入口（主类构造器调用）。 */
    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
