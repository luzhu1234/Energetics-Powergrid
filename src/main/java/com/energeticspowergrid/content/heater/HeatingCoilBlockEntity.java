package com.energeticspowergrid.content.heater;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 加热线圈（Heating Coil）方块实体。
 * <p>
 * 保存并显示线圈温度与工作状态。State 枚举映射 Create 风扇的加工类型：
 * <ul>
 *   <li>COLD（&lt;200°C）：无加工能力；</li>
 *   <li>SMOKING（200~400°C）：风扇气流经过时执行烟熏加工（对应 {@link HeaterSmokingType}）；</li>
 *   <li>BLASTING（≥400°C）：执行鼓风焙烧加工（对应 {@link HeaterBlastingType}）。</li>
 * </ul>
 * 温度由 {@link HeatingCoilDevice} 在电气模拟后推送（setTemperature），
 * 状态切换时触发 blockUpdated 以便周围风扇重新判定热源有效性。
 * 佩戴护目镜（工程师护目镜）时可查看实时温度。
 */
public class HeatingCoilBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** 线圈工作状态：冷 / 烟熏 / 鼓风焙烧。 */
    public enum State {
        COLD,
        SMOKING,
        BLASTING
    }

    /** 线圈温度（°C），由模拟设备同步。 */
    private float temperature = HeatingCoilDevice.AMBIENT_TEMPERATURE;
    /** 由温度推导出的工作状态。 */
    private State state = State.COLD;

    public HeatingCoilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 无附加行为。 */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /**
     * 由模拟设备每 tick 调用：更新温度并重新推导状态。
     * 状态变化时触发 blockUpdated（让周围机械风扇重新评估热源）并 sendData 同步客户端；
     * 温度变化超过 0.5°C 也同步，保证护目镜读数相对实时。
     */
    public void setTemperature(float temperature) {
        boolean changed = Math.abs(this.temperature - temperature) > 0.5f;
        this.temperature = temperature;
        State next = stateOf(temperature);
        if (this.state != next) {
            this.state = next;
            if (level != null)
                level.blockUpdated(worldPosition, getBlockState().getBlock());
            sendData();
        } else if (changed)
            sendData();
    }

    /** 获取当前工作状态。 */
    public State getState() {
        return state;
    }

    /** 获取线圈温度（°C）。 */
    public float getTemperature() {
        return temperature;
    }

    /** 温度 → 状态映射：&lt;200 冷，&lt;400 烟熏，其余焙烧。 */
    private static State stateOf(float temperature) {
        if (temperature < 200f)
            return State.COLD;
        if (temperature < 400f)
            return State.SMOKING;
        return State.BLASTING;
    }

    /** 温度对应的护目镜文本颜色：冷=深灰、烟熏=绿、高温=黄、极热=红。 */
    private static ChatFormatting temperatureColor(float value) {
        if (value < 200f)
            return ChatFormatting.DARK_GRAY;
        if (value < 400f)
            return ChatFormatting.GREEN;
        if (value < 550f)
            return ChatFormatting.YELLOW;
        return ChatFormatting.RED;
    }

    /** 护目镜信息：标题 + 保留两位小数的温度（按温度区间着色）。 */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Lang.builder("energeticspowergrid").translate("gui.heater.info_header").forGoggles(tooltip);
        Lang.builder("energeticspowergrid").translate("gui.heater.title")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        float shown = Math.round(temperature * 100f) / 100f;
        Lang.builder("energeticspowergrid")
                .add(Component.literal(String.format("%.2f °C", shown)))
                .style(temperatureColor(shown))
                .forGoggles(tooltip, 1);
        return true;
    }

    /** 反序列化：读取温度并据此重算状态。 */
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        temperature = tag.getFloat("Temperature");
        state = stateOf(temperature);
    }

    /** 序列化：保存温度。 */
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Temperature", temperature);
    }
}
