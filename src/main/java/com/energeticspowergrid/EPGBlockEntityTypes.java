package com.energeticspowergrid;

import com.energeticspowergrid.content.excitation.ExcitationStatorBlockEntity;
import com.energeticspowergrid.content.factorylight.FactoryLightBlockEntity;
import com.energeticspowergrid.content.factorylight.FactoryLightLightBlockEntity;
import com.energeticspowergrid.content.factorylight.FactoryLightRenderer;
// import com.energeticspowergrid.content.fan.ElectricFanBlockEntity;
// import com.energeticspowergrid.content.fan.ElectricFanRenderer;
import com.energeticspowergrid.content.fixture.LightFixtureBlockEntity;
import com.energeticspowergrid.content.fixture.LightFixtureRenderer;
import com.energeticspowergrid.content.heater.HeatingCoilBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static com.energeticspowergrid.EnergeticsPowerGrid.REGISTRATE;

/**
 * 本模组的方块实体类型注册类。
 * <p>
 * 使用 Registrate 声明各方块实体：绑定其有效的方块，
 * 需要特殊渲染的注册对应渲染器。
 */
public class EPGBlockEntityTypes {
    /** 灯具固定座方块实体：处理灯具通电、灯泡安装与光照档位，带专用渲染器 */
    public static final BlockEntityEntry<LightFixtureBlockEntity> LIGHT_FIXTURE =
            REGISTRATE.blockEntity("light_fixture", LightFixtureBlockEntity::new)
                    .validBlock(EPGBlocks.LIGHT_FIXTURE)
                    .renderer(() -> LightFixtureRenderer::new)
                    .register();

    /** 工厂灯方块实体：处理工厂灯通电与投射光块生成，带专用渲染器（光束特效） */
    public static final BlockEntityEntry<FactoryLightBlockEntity> FACTORY_LIGHT =
            REGISTRATE.blockEntity("factory_light", FactoryLightBlockEntity::new)
                    .validBlock(EPGBlocks.FACTORY_LIGHT)
                    .renderer(() -> FactoryLightRenderer::new)
                    .register();

    /** 工厂灯投射光块方块实体：投射光源的附属方块实体，无需渲染器 */
    public static final BlockEntityEntry<FactoryLightLightBlockEntity> FACTORY_LIGHT_LIGHT =
            REGISTRATE.blockEntity("factory_light_light", FactoryLightLightBlockEntity::new)
                    .validBlock(EPGBlocks.FACTORY_LIGHT_LIGHT)
                    .register();

    // Disabled: duplicates electroenergetics:electric_fan
    // 电动鼓风机方块实体已禁用：与电力学原版的电动风扇功能重复
    // public static final BlockEntityEntry<ElectricFanBlockEntity> ELECTRIC_FAN =
    //         REGISTRATE.blockEntity("electric_fan", ElectricFanBlockEntity::new)
    //                 .validBlock(EPGBlocks.ELECTRIC_FAN)
    //                 .renderer(() -> ElectricFanRenderer::new)
    //                 .register();

    /** 加热线圈方块实体：处理线圈通电发热、温度模拟与物品加热逻辑 */
    public static final BlockEntityEntry<HeatingCoilBlockEntity> HEATING_COIL =
            REGISTRATE.blockEntity("heating_coil", HeatingCoilBlockEntity::new)
                    .validBlock(EPGBlocks.HEATING_COIL)
                    .register();

    /** 励磁定子方块实体：处理定子绕组通电并为相邻转子提供励磁场 */
    public static final BlockEntityEntry<ExcitationStatorBlockEntity> EXCITATION_STATOR =
            REGISTRATE.blockEntity("excitation_stator", ExcitationStatorBlockEntity::new)
                    .validBlock(EPGBlocks.EXCITATION_STATOR)
                    .register();

    /** 三极管方块实体：NPN 与 PNP 三极管共用同一方块实体类型，由方块实例区分极性 */
    public static final BlockEntityEntry<com.energeticspowergrid.content.transistor.TransistorBlockEntity> TRANSISTOR =
            REGISTRATE.blockEntity("transistor", com.energeticspowergrid.content.transistor.TransistorBlockEntity::new)
                    .validBlock(EPGBlocks.NPN_TRANSISTOR)
                    .validBlock(EPGBlocks.PNP_TRANSISTOR)
                    .register();

    /** MOS 管方块实体：N 沟道与 P 沟道 MOS 管共用同一方块实体类型，由方块实例区分极性 */
    public static final BlockEntityEntry<com.energeticspowergrid.content.mosfet.MosfetBlockEntity> MOSFET =
            REGISTRATE.blockEntity("mosfet", com.energeticspowergrid.content.mosfet.MosfetBlockEntity::new)
                    .validBlock(EPGBlocks.N_MOSFET)
                    .validBlock(EPGBlocks.P_MOSFET)
                    .register();

    /**
     * 注册入口（占位方法）。
     * Registrate 的方块实体注册在类加载静态字段初始化时已完成，
     * 主类调用本方法仅为确保本类被加载。
     */
    public static void register() {
    }
}
