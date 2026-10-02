package com.energeticspowergrid.content.excitation.mixin;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice;
import com.george_vi.electroenergetics.content.rotor.VirtualRotor;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让三相电刷设备的输出频率像真实交流发电机一样随负载下垂（droop），
 * 并在负载稳定后的几秒内缓慢恢复。
 * <p>
 * 采用"基线-偏差"模型：设备内部维护一个稳定基线负载 {@code stableStress}，
 * 频率偏移与"当前负载相对基线的偏差量"线性成正比——偏差达到配置的满量程
 * 负载（默认 100kW）时恰好打满上限（默认 0.08Hz），小调整给小偏移，且因为
 * 偏移是向目标值缓动的连续量，微调时能读出中间值而不是 0 与 0.08 两个台阶。
 * 偏差在满量程负载 5% 以内时视为励磁微调噪声，频率完全不波动（宽容死区）。
 * 负载稳定后，基线按恢复窗口逐渐吸收当前负载，偏差随之衰减，频率缓慢回升。
 * <p>
 * （旧的"逐刻差分累加"模型下，励磁调整期间每刻的变化量动辄数千瓦，累加值
 * 几乎瞬间打满上限并被钳死在那里，表现成偏移只有 0 与 0.08 两个台阶。）
 * <p>
 * 偏移量在设备写入 {@code virtualRotor.rpm} 之后追加进去，因此紧随其后的
 * 本次求解的每个 micro tick——包括 20 Hz 以上的交流仿真——都会看到偏移后的
 * 频率。全程只读取设备自身维护的数值，每个仿真 tick 只有几次浮点运算，且
 * 完全不触碰世界或任何 BlockEntity，所以在离线程的仿真器上运行也是安全的。
 */
@Mixin(ThreePhaseAlternatorBrushesDevice.class)
public abstract class ThreePhaseAlternatorBrushesDeviceMixin {
    /**
     * 负载变化低于此瓦数视为漂移噪声而非真实的负载阶跃，不做出频率响应，
     * 避免小幅负载波动导致频率来回抖动。
     */
    @Unique
    private static final float EPG$LOAD_STEP_DEADBAND = 1f;

    // ---- 下垂状态的持久变量（随设备实例存活，行为类似设备自身字段） ----
    /** 当前叠加在输出频率上的下垂偏移量（Hz，带符号：下垂为负）。 */
    @Unique
    private float epg$dipHz;
    /** 稳定基线负载（瓦）：负载稳定时按恢复窗口逐渐吸收当前值，是偏差的基准。 */
    @Unique
    private float epg$stableStress;
    /** 上一个仿真 tick 的应力值，用于差分判断负载是否仍在变化。 */
    @Unique
    private float epg$lastStress;
    /** 是否已经采集到首个应力样本（首个 tick 没有基准，直接把基线设为当前值）。 */
    @Unique
    private boolean epg$stressKnown;

    // ---- 注入目标维护的设备状态 ----
    /** 转速（绝对值）与应力（功率负载），均为设备在每个仿真 tick 写入的最新值。 */
    @Shadow(remap = false)
    public float rpmSpeed;
    @Shadow(remap = false)
    public float stress;
    @Shadow(remap = false)
    public VirtualRotor virtualRotor;

    /**
     * 在 preTick 的 TAIL 注入：此时设备已把转速写入 virtualRotor.rpm，
     * 在这里追加下垂偏移，后续的电气求解就会使用偏移后的频率。
     */
    @Inject(method = "preTick", at = @At("TAIL"), remap = false)
    private void epg$loadDependentFrequencyDip(CallbackInfo ci) {
        // 虚拟转子尚未初始化时无事可做
        if (virtualRotor == null)
            return;

        // 差分应力得到本 tick 的负载变化量；首 tick 无基准，按 0 处理
        float delta = epg$stressKnown ? stress - epg$lastStress : 0;
        epg$lastStress = stress;

        // 读取下垂配置：最大下垂幅度（Hz）、恢复窗口（tick 数）、满量程负载（瓦）
        float maxHz = Math.abs(EPGConfigs.server().frequencyDipMaxHz.getF());
        int decayTicks = Math.max(1, EPGConfigs.server().frequencyDipDecayTicks.get());
        float fullScaleWatts = Math.max(0.1f, EPGConfigs.server().frequencyDipFullScaleKilowatt.getF()) * 1000f;

        if (!epg$stressKnown) {
            // 首个样本：基线即当前负载，不产生任何偏移
            epg$stableStress = stress;
            epg$stressKnown = true;
        } else if (Math.abs(delta) < EPG$LOAD_STEP_DEADBAND) {
            // 负载已稳定：基线在恢复窗口内逐渐吸收当前负载，
            // 偏差随之衰减，频率缓慢回升到额定值
            epg$stableStress = Mth.lerp(1f / decayTicks, epg$stableStress, stress);
        }
        // 负载仍在变化时基线保持不动，偏差反映"本轮调整总共改了多少功率"

        // 目标偏移与"当前负载 - 基线"的偏差线性成正比：
        // 发出功率增大 -> 频率下垂；功率减小 -> 频率回升（取负号即为此意）。
        // 满量程负载 5% 以内的偏差视为励磁微调噪声，频率完全不响应（宽容死区）；
        // 超过死区后从零开始按线性比例过渡到满量程偏移，没有跳变。
        float deviation = stress - epg$stableStress;
        float deadbandWatts = fullScaleWatts * 0.05f;
        float magnitude = Math.abs(deviation);
        float effectiveDeviation = magnitude > deadbandWatts ? magnitude - deadbandWatts : 0;
        float target = Mth.clamp(-Math.signum(deviation) * effectiveDeviation / fullScaleWatts * maxHz, -maxHz, maxHz);
        // 偏移向目标缓动：既平滑转子惰性刻造成的 8 tick 一次的小台阶，
        // 也让频率变化本身有物理惯性，而不是瞬间跳变
        epg$dipHz = Mth.lerp(0.2f, epg$dipHz, target);

        // 无下垂偏移时不需要改动转子频率
        if (epg$dipHz == 0)
            return;
        // 静止的机器不发电，也没有可偏移的频率可言
        if (rpmSpeed == 0)
            return;

        // virtualRotor.rpm 存的是绝对值，且 频率 = rpm / hertzPerRPM，
        // 因此频率偏移等比换算成 rpm 偏移。在 0 处钳制保证频率不为负；
        // 遇到异常非有限值时退回设备自身的转速绝对值，保证状态始终有效。
        float hertzPerRpm = Math.max(0.01f, EPGConfigs.server().hertzPerRpm.getF());
        float shifted = virtualRotor.rpm + epg$dipHz * hertzPerRpm;
        virtualRotor.rpm = Float.isFinite(shifted) ? Math.max(0, shifted) : Math.abs(rpmSpeed);
    }
}
