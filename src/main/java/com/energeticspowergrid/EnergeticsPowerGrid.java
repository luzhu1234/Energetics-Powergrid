package com.energeticspowergrid;

import com.energeticspowergrid.client.EPGClient;
import com.energeticspowergrid.config.EPGConfigs;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Energetics Power Grid（电力学电网）模组主入口类。
 * <p>
 * 本模组是电力学（Electro Energetics）与机械动力（Create）的附属模组，
 * 提供灯具、加热线圈、换向开关、励磁定子、逆变器、NPN/PNP 三极管等电气设备，
 * 在电力学的电路模拟框架上扩展真实的电气元件行为。
 * <p>
 * 构造函数中依次完成注册框架初始化与各类内容的注册。
 */
@Mod(EnergeticsPowerGrid.ID)
public class EnergeticsPowerGrid {
    /** 模组 ID，用于命名空间、注册与资源定位 */
    public static final String ID = "energeticspowergrid";
    /** 模组共享日志器 */
    public static final Logger LOGGER = LogUtils.getLogger();
    /** 基于 Create 扩展的 Registrate 注册框架实例，统一管理本模组的方块/物品/方块实体等注册 */
    public static CreateRegistrate REGISTRATE;

    /**
     * 模组构造函数，由 NeoForge 在模组加载阶段调用。
     *
     * @param modEventBus 模组事件总线，用于注册各类监听器
     * @param modContainer 模组容器，用于注册配置文件
     */
    public EnergeticsPowerGrid(IEventBus modEventBus, ModContainer modContainer) {
        // 创建注册框架，并为物品提示框追加电力学电气属性（电压/功率/电阻）说明
        REGISTRATE = CreateRegistrate.create(ID)
                .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                        .andThen(TooltipModifier.mapNull(new com.george_vi.electroenergetics.client.ElectricStatsTooltipModifier(item))));
        REGISTRATE.registerEventListeners(modEventBus);

        EPGItems.register();            // 注册物品（灯具类）
        EPGBlocks.register();           // 注册方块（灯具、线圈、开关、定子、逆变器、三极管等）
        EPGPartialModels.register();    // 注册部分模型（渲染用）
        EPGBlockEntityTypes.register(); // 注册方块实体类型
        EPGSimulatedDevices.register(modEventBus);  // 注册电力学模拟设备类型
        EPGFanProcessingTypes.register(modEventBus); // 注册 Create 风扇处理类型（加热）
        EPGCreativeTab.register(modEventBus);        // 注册创造模式物品栏
        EPGConfigs.register(modContainer);           // 注册服务端配置文件
        EPGDisplaySources.register();                // 注册显示连接器数据源（频率表/同步钟）

        // 公共初始化阶段把数据源补挂到电力学的频率表/同步钟方块实体类型上
        modEventBus.addListener(EPGDisplaySources::attachToCEEBlockEntities);

        // 仅在客户端环境中初始化客户端渲染相关内容
        if (FMLEnvironment.dist == Dist.CLIENT)
            EPGClient.init();
    }

    /**
     * 以本模组命名空间构造资源定位符。
     *
     * @param path 资源路径
     * @return energeticspowergrid:path 形式的 ResourceLocation
     */
    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}
