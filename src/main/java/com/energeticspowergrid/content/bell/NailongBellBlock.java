package com.energeticspowergrid.content.bell;

/**
 * 奶龙电铃方块：与普通电铃完全同款（两端子 20Ω 电阻、同样的音量/音调
 * 随电流变化的驱动逻辑），唯一区别是客户端播放的音效不同——
 * 通电循环播放奶龙捧腹大笑音效，断电静默停止。
 * <p>
 * 拥有独立的模型与贴图文件（{@code models/block/nailong_bell.json}、
 * {@code textures/block/nailong_bell.png} / {@code nailong_bell_base.png}），
 * 便于后续单独替换外观。客户端音频处理经 {@code BellSoundHandler}
 * 检测方块类型选择音效。
 */
public class NailongBellBlock extends ElectricBellBlock {
    public NailongBellBlock(Properties properties) {
        super(properties);
    }

    /** 标记为奶龙电铃：客户端据此选择奶龙音效。 */
    @Override
    public boolean isNailongBell() {
        return true;
    }
}
