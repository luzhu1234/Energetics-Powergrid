package com.energeticspowergrid.client;

import com.energeticspowergrid.EPGPartialModels;

/**
 * 客户端初始化入口类。
 * <p>
 * 仅在客户端环境（Dist.CLIENT）下由主类调用，
 * 负责触发客户端渲染资源（部分模型等）的注册。
 */
public class EPGClient {
    /**
     * 客户端初始化：加载本模组的部分模型常量。
     */
    public static void init() {
        EPGPartialModels.register();
    }
}
