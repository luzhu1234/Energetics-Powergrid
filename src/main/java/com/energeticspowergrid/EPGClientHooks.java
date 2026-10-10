package com.energeticspowergrid;

import com.energeticspowergrid.content.bell.ElectricBellBlockEntity;

import java.util.function.Consumer;

/**
 * 客户端钩子容器：把"只能在客户端执行"的逻辑从公共代码中解耦。
 * <p>
 * 公共代码（方块实体等）只引用本类；钩子的实现由客户端初始化时
 * （{@code EPGClient.init()}，仅 Dist.CLIENT 加载）注入。
 * 专用服务器上钩子保持为空实现，从而避免公共类加载客户端专属类
 * 导致的专用服务器崩溃（RuntimeDistCleaner 约束）。
 */
public class EPGClientHooks {
    /** 电铃客户端音频钩子：根据方块实体同步的音量/音调启停铃声。 */
    public static Consumer<ElectricBellBlockEntity> bellAudio = be -> {
    };
}
