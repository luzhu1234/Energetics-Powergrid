package com.energeticspowergrid;

import com.energeticspowergrid.content.bulb.GrowthLampItem;
import com.energeticspowergrid.content.bulb.LightBulbItem;
import com.energeticspowergrid.content.bulb.LvLightBulbItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.energeticspowergrid.EnergeticsPowerGrid.REGISTRATE;

/**
 * 本模组的物品注册类。
 * <p>
 * 注册三类灯泡物品，并通过 {@link LightBulbItem#setRated} 传入各自的
 * 额定参数（额定功率、额定电压、最小电阻、热质量、最大功率档位、是否可染色），
 * 由这些参数推导出灯丝热学特性与电气属性提示。
 */
public class EPGItems {
    static {
        // 取消默认创造物品栏，所有物品改由自定义物品栏（EPGCreativeTab）收纳
        REGISTRATE.defaultCreativeTab((net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) null);
    }

    /**
     * LV 白炽灯泡：低压小灯泡。
     * 额定参数：4W / 12V / 最小电阻 0.001Ω / 热质量 1 / 最大功率档位 1 / 可染色。
     * 电阻上限由 12V²/4W = 36Ω 推导，属于 12V 低压照明体系。
     */
    public static final ItemEntry<LvLightBulbItem> LV_LIGHT_BULB = REGISTRATE.item("lv_light_bulb", LvLightBulbItem::new)
            .transform(LightBulbItem.setRated(4, 12, 12, 0.001f, 1, true))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/lamps/light_bulb")))
            .lang("LV Incandescent Lamp")
            .register();

    /**
     * 白炽灯泡：标准市电灯泡。
     * 额定参数：11W / 220V / 最小电阻 1467Ω / 热质量 2 / 最大功率档位 2 / 可染色。
     * 电阻上限由 220V²/11W ≈ 4400Ω 推导，属于 220V 高压照明体系。
     */
    public static final ItemEntry<LightBulbItem> LIGHT_BULB = REGISTRATE.item("light_bulb", LightBulbItem::new)
            .transform(LightBulbItem.setRated(11, 220, 1467, 0.005f, 2, true))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/lamps/light_bulb")))
            .lang("Incandescent Lamp")
            .register();

    /**
     * 植物生长灯：促进农作物生长的特种灯。
     * 额定参数：110W / 220V / 最小电阻 147Ω / 热质量 2 / 最大功率档位 2 / 不可染色。
     * 功率较高，可催熟周围作物（作用半径与频率见 EPGConfigs 配置）。
     */
    public static final ItemEntry<GrowthLampItem> GROWTH_LAMP = REGISTRATE.item("growth_lamp", GrowthLampItem::new)
            .transform(LightBulbItem.setRated(110, 220, 147, 0.01f, 2, false))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/lamps/growth_lamp")))
            .register();

    /**
     * 注册入口（占位方法）。
     * Registrate 的物品注册在类加载静态字段初始化时已完成，
     * 主类调用本方法仅为确保本类被加载。
     */
    public static void register() {
    }
}
