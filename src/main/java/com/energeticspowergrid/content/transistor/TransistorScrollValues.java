package com.energeticspowergrid.content.transistor;

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
 * 三极管的三个参数菜单，遵循 CEE 的滚轮数值编辑模式
 * （模型上的扳手式数值框：滚动选择或点击输入数字）。
 * <p>
 * 本来有三个原因会让这些行为被合并成一个，这里逐一处理掉了：
 * <ul>
 * <li>SmartBlockEntity 用以 {@link BehaviourType} 为键的 Map 存储行为，
 * 所以每个子类都要有自己的 TYPE 常量并覆写 {@code getType()}。</li>
 * <li>{@code ValueSettingsPacket} 在服务端按 {@link #netId()} 匹配要编辑的
 * 行为——而该方法的默认实现对所有行为都返回 0——所以每个子类都要覆写
 * 它并返回互不相同的索引。</li>
 * <li>基类 {@code ScrollValueBehaviour} 把数值写到共享的 NBT 键
 * "ScrollValue"，所以每个子类都要覆写 write/read 并使用各自的键。</li>
 * </ul>
 */
public final class TransistorScrollValues {
    /** 工具类，禁止实例化。 */
    private TransistorScrollValues() {
    }

    /** 放大倍数 β，范围 1..1000，以 x100 整数存储以获得 0.01 的分辨率。 */
    public static class Beta extends ScrollValueBehaviour {
        /** 独立的 BehaviourType：SmartBlockEntity 按类型存 Map，共用会互相覆盖。 */
        public static final BehaviourType<Beta> TYPE = new BehaviourType<>("energeticspowergrid:transistor_beta");

        /** 初始 β = 100（内部值 10000 = 100 * 100）。 */
        public Beta(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(100, 100_000);
            value = 10_000; // beta = 100
        }

        /** 覆写为独立的类型，保证与其他两个参数菜单互不冲突。 */
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        /** 网络包匹配 ID：Beta 用 0（基类默认全是 0，必须区分开）。 */
        @Override
        public int netId() {
            return 0;
        }

        /** 写入独立的 NBT 键，避开基类共享的 "ScrollValue" 键。 */
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("TransistorBeta", value);
        }

        /** 读取时钳制到 [100, 100000]，防存档损坏或被篡改。 */
        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("TransistorBeta"), 100, 100_000);
        }

        /** 构建编辑面板：190 格刻度、每 10 格一个大刻度，显示 β 值。 */
        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("\u00d71")),
                    new ValueSettingsFormatter(vs -> Component.literal("\u03b2: " + indexToBeta(vs.value(), vs.row()))
                            .withStyle(ChatFormatting.AQUA)));
        }

        /** 把面板选出的索引换算成 β，再存为 x100 整数；值变化时播放反馈音。 */
        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double beta = indexToBeta(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(beta * 100), 100, 100_000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

        /** 反向换算：把存储的 x100 值映射回面板的 (row, index) 便于回显光标。 */
        @Override
        public ValueSettings getValueSettings() {
            double b = value / 100d;
            if (b <= 100)
                return new ValueSettings(0, Mth.clamp((int) Math.round(b), 1, 100));
            return new ValueSettings(0, Mth.clamp(90 + (int) Math.round(b / 10d), 100, 190));
        }

        /** 数值框上显示的文本，如 "β 2.5"。 */
        @Override
        public String formatValue() {
            return "\u03b2 " + value / 100d;
        }

        /** 对外提供真实 β 值（存储值除以 100）。 */
        public double getBeta() {
            return value / 100d;
        }

        /**
         * 面板索引 -> β：1..100 直接线性；100 之后斜率变为 10 倍
         * （索引 90 处折返，90..190 对应 β 100..1000），让一个面板覆盖两个量程。
         */
        private static double indexToBeta(int i, int row) {
            return i < 100 ? Math.max(1, i) : (i - 90) * 10d;
        }
    }

    /** 基射极导通电压（单位：伏），范围 0.05..3，以毫伏整数存储。 */
    public static class Vbe extends ScrollValueBehaviour {
        /** 独立的 BehaviourType，避免与 Beta/IcMax 在 Map 中互相覆盖。 */
        public static final BehaviourType<Vbe> TYPE = new BehaviourType<>("energeticspowergrid:transistor_vbe");

        /** 初始 0.7 V（内部值 700 毫伏），典型硅管压降。 */
        public Vbe(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(50, 3000);
            value = 700; // 0.7 V
        }

        /** 覆写为独立的类型，保证与其他两个参数菜单互不冲突。 */
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        /** 网络包匹配 ID：Vbe 用 1。 */
        @Override
        public int netId() {
            return 1;
        }

        /** 写入独立的 NBT 键，避开基类共享的 "ScrollValue" 键。 */
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("TransistorVbe", value);
        }

        /** 读取时钳制到 [50, 3000] 毫伏。 */
        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("TransistorVbe"), 50, 3000);
        }

        /** 两行面板：第 0 行步进 0.01 V（<1 V），第 1 行步进 0.1 V（≥1 V）。 */
        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("\u00d70.01 V"), Component.literal("\u00d70.1 V")),
                    new ValueSettingsFormatter(vs -> Component.literal(
                            LangNumberFormat.format(indexToVolts(vs.value(), vs.row())) + " V")
                            .withStyle(ChatFormatting.AQUA)));
        }

        /** 把面板选出的索引换算成伏特，再存为毫伏整数；值变化时播放反馈音。 */
        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double volts = indexToVolts(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(volts * 1000), 50, 3000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

        /** 反向换算：<1 V 走 0.01 V 行，≥1 V 走 0.1 V 行，便于面板回显。 */
        @Override
        public ValueSettings getValueSettings() {
            double v = value / 1000d;
            if (v < 1)
                return new ValueSettings(0, Mth.clamp((int) Math.round(v * 100), 5, 99));
            return new ValueSettings(1, Mth.clamp((int) Math.round(v * 10), 10, 30));
        }

        /** 数值框上显示的文本，如 "0.7 V"。 */
        @Override
        public String formatValue() {
            return (value / 1000d) + " V";
        }

        /** 对外提供真实电压值（毫伏除以 1000）。 */
        public double getVbeOn() {
            return value / 1000d;
        }

        /** 面板索引 -> 伏特：第 0 行 0.01 V 步进（下限 0.05 V），第 1 行 0.1 V 步进。 */
        private static double indexToVolts(int i, int row) {
            if (row == 0)
                return Math.max(0.05, i / 100d);
            return Math.max(1, i / 10d);
        }
    }

    /** 最大集电极电流（单位：安），范围 0.001..1000，以毫安整数存储。 */
    public static class IcMax extends ScrollValueBehaviour {
        /** 独立的 BehaviourType，避免与 Beta/Vbe 在 Map 中互相覆盖。 */
        public static final BehaviourType<IcMax> TYPE = new BehaviourType<>("energeticspowergrid:transistor_icmax");

        /** 初始 1 A（内部值 1000 毫安）。 */
        public IcMax(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(1, 1_000_000);
            value = 1000; // 1 A
        }

        /** 覆写为独立的类型，保证与其他两个参数菜单互不冲突。 */
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        /** 网络包匹配 ID：IcMax 用 2。 */
        @Override
        public int netId() {
            return 2;
        }

        /** 写入独立的 NBT 键，避开基类共享的 "ScrollValue" 键。 */
        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("TransistorIcMax", value);
        }

        /** 读取时钳制到 [1, 1000000] 毫安。 */
        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("TransistorIcMax"), 1, 1_000_000);
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

        /**
         * 反向换算，分四段映射回 (row, index)：mA <100 直存；
         * 100..1000 mA 压缩到索引 90..99（斜率 10）；A 行同理分两段，
         * 保证跨数量级时仍有足够分辨率。
         */
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

        /** 数值框显示：≥1 A 用安培，否则用毫安，避免出现过多小数位。 */
        @Override
        public String formatValue() {
            double a = value / 1000d;
            return a >= 1 ? a + " A" : value + " mA";
        }

        /** 对外提供真实电流值（毫安除以 1000）。 */
        public double getIcMax() {
            return value / 1000d;
        }

        /**
         * 面板索引 -> 安培：mA 行 1..99 线性、90 之后斜率 10；
         * A 行同样两段（1..100 A 与 100..1000 A），与 getValueSettings 互逆。
         */
        private static double indexToAmps(int i, int row) {
            if (row == 0)
                return (i < 100 ? Math.max(1, i) : (i - 90) * 10d) / 1000d;
            return ((i < 100 ? i * 1000d : (i - 90) * 10_000d)) / 1000d;
        }
    }
}
