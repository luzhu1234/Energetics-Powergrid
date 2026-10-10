package com.energeticspowergrid.client;

import com.energeticspowergrid.EPGClientHooks;
import com.energeticspowergrid.EPGPartialModels;

/**
 * 客户端初始化入口类。
 * <p>
 * 仅在客户端环境（Dist.CLIENT）下由主类调用，
 * 负责触发客户端渲染资源（部分模型等）的注册，
 * 并把客户端专属逻辑注入公共代码的钩子（本类仅在此处被加载，
 * 因此其客户端依赖不会污染专用服务器）。
 */
public class EPGClient {
    /**
     * 客户端初始化：加载本模组的部分模型常量，并注入电铃客户端音频钩子。
     */
    public static void init() {
        EPGPartialModels.register();
        EPGClientHooks.bellAudio = BellSoundHandler::tickAudio;
    }
}
