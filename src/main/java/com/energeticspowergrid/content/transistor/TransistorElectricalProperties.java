package com.energeticspowergrid.content.transistor;

import com.george_vi.electroenergetics.simulation.electrical_properties.NortonCoupleNonlinearProperties;

/**
 * 三极管模型的一个元件（NPN 或 PNP），由共享的工作点状态驱动。
 * <p>
 * <b>指数模型</b>：基射结采用真实 PN 结的指数 I-V 曲线
 * {@code I = Is·(exp(V/V_T) − 1)}，并串联基极扩展电阻 R_BE（10Ω）——
 * 串联电阻使基极电流在大驱动下仍有下限约束（不会像裸指数结那样被强电压源
 * 打出天文数字电流），小信号下则给出真实的软拐点与跨导。串联电阻与指数的
 * 组合方程 {@code V = V_T·ln(1 + I/Is) + R·I} 没有初等闭式解，采用
 * Banwell &amp; Jayakumar (2000) 的 Wright Omega 函数闭式求解（见
 * {@link #wrightOmega4}），每次求值只需一次 ln + 一次 exp，无溢出风险。
 * <p>
 * 集射极：受控电流源 {@code I_C = min(β · I_B, I_C max)}。负载电流超过 I_C
 * 后 C-E 电压被压到饱和阈值以下，元件切换为 R_sat（2Ω）电阻——自然进入饱和区；
 * 无基极电流时截止（1MΩ）。
 * <p>
 * <b>数值稳定性</b>：牛顿迭代间使用 {@link #pnLim} 对结电压做对数阻尼
 * （SPICE 的标准做法），防止指数在大步长下发散；Wright Omega 的闭式解本身
 * 对任意输入电压都有限，因此整条链路不会产生 NaN/Infinity。
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

    /** 室温热电压 V_T = kT/q ≈ 25.85mV，取 25mV 整数值（模型无温度模拟）。 */
    private static final double V_T = 0.025;
    /**
     * 基极扩展电阻 R_BE：串联在指数结上的体电阻。它同时是旧分段模型的
     * 基极回路电阻——大驱动下基极电流仍由它限定（I_B ≈ V_drive/R_BE）。
     */
    private static final double BASE_RESISTANCE = 10;
    /**
     * 拐点参考电流：定义 V_BE(on) 为"基极电流达到该值时的结电压"。
     * 取 2.5mA 使该工作点的动态电阻 V_T/I 恰为 10Ω，与串联电阻的过渡平滑衔接。
     */
    private static final double I_REF = 2.5e-3;
    /** 饱和电阻 R_sat：饱和区 C-E 等效串联电阻。 */
    private static final double SATURATION_RESISTANCE = 2;
    /** 截止电阻：截止/开路时的近似绝缘电阻。 */
    private static final double OFF_RESISTANCE = 1e6;
    /** 结漏电导下限：反偏时电导趋零，给求解矩阵对角一个极小值防奇异（CEE 二极管同款）。 */
    private static final double LEAKAGE = 1e-12;

    /** true = 基射结元件；false = 集射输出元件。 */
    private final boolean junction;
    /** 与同管另一个元件共享的工作点。 */
    private final SharedState state;
    /** 电流放大倍数 β。 */
    private double beta = 100;
    /** 基射极导通电压 V_BE(on)：基极电流达到 {@link #I_REF} 时的结电压。 */
    private double vbeOn = 0.7;
    /** 最大集电极电流 I_C max。 */
    private double icMax = 1;
    /** 反向饱和电流 Is，由 vbeOn 与 I_REF 推导（configure 时更新）。 */
    private double saturationCurrent;
    /** 临界电压 vCrit = V_T·ln(V_T/(Is·√2))，pnLim 阻尼的切换点。 */
    private double vCrit;
    /** 上一微刻的端电压差，作为本次牛顿迭代的首个初值。 */
    private double vOld;

    public TransistorElectricalProperties(boolean junction, SharedState state) {
        this.junction = junction;
        this.state = state;
    }

    /** 每刻从设备同步三个可调参数，并重推指数模型的 Is 与 vCrit。 */
    public void configure(double beta, double vbeOn, double icMax) {
        this.beta = beta;
        this.vbeOn = vbeOn;
        this.icMax = icMax;
        // Is = I_REF / exp(V_BE(on)/V_T)：使结电流在 V_BE(on) 处恰为 I_REF
        this.saturationCurrent = I_REF / Math.exp(vbeOn / V_T);
        this.vCrit = V_T * Math.log(V_T / (saturationCurrent * Math.sqrt(2)));
    }

    /** 结漏电导：反偏时电导趋零，给对角一个极小值防奇异。 */
    @Override
    public double gMin() {
        return LEAKAGE;
    }

    /**
     * Wright Omega 函数（D'Angelo, Gabrielli &amp; Turchetti 2019 近式）。
     * 满足 {@code W + ln W = z}，用于指数结 + 串联电阻方程的闭式求解。
     */
    private static double wrightOmega(double z) {
        double x1 = -3.341459552768620;
        double x2 = 8;
        double alpha = -1.314293149877800e-3;
        double beta = 4.775931364975583e-2;
        double gamma = 3.631952663804445e-1;
        double zeta = 6.313183464296682e-1;
        if (z <= x1)
            return 0;
        if (z < x2)
            return alpha * z * z * z + beta * z * z + gamma * z + zeta;
        return z - Math.log(z);
    }

    /** Wright Omega 的四阶精化：一次牛顿校正，把近似误差压到 1e-12 量级。 */
    private static double wrightOmega4(double z) {
        double w3 = wrightOmega(z);
        return w3 - (w3 - Math.exp(z - w3)) / (w3 + 1);
    }

    /**
     * PN 结牛顿迭代的对数阻尼（SPICE pnLim 的标准做法，交错电网同款）：
     * 结电压越过 vCrit 且步长超过 2V_T 时，改用对数增长限制步长，
     * 防止 {@code exp(V/V_T)} 在大电压下溢出发散。
     */
    private double pnLim(double v1, double v0) {
        if (v0 < 0 && v1 > vCrit)
            return vCrit;
        if (v0 >= 0 && v0 < vCrit && v1 > vCrit)
            return vCrit;
        double dV = v1 - v0;
        if (v1 > vCrit && Math.abs(dV) > V_T * 2) {
            double arg = dV / V_T;
            if (arg + 1 < 0)
                return vCrit;
            return v0 + V_T * Math.log1p(arg);
        }
        return v1;
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
        if (first) {
            vd = vOld; // 首个牛顿迭代以上一微刻的工作点为初值
        } else {
            vd = pnLim(vd, vOld);
            vOld = vd;
        }

        if (junction) {
            // 基射结：指数二极管 + 串联基极扩展电阻（Wright Omega 闭式解）
            state.vbe = vd;
            double isRs = saturationCurrent * BASE_RESISTANCE;
            double omegaArg = Math.log(isRs / V_T) + (isRs + vd) / V_T;
            double w = wrightOmega4(omegaArg);
            // Banwell & Jayakumar (2000)：I = V_T·W/R_s − I_s，G = W/(R_s(1+W))
            double id = V_T * w / BASE_RESISTANCE - saturationCurrent;
            double gd = Math.max(w / (BASE_RESISTANCE * (1 + w)), LEAKAGE);
            // 诺顿等效：I(n1→n2) = G·vd − I_src，PNP 电流方向镜像
            conductance = gd;
            currentSource = (pnp ? -1 : 1) * (gd * vd - id);
            return;
        }

        // 集射极：I_C = min(β · I_B, I_C max)，I_B 由共享工作点的结电压
        // 经同一 Wright Omega 闭式解求出（与基射结元件的分支方程一致）
        double isRs = saturationCurrent * BASE_RESISTANCE;
        double omegaArg = Math.log(isRs / V_T) + (isRs + state.vbe) / V_T;
        double w = wrightOmega4(omegaArg);
        double ib = Math.max(0, V_T * w / BASE_RESISTANCE - saturationCurrent);
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
