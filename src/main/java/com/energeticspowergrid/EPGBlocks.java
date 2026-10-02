package com.energeticspowergrid;

import com.energeticspowergrid.content.excitation.ExcitationStatorBlock;
import com.energeticspowergrid.content.factorylight.FactoryLightBlock;
import com.energeticspowergrid.content.factorylight.FactoryLightLightBlock;
// import com.energeticspowergrid.content.fan.ElectricFanBlock;
import com.energeticspowergrid.content.fixture.LightFixtureBlock;
import com.energeticspowergrid.content.heater.HeatingCoilBlock;
import com.energeticspowergrid.content.reversing_switch.ReversingSwitchBlock;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.client.ElectricStatsTooltipModifier;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import static com.energeticspowergrid.EnergeticsPowerGrid.REGISTRATE;
import static com.simibubi.create.foundation.data.CreateRegistrate.casingConnectivity;
import static com.simibubi.create.foundation.data.CreateRegistrate.connectedTextures;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

/**
 * 本模组的方块注册类。
 * <p>
 * 使用 Registrate 链式声明各个方块：方块属性、光照等级、方块状态模型、
 * 物品模型以及电气属性（电阻/最大功率）提示信息。
 */
public class EPGBlocks {
    static {
        // 取消默认创造物品栏，所有方块改由自定义物品栏（EPGCreativeTab）收纳
        REGISTRATE.defaultCreativeTab((net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab>) null);
    }

