package com.energeticspowergrid.config;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Supplier;

/**
 * 本模组的配置管理类。
 * <p>
 * 基于 Create 的 ConfigBase 体系注册服务端配置（CServer），
 * 各配置项的数值会直接影响电气模拟的核心参数。
 */
public class EPGConfigs {
    /** 服务端配置实例（单例），通过 {@link #server()} 访问 */
    private static CServer server;

    /**
     * 获取服务端配置实例。
     *
     * @return 服务端配置对象，包含所有电气参数
     */
    public static CServer server() {
        return server;
    }

    /**
     * 通用配置构建方法：创建配置对象并将其字段注册进 ModConfigSpec。
     *
     * @param factory 配置对象的构造工厂
     * @param <T> 配置类型
     * @return 已绑定规格的配置对象
     */
    private static <T extends ConfigBase> T register(Supplier<T> factory) {
        Pair<T, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(builder -> {
            T config = factory.get();
            config.registerAll(builder);
            return config;
        });
        T config = specPair.getLeft();
        config.specification = specPair.getRight();
        return config;
    }

    /**
     * 注册服务端配置文件，由主类在模组构造时调用。
     *
     * @param container 模组容器，用于挂载 SERVER 类型的配置
     */
    public static void register(ModContainer container) {
        server = register(CServer::new);
        container.registerConfig(ModConfig.Type.SERVER, server.specification);
    }

