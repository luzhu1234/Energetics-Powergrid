package com.energeticspowergrid.content.inverter;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import com.google.common.util.concurrent.AtomicDouble;

/**
 * 逆变器的输出元件：一个电动势按固定方波翻转符号的 Norton 源。
 * 由求解器在每个微刻重新求值，因此无论子步如何划分，波形在游戏时间内
 * 都保持精确的频率——与发电机相位绕组使用的是同一机制。
 * <p>
 * 采用 Norton 形式（电流源 {@code v/R} 并联电阻 {@code R}）并非风格偏好：
 * 如果按理想电压源建模，该元件的电流将无上限，并使求解器矩阵非 SPD
 * （对称正定），导致相连器件被冲击、求解发散。Norton 形式让矩阵保持
 * 电导形式，并把电流限制在 {@code v/R} 以内。
 * <p>
 * 只访问 {@code configure} 时捕获的原始量；电气仿真是离线程运行的，
 * 本类绝不能触碰方块、方块实体或世界对象。
 */
public class InverterElectricalProperties extends MicroTickingElectricalProperties {
    /** 输出峰值电压（跟随上一刻实测的输入电压）。 */
    private double amplitude;
    /** 输出内阻（串联电阻）。 */
    private double seriesResistance;
    /** 半个周期对应的微刻数。 */
    private double halfPeriodMicroTicks;
    /** 基准微刻（游戏时间 x 每刻微刻数），用于锁定全局时间轴。 */
    private long baseMicroTick;

    /** 上一次求解实际输出的能量（焦耳），在线程外累计。AtomicDouble 保证跨线程安全。 */
    private final AtomicDouble deliveredEnergy = new AtomicDouble();

    /**
     * 每刻在服务端线程上调用，捕获本次求解需要的全部原始量。
     *
     * @param amplitude        输出峰值电压（跟随实测输入电压）
     * @param seriesResistance 输出内阻
     * @param frequency        波形频率（Hz）
     * @param gameTime         世界游戏时间（服务端线程上捕获）
     * @param totalMicroTicks  每个游戏刻的微刻数
     */
    public void configure(double amplitude, double seriesResistance, double frequency, long gameTime, int totalMicroTicks) {
        this.amplitude = amplitude;
        this.seriesResistance = Math.max(0.01, seriesResistance);
        // 一个游戏刻是 0.05 s，所以 <frequency> Hz 的半波持续 10 / frequency 个游戏刻。
        this.halfPeriodMicroTicks = Math.max(0.25, (10.0 / frequency) * totalMicroTicks);
        this.baseMicroTick = gameTime * totalMicroTicks;
        this.voltageSource = 0;
        this.currentSource = 0;
        // 幅值过低时输出端表现为 1 kΩ 的弱负载，而不是一个零幅源的硬短路。
        this.resistance = amplitude > 0.01 ? this.seriesResistance : 1e3;
        deliveredEnergy.set(0);
    }

    /** 上一次求解输出的平均功率（瓦）；读取后清零，实现"取一次"语义。 */
    public double pollDeliveredPower() {
        return deliveredEnergy.getAndSet(0);
    }

    /**
     * 每个微刻由求解器调用，设置本元件的 Norton 参数。
     * 用全局微刻序号对半周期取模，奇偶决定极性——这就是方波；
     * 基准取自游戏时间，所以频率不随子步划分漂移。
     */
    @Override
    public void tick(double[] allVoltages, int microTick, int totalMicroTicks, int n1, int n2) {
        if (amplitude <= 0.01) {
            this.resistance = 1e3;
            this.currentSource = 0;
            return;
        }
        this.resistance = seriesResistance;
        long halfCycle = (long) ((baseMicroTick + microTick) / halfPeriodMicroTicks);
        double emf = (halfCycle & 1) == 0 ? amplitude : -amplitude;
        // Norton 形式：电流源 = emf / 内阻，配合并联内阻把电流天然限制在 v/R。
        this.currentSource = emf / seriesResistance;
    }

    /**
     * 每个微刻结束后由求解器调用，累计本元件实际输出的能量：
     * 用本微刻的 emf 与实测端电压差算出电流，P=|U·I| 按微刻占比折算。
     * 这是输入侧功率反推的数据来源（能量守恒的关键）。
     */
    @Override
    public void afterTick(double[] allVoltages, int n1, int n2, int microTick, int totalMicroTicks) {
        if (amplitude <= 0.01)
            return;
        long halfCycle = (long) ((baseMicroTick + microTick) / halfPeriodMicroTicks);
        double emf = (halfCycle & 1) == 0 ? amplitude : -amplitude;
        double vd = allVoltages[n1 * totalMicroTicks + microTick]
                - allVoltages[n2 * totalMicroTicks + microTick];
        double current = (emf - vd) / seriesResistance;
        deliveredEnergy.addAndGet(Math.abs(current * vd) / totalMicroTicks);
    }
}