    /** 灯具固定座：墙面/地面安装的灯具底座，随输入功率分两档发光（10/15 级光照），电阻 1Ω */
    public static final BlockEntry<LightFixtureBlock> LIGHT_FIXTURE = REGISTRATE.block("light_fixture", LightFixtureBlock::new)
            .tag(CEETags.LIGHT, EPGTags.SABLE_LIGHT)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion()
                    .lightLevel(s -> switch (s.getValue(LightFixtureBlock.POWER)) {
                        case 1 -> 10;
                        case 2 -> 15;
                        default -> 0;
                    }))
            // 依据朝向（FACING）与翻转（ROLL）状态选择水平/垂直模型并施加旋转
            .blockstate((c, p) -> p.getVariantBuilder(c.getEntry()).forAllStates(state -> {
                Direction facing = state.getValue(LightFixtureBlock.FACING);
                boolean roll = state.getValue(DirectionalRolledDeviceBlock.ROLL);
                boolean vertical = facing.getAxis().isVertical();
                int x = 0, y = 0;
                switch (facing) {
                    case UP -> {
                    }
                    case DOWN -> x = 180;
                    case EAST -> y = 180;
                    case NORTH -> y = 90;
                    case SOUTH -> y = -90;
                    default -> {
                    }
                }
                if (roll && vertical)
                    y = 90;
                else if (roll && !vertical)
                    x = -90;
                String suffix = vertical ? "_v" : "_h";
                return ConfiguredModel.builder()
                        .modelFile(p.models().getExistingFile(p.modLoc("block/fixtures/light_fixture" + suffix)))
                        .rotationX(x)
                        .rotationY(y)
                        .build();
            }))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/fixtures/light_fixture_v")))
            // 物品提示中标注电阻 1Ω
            .onRegister(i -> ElectricStatsTooltipModifier.ALL_ENTRIES.register(i, new ElectricStatsTooltipModifier.ElectricStatSet()
                    .addResistance(() -> 1)))
            .build()
            .register();

    /** 工厂灯：大型工业投光灯，随功率分两档发光（10/15 级光照），可向远处投射光块 */
    public static final BlockEntry<FactoryLightBlock> FACTORY_LIGHT = REGISTRATE.block("factory_light", FactoryLightBlock::new)
            .tag(CEETags.LIGHT, EPGTags.SABLE_LIGHT)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion()
                    .lightLevel(s -> switch (s.getValue(FactoryLightBlock.POWER)) {
                        case 2 -> 10;
                        case 3 -> 15;
                        default -> 0;
                    }))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/factory_light/factorylight")))
            .build()
            .register();

    /** 工厂灯投射光块：工厂灯向远处投射产生的虚拟光源方块，不可获取，仅提供光照（10/15 级） */
    public static final BlockEntry<FactoryLightLightBlock> FACTORY_LIGHT_LIGHT = REGISTRATE.block("factory_light_light", FactoryLightLightBlock::new)
            .properties(p -> p.lightLevel(s -> s.getValue(FactoryLightLightBlock.POWER) == 1 ? 15 : 10))
            .register();

    /** 导电外壳：金属装饰外壳方块，支持 Create 的外壳连接纹理（相邻自动拼接） */
    public static final BlockEntry<CasingBlock> CONDUCTIVE_CASING = REGISTRATE.block("conductive_casing", CasingBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.METAL))
            .onRegister(connectedTextures(() -> new EncasedCTBehaviour(EPGPartialModels.CONDUCTIVE_CASING)))
            .onRegister(casingConnectivity((block, cc) -> cc.makeCasing(block, EPGPartialModels.CONDUCTIVE_CASING)))
            .transform(pickaxeOnly())
            .simpleItem()
            .register();

    // Disabled: duplicates electroenergetics:electric_fan
    // 电动鼓风机已禁用：与电力学原版的电动风扇（electroenergetics:electric_fan）功能重复
    // public static final BlockEntry<ElectricFanBlock> ELECTRIC_FAN = REGISTRATE.block("electric_fan", ElectricFanBlock::new)
    //         .initialProperties(SharedProperties::softMetal)
    //         .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion())
    //         .transform(pickaxeOnly())
    //         .item()
    //         .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/electric_fan/item")))
    //         .onRegister(i -> ElectricStatsTooltipModifier.ALL_ENTRIES.register(i, new ElectricStatsTooltipModifier.ElectricStatSet()
    //                 .addResistance(() -> com.energeticspowergrid.config.EPGConfigs.server().electricFanResistance.getF())))
    //         .build()
    //         .register();

    /** 加热线圈：通电发热的电阻线圈，可为上方物品加热（类似 Create 熔炉鼓风），电阻与最大功率可由配置调整 */
    public static final BlockEntry<HeatingCoilBlock> HEATING_COIL = REGISTRATE.block("heating_coil", HeatingCoilBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(AllTags.AllBlockTags.FAN_TRANSPARENT.tag)
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/heating_coil")))
            // 物品提示中标注电阻与最大功率（读取服务端配置）
            .onRegister(i -> ElectricStatsTooltipModifier.ALL_ENTRIES.register(i, new ElectricStatsTooltipModifier.ElectricStatSet()
                    .addResistance(() -> com.energeticspowergrid.config.EPGConfigs.server().heatingCoilResistance.getF())
                    .addMaxPower(() -> com.energeticspowergrid.config.EPGConfigs.server().heatingCoilMaxPower.getF())))
            .build()
            .register();

    /** 换向开关：双掷开关，可切换电流方向（正/反向），用于电动机反转等场景，模型复用电力学双开关 */
    public static final BlockEntry<ReversingSwitchBlock> REVERSING_SWITCH = REGISTRATE.block("reversing_switch", ReversingSwitchBlock::new)
            .tag(CEETags.LIGHT)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).noOcclusion())
            // 依据闭合状态选用电力学的双开关开/闭模型
            .blockstate((c, p) -> DirectionalRolledDeviceBlock.generateBlockState(c, p,
                    bs -> ResourceLocation.fromNamespaceAndPath("electroenergetics",
                            bs.getValue(com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock.CLOSED)
                                    ? "block/double_switch/block_closed"
                                    : "block/double_switch/block")))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(),
                    ResourceLocation.fromNamespaceAndPath("electroenergetics", "block/double_switch/block")))
            .build()
            .register();

    /** NPN 三极管：半导体放大/开关元件，基极小电流控制集电极-发射极大电流，灰色外观 */
    public static final BlockEntry<com.energeticspowergrid.content.transistor.TransistorBlock> NPN_TRANSISTOR = REGISTRATE.block("npn_transistor", com.energeticspowergrid.content.transistor.TransistorBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/transistor")))
            .build()
            .register();

    /** PNP 三极管：与 NPN 极性相反的三极管，橙色外观，逻辑上由 pnp 标志区分 */
    public static final BlockEntry<com.energeticspowergrid.content.transistor.PnpTransistorBlock> PNP_TRANSISTOR = REGISTRATE.block("pnp_transistor", com.energeticspowergrid.content.transistor.PnpTransistorBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/transistor")))
            .build()
            .register();

    /** 励磁定子：为旋转转子提供励磁场强的定子绕组，与电力学发电机/电动机配合构建励磁系统 */
    public static final BlockEntry<ExcitationStatorBlock> EXCITATION_STATOR = REGISTRATE.block("excitation_stator", ExcitationStatorBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/excitation_stator/block")))
            .build()
            .register();

    /** 逆变器：将直流电逆变为交流电输出（方波），直通模式下也可反向导通，模型复用电力学双开关 */
    public static final BlockEntry<com.energeticspowergrid.content.inverter.InverterBlock> INVERTER = REGISTRATE.block("inverter", com.energeticspowergrid.content.inverter.InverterBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).noOcclusion())
            // 依据闭合状态选用电力学的双开关开/闭模型
            .blockstate((c, p) -> DirectionalRolledDeviceBlock.generateBlockState(c, p,
                    bs -> ResourceLocation.fromNamespaceAndPath("electroenergetics",
                            bs.getValue(com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock.CLOSED)
                                    ? "block/double_switch/block_closed"
                                    : "block/double_switch/block")))
            .transform(pickaxeOnly())
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(),
                    ResourceLocation.fromNamespaceAndPath("electroenergetics", "block/double_switch/block")))
            .build()
            .register();

    /**
     * 注册入口（占位方法）。
     * Registrate 的方块注册在类加载静态字段初始化时已完成，
     * 主类调用本方法仅为确保本类被加载。
     */
    public static void register() {
    }
}
