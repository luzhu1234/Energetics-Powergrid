package com.energeticspowergrid.content.bulb;

/**
 * LV（低压）白炽灯泡物品。
 * <p>
 * 行为完全继承自 {@link LightBulbItem}，仅在注册时通过
 * {@code setRated(4, 12, ...)} 以更低电压（12V）与功率（4W）区分。
 * 该类存在是为了让两种灯泡在注册与物品栈判断时属于不同物品类型。
 */
public class LvLightBulbItem extends LightBulbItem {
    /**
     * 构造 LV 白炽灯泡物品。
     *
     * @param properties 物品属性
     */
    public LvLightBulbItem(Properties properties) {
        super(properties);
    }
}
