package com.energeticspowergrid;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/**
 * 本模组的部分模型（PartialModel）与连接纹理注册类。
 * <p>
 * 灯泡类模型按状态分为：熄灭、点亮、破损、光晕四组；
 * 另含工厂灯光束特效模型与导电外壳的连接纹理。
 */
public class EPGPartialModels {
    /** 普通灯泡模型（熄灭状态） */
    public static final PartialModel LIGHT_BULB = model("block/lamps/light_bulb");
    /** 普通灯泡模型（点亮状态） */
    public static final PartialModel LIGHT_BULB_ON = model("block/lamps/light_bulb_on");
    /** 普通灯泡模型（过载烧毁状态） */
    public static final PartialModel LIGHT_BULB_BROKEN = model("block/lamps/light_bulb_broken");
    /** 普通灯泡模型（发光光晕） */
    public static final PartialModel LIGHT_BULB_LIGHT = model("block/lamps/light_bulb_light");

    /** 染色灯泡模型（熄灭状态） */
    public static final PartialModel DYED_LIGHT_BULB = model("block/lamps/dyed_light_bulb");
    /** 染色灯泡模型（点亮状态） */
    public static final PartialModel DYED_LIGHT_BULB_ON = model("block/lamps/dyed_light_bulb_on");
    /** 染色灯泡模型（过载烧毁状态） */
    public static final PartialModel DYED_LIGHT_BULB_BROKEN = model("block/lamps/dyed_light_bulb_broken");
    /** 染色灯泡模型（发光光晕） */
    public static final PartialModel DYED_LIGHT_BULB_LIGHT = model("block/lamps/dyed_light_bulb_light");
    /** 染色灯泡模型（玻璃外壳部分，用于叠加染色层） */
    public static final PartialModel DYED_LIGHT_BULB_BULB = model("block/lamps/dyed_light_bulb_bulb");

    /** 植物生长灯模型（熄灭状态） */
    public static final PartialModel GROWTH_LAMP = model("block/lamps/growth_lamp");
    /** 植物生长灯模型（点亮状态） */
    public static final PartialModel GROWTH_LAMP_ON = model("block/lamps/growth_lamp_on");
    /** 植物生长灯模型（过载烧毁状态） */
    public static final PartialModel GROWTH_LAMP_BROKEN = model("block/lamps/growth_lamp_broken");
    /** 植物生长灯模型（发光光晕） */
    public static final PartialModel GROWTH_LAMP_LIGHT = model("block/lamps/growth_lamp_light");

    /** 工厂灯光束特效：单束光锥模型 */
    public static final PartialModel FL_RAYS_SINGLE = model("block/factory_light/godrayssingular");
    /** 工厂灯光束特效：中央光锥模型 */
    public static final PartialModel FL_RAYS_CENTER = model("block/factory_light/godrayscenter");
    /** 工厂灯光束特效：前缘光锥模型 */
    public static final PartialModel FL_RAYS_FRONT = model("block/factory_light/godraysedgefront");
    /** 工厂灯光束特效：后缘光锥模型 */
    public static final PartialModel FL_RAYS_BACK = model("block/factory_light/godraysedgeback");

    // Disabled: electric blower duplicates CEE electric fan
    // 电动鼓风机扇叶模型已禁用：与电力学原版的电动风扇功能重复
    // public static final PartialModel FAN_PROPELLER = model("block/electric_fan/propeller");

    /** 导电外壳的全向连接纹理（本体贴图 + 相邻拼接贴图） */
    public static final CTSpriteShiftEntry CONDUCTIVE_CASING = CTSpriteShifter.getCT(
            AllCTTypes.OMNIDIRECTIONAL,
            EnergeticsPowerGrid.rl("block/conductive_casing"),
            EnergeticsPowerGrid.rl("block/conductive_casing_connected"));

    /**
     * 以模组命名空间构造部分模型。
     *
     * @param path 模型资源路径
     * @return 对应的 PartialModel 实例
     */
    private static PartialModel model(String path) {
        return PartialModel.of(EnergeticsPowerGrid.rl(path));
    }

    /**
     * 注册入口（占位方法）。
     * 模型常量在类加载静态字段初始化时已完成注册，
     * 主类调用本方法仅为确保本类被加载。
     */
    public static void register() {
    }
}