    /**
     * 服务端配置项集合。
     * <p>
     * 包含生长灯、工厂灯、加热线圈、换向开关、励磁定子、
     * 逆变器以及电网频率模拟等参数。
     */
    public static class CServer extends ConfigBase {
        /** 生长灯作用半径：默认 2 格（范围 1~8）。调大后生长灯催熟作物的覆盖范围更大，但每刻遍历的方块更多，可能增加卡顿 */
        public final ConfigInt growthLampRadius = i(2, 1, 8, "growthLampRadius",
                "Radius of the area affected by the growth lamp");
        /** 生长灯催熟概率分母：默认 50（范围 0~400）。数值越小作物被随机刻的频率越高；设为 1 时每刻催熟范围内全部方块 */
        public final ConfigInt growthLampChance = i(50, 0, 400, "growthLampChance",
                "Chance denominator for ticking a crop (lower = more frequent). 1 ticks every block every tick.");
        /** 元件损耗开关：默认开启。开启后灯泡过载会烧毁（切换为破损模型并不再发光），关闭则灯泡永不烧毁 */
        public final ConfigBool componentDamage = b(true, "componentDamage",
                "Light bulbs burn out when overloaded");
        /** 工厂灯投射距离：默认 16 格（范围 0~32）。工厂灯沿朝向投射的光块最远距离，调大可照亮更远区域 */
        public final ConfigInt factoryLightProjectionRange = i(16, 0, 32, "factoryLightProjectionRange",
                "Maximum block range of the factory light projected light blocks");
        // Disabled: electric blower duplicates CEE electric fan
        // 电动鼓风机配置已禁用：与电力学原版的电动风扇功能重复
        // public final ConfigFloat electricFanResistance = f(25, 0.1f, 10000, "electricFanResistance",
        //         "Series resistance of the electric blower");
        // public final ConfigFloat electricFanCurrentToSpeed = f(64, 1, 1024, "electricFanCurrentToSpeed",
        //         "Kinetic speed = current * this value, clamped to +/- 256");
        /** 加热线圈串联电阻：默认 25Ω（范围 0.1~10000）。决定线圈在给定电压下的发热功率（P=U²/R），调小发热更快 */
        public final ConfigFloat heatingCoilResistance = f(25, 0.1f, 10000, "heatingCoilResistance",
                "Series resistance of the heating coil");
        /** 加热线圈维持功率：默认 1500W（范围 1~100000）。使线圈温度维持在 600°C 所需的功率，低于该值线圈降温、高于则升温 */
        public final ConfigFloat heatingCoilMaxPower = f(1500, 1, 100000, "heatingCoilMaxPower",
                "Power in watts that holds the heating coil at 600 C");
        /** 加热线圈热质量：默认 1（范围 0.01~100）。数值越大线圈升降温越慢（惯性越大），调小则温度响应更灵敏 */
        public final ConfigFloat heatingCoilMass = f(1, 0.01f, 100, "heatingCoilMass",
                "Thermal mass of the heating coil");
        /** 换向开关接触电阻：默认 0.001Ω（范围 0.0001~10）。开关闭合时的等效串联电阻，通常保持极小以减少压降损耗 */
        public final ConfigFloat reversingSwitchResistance = f(0.001f, 0.0001f, 10, "reversingSwitchResistance",
                "Contact resistance of the reversing switch");
        /** 励磁定子绕组内阻：默认 100Ω（范围 0.1~100000）。定子线圈的内部电阻，决定同样电压下的励磁电流大小 */
        public final ConfigFloat excitationStatorResistance = f(100, 0.1f, 100000, "excitationStatorResistance",
                "Internal resistance of the excitation stator winding");
        /** 励磁场强系数：默认 1（范围 0~1000）。B = I * U * y 中的系数 y，每个定子按励磁电流与电压之积缩放磁场强度 */
        public final ConfigFloat excitationStatorFieldFactor = f(1, 0, 1000, "excitationStatorFieldFactor",
                "The y in B = I * U * y, scaling excitation strength per stator");
        /** 单定子等效力场：默认 1000（范围 1~100000）。相当于一个原版（电力学）定子对转子拉力的励磁强度值，用于两套系统换算 */
        public final ConfigFloat excitationFieldPerStator = f(1000, 1, 100000, "excitationFieldPerStator",
                "Excitation strength that matches one vanilla stator's pull on a rotor");
        /** 原版定子固有力场：默认 100（范围 0~10000）。每个原版定子固定贡献给转子的励磁强度，使其纳入励磁体系（100 = 十分之一个等效定子） */
        public final ConfigFloat vanillaStatorField = f(100, 0, 10000, "vanillaStatorField",
                "Fixed excitation strength a vanilla stator contributes to a rotor, making it part of the excitation system (100 = one tenth of a stator equivalent)");
        /** 转速-频率换算：默认 6.4（范围 0.01~100）。每 1 RPM 对应的交流频率（Hz），与电力学自身的换算值保持一致 */
        public final ConfigFloat hertzPerRpm = f(6.4f, 0.01f, 100, "hertzPerRpm",
                "Rotational speed to AC frequency, matching electroenergetics' own value");
        /** 频率跌落上限：默认 0.08Hz（范围 0~5）。负载突变引起的频率跌落/抬升的最大幅度，防止电网频率剧烈波动 */
        public final ConfigFloat frequencyDipMaxHz = f(0.08f, 0, 5, "frequencyDipMaxHz",
                "Upper bound on the frequency dip or lift caused by a load change");
        /** 频率跌落满量程负载：默认 100kW（范围 1~100000）。负载阶跃达到该千瓦数时频率偏移恰好打满 frequencyDipMaxHz；小于该值 5% 的变化视为噪声、频率完全不波动，之间按线性比例——偏移量与负载变化量线性成正比 */
        public final ConfigFloat frequencyDipFullScaleKilowatt = f(100, 1f, 100000, "frequencyDipFullScaleKilowatt",
                "Load step in kilowatts at which the frequency dip reaches frequencyDipMaxHz; changes below 5% of this value cause no frequency shift, between they scale linearly");
        /** 频率跌落恢复时间：默认 100 刻（范围 1~600）。频率跌落恢复到可用频率所需的刻数（100 刻 = 5 秒），越大恢复越慢 */
        public final ConfigInt frequencyDipDecayTicks = i(100, 1, 600, "frequencyDipDecayTicks",
                "Ticks over which a frequency dip decays back to the attainable frequency, 100 = 5 seconds");

        /**
         * 配置名称，用于配置文件分节标识。
         */
        @Override
        public String getName() {
            return "server";
        }
    }
}
