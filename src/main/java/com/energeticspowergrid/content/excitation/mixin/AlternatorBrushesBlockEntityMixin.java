package com.energeticspowergrid.content.excitation.mixin;

import com.energeticspowergrid.content.excitation.EPGRotorFractional;
import com.george_vi.electroenergetics.CreateElectroEnergetics;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.config.CRotor;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesBlockEntity;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesDevice;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlockEntity;
import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让碳刷的输出功率预算使用"小数有效磁铁数"而不是被四舍五入的整数。
 * <p>
 * 原版 {@code tick} 按 {@code Σ rotor.magnets × |转速| × 48} 计算 totalStress，
 * 而 magnets 是 int——功率以一整块磁铁的输出为台阶跳变（640rpm 下约 30.7kW），
 * 微调励磁时功率纹丝不动，只有跨过一整块磁铁的门槛才猛跳一档。
 * <p>
 * 在 tick 的 TAIL 注入，用转子 mixin 暴露的连续值重算设备的
 * {@code stress}（每 tick 机械功率预算，直接决定发电功率）与电压。
 * 单相（{@link AlternatorBrushesDevice}）与三相
 * （{@link ThreePhaseAlternatorBrushesDevice}）碳刷共用同一个 BE tick，
 * 因此一个注入同时覆盖两种碳刷。转速追赶等其他逻辑保持原样。
 */
@Mixin(AlternatorBrushesBlockEntity.class)
public abstract class AlternatorBrushesBlockEntityMixin {
    /** 原版 tick 扫描到的转子列表（沿朝向直线延伸，最多 16 格）。 */
    @Shadow(remap = false)
    private List<AlternatorRotorBlockEntity> rotors;

    /** BE 当前电压（原版护目镜显示用），包级私有故经 Shadow 访问。 */
    @Shadow(remap = false)
    float voltage;

    /**
     * 取转子的小数有效磁铁数；转子尚未跑过第一次 lazyTick 时（-1）回退到
     * int 磁铁数，避免区块刚加载时碳刷输出归零。
     * int 磁铁数是电力学的包级私有字段，必须经由鸭子接口穿透访问。
     */
    private static float epg$fractionalOf(AlternatorRotorBlockEntity rotor) {
        if (rotor instanceof EPGRotorFractional duck) {
            float fractional = duck.epg$getFractionalMagnets();
            if (fractional >= 0)
                return fractional;
            return duck.epg$getMagnets();
        }
        return 0;
    }

    /**
     * 在 tick 末尾用连续磁铁数重算 stress 与电压，覆盖原版按整数算出的值。
     * 机械功率预算 stress 是发电功率的唯一来源（每微刻存入 storedEnergy），
     * 所以发电功率随励磁连续变化，台阶远细于 1kW。
     */
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void epg$fractionalStress(CallbackInfo ci) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel))
            return;

        float totalStress = 0;
        for (AlternatorRotorBlockEntity rotor : rotors)
            totalStress += epg$fractionalOf(rotor) * Math.abs(self.getSpeed());
        totalStress *= CEEConfigs.server().rotorValues.rotorPowerMultiplier.getF();

        float fullLoadCurrent = CEEConfigs.server().rotorValues.rotorFullLoadCurrent.getF();
        SimulatedDevice device = DevicesSavedData.load(serverLevel).getDevice(self.getBlockPos());
        if (device instanceof AlternatorBrushesDevice singlePhase) {
            singlePhase.stress = totalStress;
            singlePhase.voltage = totalStress / fullLoadCurrent;
        } else if (device instanceof ThreePhaseAlternatorBrushesDevice threePhase) {
            threePhase.stress = totalStress;
            threePhase.voltage = totalStress / fullLoadCurrent;
        }
    }

    /**
     * 护目镜读数同样连续化：原版 addToGoggleTooltip 按 int 磁铁数求和显示
     * 发电功率——实际发电已连续，但玩家看到的读数仍是台阶值。HEAD 取消
     * 并用小数磁铁数重新实现（文案、格式与原版逐行一致）。
     */
    @Inject(method = "addToGoggleTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private void epg$fractionalGoggles(List<Component> tooltip, boolean isPlayerSneaking, CallbackInfoReturnable<Boolean> cir) {
        KineticBlockEntity self = (KineticBlockEntity) (Object) this;
        float totalStress = 0;
        for (AlternatorRotorBlockEntity rotor : rotors)
            totalStress += epg$fractionalOf(rotor) * Math.abs(self.getSpeed());
        CRotor rotorConfig = CEEConfigs.server().rotorValues;
        totalStress *= rotorConfig.rotorPowerMultiplier.getF();

        Lang.builder(CreateElectroEnergetics.ID)
                .translate("gui.goggles.electric_stats")
                .forGoggles(tooltip);
        Lang.builder(CreateElectroEnergetics.ID)
                .translate("gui.goggles.energy_generation")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        Lang.builder(CreateElectroEnergetics.ID)
                .text(LangNumberFormat.format(Math.round(Math.abs(totalStress))))
                .translate("generic.watts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        Lang.builder(CreateElectroEnergetics.ID)
                .translate("gui.goggles.voltage")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        Lang.builder(CreateElectroEnergetics.ID)
                .text(LangNumberFormat.format(Math.round(voltage)))
                .translate("generic.volts")
                .text(" / " + LangNumberFormat.format(Math.round(totalStress / rotorConfig.rotorFullLoadCurrent.getF())))
                .translate("generic.volts")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        cir.setReturnValue(true);
    }
}
