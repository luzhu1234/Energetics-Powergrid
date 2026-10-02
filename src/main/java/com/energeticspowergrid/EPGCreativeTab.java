package com.energeticspowergrid;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的创造模式物品栏注册类。
 * <p>
 * 注册一个自定义物品栏"base"，收纳本模组的全部方块与物品，
 * 并按类别排序展示。
 */
public class EPGCreativeTab {
    /** 创造模式物品栏的延迟注册器 */
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EnergeticsPowerGrid.ID);

    /**
     * 主物品栏：图标为灯具固定座，按"灯具→灯泡→工业设备→半导体器件"顺序收纳全部内容。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = REGISTER.register("base",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.energeticspowergrid"))
                    .icon(EPGBlocks.LIGHT_FIXTURE::asStack)
                    .displayItems((parameters, output) -> {
                        output.accept(EPGBlocks.LIGHT_FIXTURE.asStack());      // 灯具固定座
                        output.accept(EPGItems.LV_LIGHT_BULB.asStack());       // LV 白炽灯泡
                        output.accept(EPGItems.LIGHT_BULB.asStack());          // 白炽灯泡
                        output.accept(EPGItems.GROWTH_LAMP.asStack());         // 植物生长灯
                        output.accept(EPGBlocks.FACTORY_LIGHT.asStack());      // 工厂灯
                        output.accept(EPGBlocks.CONDUCTIVE_CASING.asStack());  // 导电外壳
                        // output.accept(EPGBlocks.ELECTRIC_FAN.asStack());    // 电动鼓风机（已禁用，与电力学风扇重复）
                        output.accept(EPGBlocks.HEATING_COIL.asStack());       // 加热线圈
                        output.accept(EPGBlocks.REVERSING_SWITCH.asStack());   // 换向开关
                        output.accept(EPGBlocks.EXCITATION_STATOR.asStack());  // 励磁定子
                        output.accept(EPGBlocks.INVERTER.asStack());           // 逆变器
                        output.accept(EPGBlocks.NPN_TRANSISTOR.asStack());     // NPN 三极管
                        output.accept(EPGBlocks.PNP_TRANSISTOR.asStack());     // PNP 三极管
                    })
                    .build());

    /**
     * 将物品栏注册器挂载到模组事件总线上。
     *
     * @param bus 模组事件总线
     */
    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
