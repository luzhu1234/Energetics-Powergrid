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
 *     "基极电流够不够"的计算——只看电压 V_GS；</li>
 * <li><b>低导通损耗</b>：强驱动下沟道是纯电阻 R_DS(on)（默认 0.1Ω），
 *     而三极管饱和后有 ~2Ω 的等效饱和电阻——同样 10A 负载下 MOS 管只耗
 *     几瓦，三极管要耗上百瓦。大电流开关场景首选；</li>
 * <li><b>体二极管</b>：D-S 之间反并联一个寄生二极管（N 沟道：S→D 方向
 *     导通），这是 MOS 管的固有结构。</li>
 * </ul>
 * <p>
 * <b>沟道模型</b>（以 N 沟道为基准，P 沟道电压取反跑同一套数学）——
 * 真实 MOSFET 的三个工作区，兼顾模拟放大与数字开关两种用途：
 * <ul>
 * <li><b>截止区</b>（V_GS ≤ V_GS(th)）：沟道关断（1MΩ），体二极管仍可能
 *     正向导通；</li>
 * <li><b>饱和区/放大区</b>（V_GS &gt; V_th 且 V_DS ≥ 过驱动电压 Vov）：
 *     平方律恒流 {@code I_D = k·Vov²/2}——电流只随栅压增长、与 V_DS 基本
 *     无关，这就是<b>模拟放大</b>的工作区：把栅极偏置在阈值上方，小信号
 *     调制 Vov 即得到跨导 {@code gm = k·Vov} 的受控电流；</li>
 * <li><b>线性区/欧姆区</b>（V_GS &gt; V_th 且 V_DS &lt; Vov）：
 *     {@code I_D = k·(Vov·V_DS − V_DS²/2)}——小 V_DS 下退化为电阻
 *     {@code R = 1/(k·Vov)}，<b>数字开关</b>的工作区：强驱动（大 Vov）
 *     时电阻逼近 R_DS(on)，满幅导通。</li>
 * </ul>
 * 跨导系数 k 由可调参数 R_DS(on) 校准：{@code k = 1/(R_DS(on)·10)}，即
 * 过驱动电压为 10V（如 12V 电源驱动 2V 阈值管）时沟道电阻恰为 R_DS(on)。
 * 数字用途下强驱动进入欧姆区、损耗即 R_DS(on) 规格；模拟用途下弱驱动
 * 进入饱和区、跨导随偏置可调。I_D max 只在饱和区/放大区限流——欧姆区
 * 的开关电流不受它影响，数字满幅导通不受损。
 * <p>
 * <b>方向安全</b>：与三极管相同，继承 {@link NortonCoupleNonlinearProperties}
 * （CEE 二极管同款机制），求解器保证以语义方向回调 tick——切勿改用
 * MicroTicking 机制（节点对顺序随放置顺序翻转，方向敏感元件会坏）。
 * 全模型为多项式，无指数/对数运算，牛顿迭代天然稳定。
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
    /** 饱和区/限流.stamp 的电导下限：防止开路漏极导致矩阵奇异。 */
    private static final double LEAKAGE = 1e-9;
    /**
     * R_DS(on) 的校准过驱动电压：k = 1/(R_DS(on)·VOV_REF)，即过驱动
     * V_GS−V_th = 10V（典型 12V 驱动 − 2V 阈值）时欧姆区电阻恰为 R_DS(on)。
     */
    private static final double VOV_REF = 10;

    /** true = 栅源绝缘元件；false = 漏源沟道元件。 */
    private final boolean gate;
    /** 与同管另一个元件共享的工作点。 */
    private final SharedState state;
    /** 阈值电压 V_GS(th)：V_GS 达到它沟道才开始导通。 */
    private double vth = 2;
    /** 导通电阻 R_DS(on)：过驱动 10V 时欧姆区电阻（校准 k 用）。 */
    private double rdsOn = 0.1;
    /** 最大漏极电流 I_D max：饱和区/放大区的限流值。 */
    private double idMax = 10;
    /** 跨导系数 k = 1/(R_DS(on)·VOV_REF)，由 configure 时更新。 */
    private double k;
    /** 上一微刻的端电压差，作为本次牛顿迭代的首个初值。 */
    private double vOld;

    public MosfetElectricalProperties(boolean gate, SharedState state) {
        this.gate = gate;
        this.state = state;
    }

    /** 每刻从设备同步三个可调参数，并重推跨导系数 k。 */
    public void configure(double vth, double rdsOn, double idMax) {
        this.vth = vth;
        this.rdsOn = rdsOn;
        this.idMax = idMax;
        this.k = 1 / (rdsOn * VOV_REF);
    }

    /** 截止区电导：1/1MΩ（沟道关断）。 */
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

        double vov = vgs - vth;   // 过驱动电压 V_GS − V_GS(th)
        double g, i;
        if (vov <= 0) {
            // 截止区：沟道关断
            g = 1 / OFF_RESISTANCE;
            i = 0;
        } else {
            if (vd < vov) {
                // 线性区/欧姆区：I = k(Vov·V_DS − V_DS²/2)，
                // 小 V_DS 时 R ≈ 1/(k·Vov) → 强驱动下逼近 R_DS(on)（数字开关工作区）。
                // V_DS 为负时公式自然给出反向电流（沟道双向导通，同步整流行为）。
                // 不做 I_D max 限流——数字满幅导通的电流不受影响。
                g = Math.max(k * (vov - vd), LEAKAGE);
                i = k * (vov * vd - vd * vd / 2);
            } else {
                // 饱和区/放大区：I = k·Vov²/2，只随栅压平方增长（模拟放大工作区）。
                // I_D max 限流只在此区生效，保护"部分导通"状态下的过大电流
                // （例如无负载硬驱动的短路工况）。
                g = LEAKAGE;
                i = 0.5 * k * vov * vov;
                if (i > idMax) {
                    i = idMax;
                }
            }
        }
        // 体二极管（N 沟道：阳极=S 阴极=D）：V_DS 低于 −V_F 后正向导通，
        // 与沟道状态无关——截止态下反向电流也能从这里走
        if (vd < -BODY_DIODE_VF) {
            g += 1 / BODY_DIODE_R;
            i += (vd + BODY_DIODE_VF) / BODY_DIODE_R;
        }
        // 诺顿等效：I(n1→n2) = g·vd − j → j = g·vd − I；
        // P 沟道电流方向镜像（电导不变）
        conductance = g;
        currentSource = p ? -(g * vd - i) : (g * vd - i);
    }
}
