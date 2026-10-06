package com.energeticspowergrid.content.mosfet;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;

import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * MOS 管的三个参数菜单，遵循 CEE 的滚轮数值编辑模式。
 * <p>
 * 与三极管参数菜单相同，这里有三个会让行为互相冲突的坑，逐一处理掉了：
 * 独立 {@link BehaviourType}（SmartBlockEntity 按 Type 存 Map）、
 * 独立 {@link #netId()}（ValueSettingsPacket 按它匹配）、
 * 独立 NBT 键（基类共享 "ScrollValue" 键）。
 * <p>
 * 三个参数与三极管参数的对应关系：
 * β（放大倍数）没有对应物——MOS 管是电压驱动，不存在电流放大比；
 * V_BE(on) 对应 <b>V_GS(th) 阈值电压</b>（电压驱动的门槛）；
 * I_C max 对应 <b>I_D max 最大漏极电流</b>；
 * 额外多出 <b>R_DS(on) 导通电阻</b>——MOS 管导通后是纯电阻，
 * 它直接决定导通损耗（低功耗特性的来源）。
 */
public final class MosfetScrollValues {
    /** 工具类，禁止实例化。 */
    private MosfetScrollValues() {
    }

    /** 阈值电压 V_GS(th)，范围 0.1..20V，以毫伏整数存储。 */
    public static class Vth extends ScrollValueBehaviour {
        /** 独立的 BehaviourType：SmartBlockEntity 按类型存 Map，共用会互相覆盖。 */
        public static final BehaviourType<Vth> TYPE = new BehaviourType<>("energeticspowergrid:mosfet_vth");

        /** 初始 2V（内部值 2000 毫伏），逻辑电平 MOS 管的典型阈值。 */
        public Vth(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(100, 20_000);
            value = 2000; // 2 V
        }

        /** 覆写为独立的类型，保证与其他两个参数菜单互不冲突。 */
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        /** 网络包匹配 ID：Vth 用 0（基类默认全是 0，必须区分开）。 */
        @Override
        public int netId() {
            return 0;
        }

        /** 写入独立的 NBT 键，避开基类共享的 "ScrollValue" 键。 */
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("MosfetVth", value);
        }

        /** 读取时钳制到 [100, 20000] 毫伏。 */
        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("MosfetVth"), 100, 20_000);
        }

        /** 两行面板：第 0 行步进 0.1 V（<2V），第 1 行步进 1 V（≥2V）。 */
        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("\u00d70.1 V"), Component.literal("\u00d71 V")),
                    new ValueSettingsFormatter(vs -> Component.literal(
                            LangNumberFormat.format(indexToVolts(vs.value(), vs.row())) + " V")
                            .withStyle(ChatFormatting.AQUA)));
        }

        /** 把面板选出的索引换算成伏特，再存为毫伏整数；值变化时播放反馈音。 */
        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double volts = indexToVolts(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(volts * 1000), 100, 20_000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

        /** 反向换算：<2V 走 0.1V 行，≥2V 走 1V 行，便于面板回显。 */
        @Override
        public ValueSettings getValueSettings() {
            double v = value / 1000d;
            if (v < 2)
                return new ValueSettings(0, Mth.clamp((int) Math.round(v * 10), 1, 19));
            return new ValueSettings(1, Mth.clamp((int) Math.round(v), 2, 20));
        }

        /** 数值框上显示的文本，如 "2.0 V"。 */
        @Override
        public String formatValue() {
            return (value / 1000d) + " V";
        }

        /** 对外提供真实电压值（毫伏除以 1000）。 */
        public double getVth() {
            return value / 1000d;
        }

        /** 面板索引 -> 伏特：第 0 行 0.1V 步进（0.1..1.9V），第 1 行 1V 步进（2..20V）。 */
        private static double indexToVolts(int i, int row) {
            if (row == 0)
                return Math.max(0.1, i / 10d);
            return Math.max(2, i);
        }
    }

    /** 导通电阻 R_DS(on)，范围 0.001..100Ω，以毫欧整数存储。 */
    public static class RdsOn extends ScrollValueBehaviour {
        /** 独立的 BehaviourType，避免与 Vth/IdMax 在 Map 中互相覆盖。 */
        public static final BehaviourType<RdsOn> TYPE = new BehaviourType<>("energeticspowergrid:mosfet_rdson");

        /** 初始 0.1Ω（内部值 100 毫欧），功率型 MOS 管的典型值。 */
        public RdsOn(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(1, 100_000);
            value = 100; // 0.1 Ω
        }

        /** 覆写为独立的类型，保证与其他两个参数菜单互不冲突。 */
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        /** 网络包匹配 ID：RdsOn 用 1。 */
        @Override
        public int netId() {
            return 1;
        }

        /** 写入独立的 NBT 键，避开基类共享的 "ScrollValue" 键。 */
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("MosfetRdsOn", value);
        }

        /** 读取时钳制到 [1, 100000] 毫欧。 */
        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("MosfetRdsOn"), 1, 100_000);
        }

        /** 两行面板：mΩ 行与 Ω 行，覆盖 0.001 到 100 欧姆。 */
        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("m\u03a9"), Component.literal("\u03a9")),
                    new ValueSettingsFormatter(vs -> Component.literal(
                            LangNumberFormat.format(indexToOhms(vs.value(), vs.row())) + " \u03a9")
                            .withStyle(ChatFormatting.AQUA)));
        }

        /** 把面板选出的索引换算成欧姆，再存为毫欧整数；值变化时播放反馈音。 */
        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double ohms = indexToOhms(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(ohms * 1000), 1, 100_000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

        /** 反向换算，四段映射回 (row, index)，保证跨数量级仍有分辨率。 */
        @Override
        public ValueSettings getValueSettings() {
            double mOhm = value;
            if (mOhm < 100)
                return new ValueSettings(0, (int) Math.max(1, mOhm));
            if (mOhm < 1000)
                return new ValueSettings(0, (int) (mOhm / 10) + 90);
            if (mOhm < 100_000)
                return new ValueSettings(1, (int) (mOhm / 1000));
            return new ValueSettings(1, (int) (mOhm / 10_000) + 90);
        }

        /** 数值框显示：≥1Ω 用欧姆，否则用毫欧。 */
        @Override
        public String formatValue() {
            double o = value / 1000d;
            return o >= 1 ? o + " \u03a9" : value + " m\u03a9";
        }

        /** 对外提供真实电阻值（毫欧除以 1000）。 */
        public double getRdsOn() {
            return value / 1000d;
        }

        /** 面板索引 -> 欧姆：mΩ 行两段斜率，Ω 行两段斜率，与 getValueSettings 互逆。 */
        private static double indexToOhms(int i, int row) {
            if (row == 0)
                return (i < 100 ? Math.max(1, i) : (i - 90) * 10d) / 1000d;
            return (i < 100 ? i * 1000d : (i - 90) * 10_000d) / 1000d;
        }
    }

    /** 最大漏极电流（单位：安），范围 0.001..1000，以毫安整数存储。 */
    public static class IdMax extends ScrollValueBehaviour {
        /** 独立的 BehaviourType，避免与 Vth/RdsOn 在 Map 中互相覆盖。 */
        public static final BehaviourType<IdMax> TYPE = new BehaviourType<>("energeticspowergrid:mosfet_idmax");

        /** 初始 10A（内部值 10000 毫安），功率 MOS 管的典型量级。 */
        public IdMax(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(1, 1_000_000);
            value = 10_000; // 10 A
        }

        /** 覆写为独立的类型，保证与其他两个参数菜单互不冲突。 */
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        /** 网络包匹配 ID：IdMax 用 2。 */
        @Override
        public int netId() {
            return 2;
        }

        /** 写入独立的 NBT 键，避开基类共享的 "ScrollValue" 键。 */
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("MosfetIdMax", value);
        }

        /** 读取时钳制到 [1, 1000000] 毫安。 */
        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("MosfetIdMax"), 1, 1_000_000);
        }

        /** 两行面板：mA 行与 A 行，覆盖 0.001 A 到 1000 A 的六个数量级。 */
        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("mA"), Component.literal("A")),
                    new ValueSettingsFormatter(vs -> Component.literal(
                            LangNumberFormat.format(indexToAmps(vs.value(), vs.row())) + " A")
                            .withStyle(ChatFormatting.AQUA)));
        }

        /** 把面板选出的索引换算成安培，再存为毫安整数；值变化时播放反馈音。 */
        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double amps = indexToAmps(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(amps * 1000), 1, 1_000_000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

        /** 反向换算，四段映射回 (row, index)，保证跨数量级仍有分辨率。 */
        @Override
        public ValueSettings getValueSettings() {
            double mA = value;
            if (mA < 100)
                return new ValueSettings(0, (int) Math.max(1, mA));
            if (mA < 1000)
                return new ValueSettings(0, (int) (mA / 10) + 90);
            if (mA < 100_000)
                return new ValueSettings(1, (int) (mA / 1000));
            return new ValueSettings(1, (int) (mA / 10_000) + 90);
        }

        /** 数值框显示：≥1 A 用安培，否则用毫安。 */
        @Override
        public String formatValue() {
            double a = value / 1000d;
            return a >= 1 ? a + " A" : value + " mA";
        }

        /** 对外提供真实电流值（毫安除以 1000）。 */
        public double getIdMax() {
            return value / 1000d;
        }

        /** 面板索引 -> 安培：mA 行两段斜率，A 行两段斜率，与 getValueSettings 互逆。 */
        private static double indexToAmps(int i, int row) {
            if (row == 0)
                return (i < 100 ? Math.max(1, i) : (i - 90) * 10d) / 1000d;
            return ((i < 100 ? i * 1000d : (i - 90) * 10_000d)) / 1000d;
        }
    }
}
