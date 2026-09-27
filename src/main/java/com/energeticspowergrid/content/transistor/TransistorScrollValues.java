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
 * The transistor's three parameter menus, following CEE's scroll-value editing pattern
 * (wrench-style value box on the model: scroll or click to type a number).
 * <p>
 * Three things would normally collapse these into one behaviour, each handled here:
 * <ul>
 * <li>SmartBlockEntity stores behaviours in a map keyed by {@link BehaviourType}, so each
 * subclass carries its own TYPE constant and overrides {@code getType()}.</li>
 * <li>{@code ValueSettingsPacket} matches the behaviour to edit server-side by
 * {@link #netId()} - whose default is 0 for every behaviour - so each subclass overrides it
 * with a distinct index.</li>
 * <li>The base {@code ScrollValueBehaviour} writes its value to the shared NBT key
 * "ScrollValue", so each subclass overrides write/read with a distinct key.</li>
 * </ul>
 */
public final class TransistorScrollValues {
    private TransistorScrollValues() {
    }

    /** Amplification beta, 1..1000, stored x100 for 0.01 resolution. */
    public static class Beta extends ScrollValueBehaviour {
        public static final BehaviourType<Beta> TYPE = new BehaviourType<>("energeticspowergrid:transistor_beta");

        public Beta(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(100, 100_000);
            value = 10_000; // beta = 100
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        @Override
        public int netId() {
            return 0;
        }

        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("TransistorBeta", value);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("TransistorBeta"), 100, 100_000);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("\u00d71")),
                    new ValueSettingsFormatter(vs -> Component.literal("\u03b2: " + indexToBeta(vs.value(), vs.row()))
                            .withStyle(ChatFormatting.AQUA)));
        }

        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double beta = indexToBeta(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(beta * 100), 100, 100_000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

        @Override
        public ValueSettings getValueSettings() {
            double b = value / 100d;
            if (b <= 100)
                return new ValueSettings(0, Mth.clamp((int) Math.round(b), 1, 100));
            return new ValueSettings(0, Mth.clamp(90 + (int) Math.round(b / 10d), 100, 190));
        }

        @Override
        public String formatValue() {
            return "\u03b2 " + value / 100d;
        }

        public double getBeta() {
            return value / 100d;
        }

        private static double indexToBeta(int i, int row) {
            return i < 100 ? Math.max(1, i) : (i - 90) * 10d;
        }
    }

    /** Base-emitter switch-on voltage in volts, 0.05..3, stored in millivolts. */
    public static class Vbe extends ScrollValueBehaviour {
        public static final BehaviourType<Vbe> TYPE = new BehaviourType<>("energeticspowergrid:transistor_vbe");

        public Vbe(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(50, 3000);
            value = 700; // 0.7 V
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        @Override
        public int netId() {
            return 1;
        }

        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("TransistorVbe", value);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("TransistorVbe"), 50, 3000);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("\u00d70.01 V"), Component.literal("\u00d70.1 V")),
                    new ValueSettingsFormatter(vs -> Component.literal(
                            LangNumberFormat.format(indexToVolts(vs.value(), vs.row())) + " V")
                            .withStyle(ChatFormatting.AQUA)));
        }

        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double volts = indexToVolts(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(volts * 1000), 50, 3000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

        @Override
        public ValueSettings getValueSettings() {
            double v = value / 1000d;
            if (v < 1)
                return new ValueSettings(0, Mth.clamp((int) Math.round(v * 100), 5, 99));
            return new ValueSettings(1, Mth.clamp((int) Math.round(v * 10), 10, 30));
        }

        @Override
        public String formatValue() {
            return (value / 1000d) + " V";
        }

        public double getVbeOn() {
            return value / 1000d;
        }

        private static double indexToVolts(int i, int row) {
            if (row == 0)
                return Math.max(0.05, i / 100d);
            return Math.max(1, i / 10d);
        }
    }

    /** Max collector current in amps, 0.001..1000, stored in milliamps. */
    public static class IcMax extends ScrollValueBehaviour {
        public static final BehaviourType<IcMax> TYPE = new BehaviourType<>("energeticspowergrid:transistor_icmax");

        public IcMax(Component label, SmartBlockEntity be, ValueBoxTransform slot) {
            super(label, be, slot);
            between(1, 1_000_000);
            value = 1000; // 1 A
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        @Override
        public int netId() {
            return 2;
        }

        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            nbt.putInt("TransistorIcMax", value);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            value = Mth.clamp(nbt.getInt("TransistorIcMax"), 1, 1_000_000);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(label, 190, 10,
                    ImmutableList.of(Component.literal("mA"), Component.literal("A")),
                    new ValueSettingsFormatter(vs -> Component.literal(
                            LangNumberFormat.format(indexToAmps(vs.value(), vs.row())) + " A")
                            .withStyle(ChatFormatting.AQUA)));
        }

        @Override
        public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
            double amps = indexToAmps(valueSetting.value(), valueSetting.row());
            int stored = Mth.clamp((int) Math.round(amps * 1000), 1, 1_000_000);
            if (stored != value) {
                playFeedbackSound(this);
                setValue(stored);
            }
        }

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

        @Override
        public String formatValue() {
            double a = value / 1000d;
            return a >= 1 ? a + " A" : value + " mA";
        }

        public double getIcMax() {
            return value / 1000d;
        }

        private static double indexToAmps(int i, int row) {
            if (row == 0)
                return (i < 100 ? Math.max(1, i) : (i - 90) * 10d) / 1000d;
            return ((i < 100 ? i * 1000d : (i - 90) * 10_000d)) / 1000d;
        }
    }
}
