package com.energeticspowergrid.content.bell;

/**
 * 彩蛋电铃方块：与普通电铃完全同款（两端子 20Ω 电阻、同样的音量/音调
 * 随电流变化的驱动逻辑），唯一区别是客户端播放的音效不同——
 * 通电循环播放彩蛋铃声（冰冰冰），断电没有收尾音效。
 * <p>
 * 电气/方块实体与普通电铃共用（同设备类型、同方块实体类型），
 * 客户端音频处理经 {@code BellSoundHandler} 检测方块类型选择音效。
 * 合成方式：普通电铃围上 8 个冰。
 */
public class IceBellBlock extends ElectricBellBlock {
    public IceBellBlock(Properties properties) {
        super(properties);
    }

    /** 标记为彩蛋电铃：客户端据此选择彩蛋音效。 */
    @Override
    public boolean isIceBell() {
        return true;
    }
}
