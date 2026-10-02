package com.energeticspowergrid.content.transistor;

import com.george_vi.electroenergetics.simulation.electrical_properties.NortonCoupleNonlinearProperties;

/**
 * 三极管模型的一个元件（NPN 或 PNP），由共享的工作点状态驱动。
 * <p>
 * - 基射极（结）：分段二极管。低于导通电压时近似开路（1MΩ），高于导通电压时
 * 等效为 0.7V 电压源串联 10Ω 基极扩展电阻——使结电压被钳位在
 * {@code V_BE(on) + I_B · R_BE}，与真实二极管压降一致。
 * <p>
 * 集射极：受控电流源 {@code I_C = min(β · I_B, I_C max)}。负载电流超过 I_C
 * 后 C-E 电压被压到饱和阈值以下，元件切换为 R_sat（2Ω）电阻——自然进入饱和区；
 * 无基极电流时截止（1MΩ）。
 * <p>
 * <b>方向安全</b>：本类继承 {@link NortonCoupleNonlinearProperties}（CEE 二极管
 * 同款机制）。求解器以"语义方向"回调 {@code tick(v1, v2, first)}——v1/v2 始终
 * 对应注册时的 n1/n2 端子，与节点编号顺序无关。切勿改用
 * {@code MicroTickingElectricalProperties}：它的回调节点对顺序取决于求解时的
 * 节点编号分配（随方块放置顺序变化），方向敏感元件会在一半的放置方式下
 * 永久截止（采样到的 V_BE 变成 −12V）。
 * <p>
 * PNP：所有电压量取反（V_EB 代替 V_BE、V_EC 代替 V_CE），电流方向随之镜像；
 * 集射极的等效电导与电流源幅值两种极性完全相同。
 */
public class TransistorElectricalProperties extends NortonCoupleNonlinearProperties {

    /** 一个三极管的两个元件共享的工作点。 */
    public static class SharedState {
        /** 上一次迭代的 V_BE（带符号；NPN：V_B−V_E，PNP：V_E−V_B） */
        public double vbe;
        /** PNP 三极管为 true：结电压与电流方向按极性镜像 */
        public boolean pnp;
    }

    /** 基极扩展电阻 R_BE（导通后基极回路的等效内阻）。 */
    private static final double BASE_RESISTANCE = 10;
    /** 饱和电阻 R_sat：饱和区 C-E 等效串联电阻。 */
    private static final double SATURATION_RESISTANCE = 2;
    /** 截止电阻：截止/开路时的近似绝缘电阻。 */
    private static final double OFF_RESISTANCE = 1e6;

    /** true = 基射结元件；false = 集射输出元件。 */
    private final boolean junction;
    /** 与同管另一个元件共享的工作点。 */
    private final SharedState state;
    /** 电流放大倍数 β。 */
    private double beta = 100;
    /** 基射极导通电压 V_BE(on)。 */
    private double vbeOn = 0.7;
    /** 最大集电极电流 I_C max。 */
    private double icMax = 1;
    /** 上一微刻的端电压差，作为本次牛顿迭代的首个初值。 */
    private double vOld;

    public TransistorElectricalProperties(boolean junction, SharedState state) {
        this.junction = junction;
        this.state = state;
    }

    /** 每刻从设备同步三个可调参数。 */
    public void configure(double beta, double vbeOn, double icMax) {
        this.beta = beta;
        this.vbeOn = vbeOn;
        this.icMax = icMax;
    }

    /**
     * 每个牛顿迭代由求解器调用一次：v1/v2 是本元件两个端子的当前电压
     * （语义方向，NPN 时 v1 对应基极/集电极、v2 对应发射极），first 表示
     * 这是本微刻的首次迭代（此时以上一微刻的工作点为初值）。
     * 元件据此设置诺顿等效的电导与电流源。
     */
    @Override
    public void tick(double v1, double v2, boolean first) {
        boolean pnp = state.pnp;
        // 带符号的端电压差（PNP 取反，使导通判定/电流方向统一为 NPN 形式）
        double vd = pnp ? -(v1 - v2) : (v1 - v2);
        if (first)
            vd = vOld; // 首个牛顿迭代以上一微刻的工作点为初值
        vOld = vd;

        if (junction) {
            // 基射结：分段二极管
            state.vbe = vd;
            if (vd > vbeOn) {
                // 导通：0.7V 串联 10Ω 的戴维南 → 诺顿 0.07A 注入基极 ∥ 0.1S
                conductance = 1 / BASE_RESISTANCE;
                currentSource = (pnp ? -1 : 1) * vbeOn / BASE_RESISTANCE;
            } else {
                conductance = 1 / OFF_RESISTANCE;
                currentSource = 0;
            }
            return;
        }

        // 集射极：I_C = min(β · I_B, I_C max)
        double ib = state.vbe > vbeOn ? (state.vbe - vbeOn) / BASE_RESISTANCE : 0;
        double ic = Math.min(beta * ib, icMax);
        if (ic < 1e-9) {
            // 无基极电流：截止
            conductance = 1 / OFF_RESISTANCE;
            currentSource = 0;
            return;
        }
        if (vd >= ic * SATURATION_RESISTANCE) {
            // 放大区：恒流源 I_C（NPN 从集电极抽取电流，PNP 向集电极注入）；
            // 微量电导防止开路集电极导致矩阵奇异
            conductance = 1e-9;
            currentSource = (pnp ? 1 : -1) * ic;
        } else {
            // 饱和区（含 V_CE 为负的反向情形）：C-E 等效 R_sat 电阻。
            // 注意不能用 Math.abs(vd) 判定——当外部电路撑不起 I_C 时（例如灯丝
            // 热态电阻 × I_C 超过电源电压），V_CE 变负，若仍按放大区输出理想
            // 恒流源，就会反向灌流、凭空注入能量，在负载上打出远超电源的电压
            // （实测 12V 电池被打出 19V，灯具瞬间过压烧毁）。改用 R_sat 电阻后
            // 电流由外部电路决定：I = V_电源 / (R_负载 + R_sat)，能量自洽，
            // 且该分支在 vd = ic·R_sat 处与恒流源线连续衔接，牛顿迭代无振荡。
            conductance = 1 / SATURATION_RESISTANCE;
            currentSource = 0;
        }
    }
}
