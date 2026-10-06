package com.energeticspowergrid.content.mosfet;

import com.george_vi.electroenergetics.simulation.electrical_properties.NortonCoupleNonlinearProperties;

/**
 * MOS 管模型的一个元件（N 沟道或 P 沟道），由共享的工作点状态驱动。
 * <p>
 * <b>与三极管的本质区别</b>（为什么 MOS 管"电压驱动、适合低功耗"）：
 * <ul>
 * <li><b>电压驱动</b>：栅极与沟道之间是 SiO₂ 绝缘层——本模型用 1 GΩ 的
 *     栅源绝缘电阻表示（三极管的基极要持续抽取 I_B = I_C/β 的电流）。
 *     稳态下栅极电流只有纳安级，控制回路几乎不消耗功率，也不需要
 *     "基极电流够不够"的计算——只看电压 V_GS 是否越过阈值；</li>
 * <li><b>低导通损耗</b>：导通后 D-S 之间是纯电阻 R_DS(on)（默认 0.1Ω），
 *     而三极管饱和后有 ~2Ω 的等效饱和电阻——同样 10A 负载下 MOS 管只耗
 *     10W，三极管要耗 200W。大电流开关场景（高效电源、电机驱动）选 MOS 管；</li>
 * <li><b>体二极管</b>：D-S 之间反并联一个寄生二极管（N 沟道：S→D 方向
 *     导通），这是 MOS 管的固有结构，正向电流可以"倒着"从源极流到漏极。</li>
 * </ul>
 * <p>
 * <b>沟道模型</b>（以 N 沟道为基准，P 沟道电压取反跑同一套数学）：
 * <ul>
 * <li>V_GS ≥ V_GS(th)：沟道导通。欧姆区 I = V_DS / R_DS(on)；当电流达到
 *     I_D max 后转入恒流限流分支（与欧姆分支在 I_D max·R_DS(on) 处连续），
 *     防止大电压源打出无限电流；</li>
 * <li>V_GS &lt; V_GS(th)：沟道截止（1MΩ）——体二极管仍然可能正向导通。</li>
 * </ul>
 * <p>
 * <b>方向安全</b>：与三极管相同，继承 {@link NortonCoupleNonlinearProperties}
 * （CEE 二极管同款机制），求解器保证以语义方向回调 tick——切勿改用
 * MicroTicking 机制（节点对顺序随放置顺序翻转，方向敏感元件会坏）。
 */
public class MosfetElectricalProperties extends NortonCoupleNonlinearProperties {

    /** 一个 MOS 管的两个元件共享的工作点。 */
    public static class SharedState {
        /** 上一次迭代的栅源电压 V_GS（原始符号：V_G−V_S，沟道侧按极性取用） */
        public double vgs;
        /** P 沟道 MOS 管为 true：沟道电压与电流方向按极性镜像 */
        public boolean pChannel;
    }

    /**
     * 栅源绝缘电阻：SiO₂ 氧化层的等效电阻。1GΩ 下 12V 驱动只产生 12nA
     * 漏电流——这就是"电压驱动、静态零功耗"的模型表达。它同时给栅极节点
     * 提供了一条到源极的微弱通路，保证求解矩阵非奇异。
     */
    private static final double GATE_INSULATION = 1e9;
    /** 体二极管正向压降（硅 PN 结典型值）。 */
    private static final double BODY_DIODE_VF = 0.7;
    /** 体二极管导通后的动态电阻（低损耗，可传导大电流）。 */
    private static final double BODY_DIODE_R = 0.05;
    /** 截止电阻：沟道关断时的近似绝缘电阻。 */
    private static final double OFF_RESISTANCE = 1e6;
    /** 限流分支的并联电导：1e-9 S，防止开路时矩阵奇异。 */
    private static final double CLAMP_CONDUCTANCE = 1e-9;

    /** true = 栅源绝缘元件；false = 漏源沟道元件。 */
    private final boolean gate;
    /** 与同管另一个元件共享的工作点。 */
    private final SharedState state;
    /** 阈值电压 V_GS(th)：V_GS 达到它沟道才导通。 */
    private double vth = 2;
    /** 导通电阻 R_DS(on)：沟道导通后的欧姆电阻。 */
    private double rdsOn = 0.1;
    /** 最大漏极电流 I_D max：限流分支的目标电流。 */
    private double idMax = 10;
    /** 上一微刻的端电压差，作为本次牛顿迭代的首个初值。 */
    private double vOld;

    public MosfetElectricalProperties(boolean gate, SharedState state) {
        this.gate = gate;
        this.state = state;
    }

    /** 每刻从设备同步三个可调参数。 */
    public void configure(double vth, double rdsOn, double idMax) {
        this.vth = vth;
        this.rdsOn = rdsOn;
        this.idMax = idMax;
    }

    /**
     * 截止态的等效电导：1/1MΩ。覆写 gMin 交给基类处理反偏漏电导，
     * 这里沟道截止直接用固定电阻表示。
     */
    @Override
    public double gMin() {
        return 1 / OFF_RESISTANCE;
    }

    /**
     * 每个牛顿迭代由求解器调用一次：v1/v2 是本元件两个端子的当前电压
     * （语义方向：栅元件 v1=栅极、v2=源极；沟道元件 v1=漏极、v2=源极），
     * first 表示这是本微刻的首次迭代。
     */
    @Override
    public void tick(double v1, double v2, boolean first) {
        if (gate) {
            // 栅极：纯绝缘电阻，记录 V_GS 供沟道元件读取
            double vgs = v1 - v2;
            if (first)
                vgs = vOld;
            else
                vOld = vgs;
            state.vgs = vgs;
            conductance = 1 / GATE_INSULATION;
            currentSource = 0;
            return;
        }

        // 沟道元件：电压取反跑 N 沟道数学（P 沟道镜像，同 PNP 的处理方式）
        boolean p = state.pChannel;
        double vgs = p ? -state.vgs : state.vgs;
        double vd = p ? -(v1 - v2) : (v1 - v2);
        if (first) {
            vd = vOld;
        } else {
            vOld = vd;
        }

        double g = 1 / OFF_RESISTANCE;
        double j = 0;
        if (vgs >= vth) {
            // 沟道导通：欧姆区电阻 R_DS(on)，带 I_D max 限流
            if (vd >= idMax * rdsOn) {
                // 恒流限流分支：I ≈ I_D max，与欧姆分支在 idMax·rdsOn 处连续
                g = CLAMP_CONDUCTANCE;
                j = -idMax;
            } else {
                // 欧姆区（含反向：V_DS<0 时沟道照样双向导通，同步整流行为）
                g = 1 / rdsOn;
                j = 0;
            }
        }
        // 体二极管（N 沟道：阳极=S 阴极=D）：V_DS 低于 -V_F 后正向导通，
        // 与沟道状态无关——截止态下反向电流也能从这里走
        if (vd < -BODY_DIODE_VF) {
            g += 1 / BODY_DIODE_R;
            // 二极管诺顿形式：I = g·(vd + V_F) = g·vd − (−g·V_F)
            j += -BODY_DIODE_VF / BODY_DIODE_R;
        }
        // 诺顿等效：I(n1→n2) = g·vd − j；P 沟道电流方向镜像（电导不变）
        conductance = g;
        currentSource = p ? -j : j;
    }
}
