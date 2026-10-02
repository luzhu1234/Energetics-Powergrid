package com.energeticspowergrid.content.fixture;

import com.energeticspowergrid.content.bulb.GrowthLampItem;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 灯具（Light Fixture）模拟电气设备。
 * <p>
 * 这是运行在服务端电网模拟器中的“纯设备”对象（不与方块实体一一绑定，
 * 而是保存在世界级 DevicesSavedData 中）。电气行为：
 * <ul>
 *   <li>preTick：把灯泡建模为一个电阻接入电路，阻值随温度变化（resistanceFunction）；</li>
 *   <li>postTick：读取模拟结果——节点电压决定亮度档位，电阻热损耗（功率）
 *       按热容/散热系数积分成灯丝温度；过压或过热持续 4 tick 且开启组件损坏时烧毁。</li>
 * </ul>
 * 状态（温度/烧毁/颜色）随后同步给 {@link LightFixtureBlockEntity} 供渲染与显示。
 */
public class LightFixtureDevice extends SimpleElectricalDevice {
    /** 环境温度基准（°C），灯丝冷却的下限。 */
    public static final float AMBIENT_TEMPERATURE = 22f;

    /** 当前安装的灯泡物品（决定电阻函数与热属性）。 */
    @Nullable
    private Item bulbItem = Items.AIR;
    /** 灯丝温度（°C）。 */
    private float temperature = AMBIENT_TEMPERATURE;
    /** 是否已烧毁（过压/过热累计 4 tick 后）。 */
    private boolean burned;
    /** 染色颜色（仅可染色灯泡）。 */
    @Nullable
    private DyeColor color;
    /** 过压/过热状态连续持续的 tick 计数，达到 4 即烧毁。 */
    private int overheatTicks;
    /** 特效标记：烧毁瞬间置位，随同步传给方块实体播放闪光粒子。 */
    private boolean playEffect;

    /** 对应方块实体的缓存引用，由 BE 的 tick() 反向挂载，避免每 tick 查找。 */
    public LightFixtureBlockEntity be;

    public LightFixtureDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /** 是否安装了有效灯泡。 */
    public boolean hasBulb() {
        return bulbItem != null && bulbItem != Items.AIR && bulbItem instanceof ILightBulb;
    }

    /** 获取灯泡物品，空位返回 AIR。 */
    public Item getBulbItem() {
        return bulbItem == null ? Items.AIR : bulbItem;
    }

    /** 获取灯丝温度（°C）。 */
    public float getTemperature() {
        return temperature;
    }

    /** 灯泡是否已烧毁。 */
    public boolean isBurned() {
        return burned;
    }

    /** 获取染色颜色（可能为 null）。 */
    @Nullable
    public DyeColor getColor() {
        return color;
    }

    /**
     * 玩家换灯泡后由方块实体调用：重置全部热学状态并强制同步。
     * 熄灭（POWER 归 0），温度回到环境温度，过热计数清零。
     */
    public void setInstalledBulb(@Nullable Item item, @Nullable DyeColor color) {
        this.bulbItem = item == null ? Items.AIR : item;
        this.color = color;
        this.temperature = AMBIENT_TEMPERATURE;
        this.burned = false;
        this.overheatTicks = 0;
        this.playEffect = false;
        setPowerLevel(0);
        syncToBlockEntity(true);
    }

    /** 尝试染色：仅当已安装可染色灯泡时成功。 */
    public boolean trySetColor(DyeColor color) {
        if (!hasBulb())
            return false;
        ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
        if (properties == null || !properties.dyeable())
            return false;
        this.color = color;
        syncToBlockEntity(true);
        return true;
    }

    /**
     * 电路构建阶段（每 tick 模拟前调用）：
     * 若已安装未烧毁的灯泡，把节点 0-1 之间接入一个电阻，
     * 阻值 = max(0.1, 灯泡电阻函数(当前温度))——下限 0.1Ω 防止数值奇异（近似短路）。
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        if (!hasBulb() || burned)
            return;
        ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
        if (properties == null)
            return;
        double resistance = Math.max(0.1, ((ILightBulb) bulbItem).resistanceFunction(temperature));
        bridges.builder(pos).resistor(0, 1, resistance);
    }

    /**
     * 模拟结果处理阶段（每 tick 模拟后调用）：
     * <ol>
     *   <li>读取节点 0-1 间电压绝对值与电阻热损耗功率（heat，单位 W）；</li>
     *   <li>热学积分：温度 += (发热 - 散热) / 20 / 热容。散热功率 = 散热系数 × 温差，
     *       除以 20 是把 W 换算到每 tick 的能量；</li>
     *   <li>损坏判定：电压 ≥ 击穿电压，或温度 ≥ 过热温度，且服务端开启 componentDamage
     *       时累计 4 tick 即烧毁——播放闪光、熄灭、置 playEffect；</li>
     *   <li>亮度档位：电压 ≥ 85% 额定 → 满档(2)；≥ 45% 额定 → 低档(1)；
     *       并被灯泡 maxPowerLevel 上限截断。档位写入方块状态 POWER 驱动光照；</li>
     *   <li>若为生长灯且点亮，对灯具朝向位置执行作物催熟 tick；</li>
     *   <li>最终把温度/烧毁/颜色同步到方块实体。</li>
     * </ol>
     */
    @Override
    public void postTick(SimulationResults results) {
        // 无灯泡：保持熄灭
        if (!hasBulb()) {
            setPowerLevel(0);
            return;
        }

        ILightBulb bulb = (ILightBulb) bulbItem;
        ILightBulb.ThermalProperties properties = bulb.thermalProperties();
        if (properties == null)
            return;

        // 已烧毁：不再计算
        if (burned) {
            setPowerLevel(0);
            return;
        }

        // 热学积分：发热来自电阻热损耗，散热与温差成正比
        double voltage = Math.abs(results.getVoltageAt(pos, 0, 1));
        double heat = results.getHeatLoss(pos, 0, 1);
        float dissipatedPower = properties.dissipationFactor() * (temperature - AMBIENT_TEMPERATURE);
        temperature += (float) ((heat - dissipatedPower) / 20.0 / properties.thermalMass());
        if (!Float.isFinite(temperature) || temperature < AMBIENT_TEMPERATURE)
            temperature = AMBIENT_TEMPERATURE;

        // 损坏判定：过压或过热，连续 4 tick 且开启损坏配置才烧毁（防止瞬时尖峰误杀）
        boolean overvoltage = voltage >= properties.breakdownVoltage();
        boolean overheated = temperature >= properties.overheatTemperature();
        if (EPGConfigs.server().componentDamage.get() && (overvoltage || overheated)) {
            if (++overheatTicks >= 4) {
                burned = true;
                playEffect = true;
                temperature = AMBIENT_TEMPERATURE;
                setPowerLevel(0);
                spawnBurnParticles();
                syncToBlockEntity(true);
                return;
            }
        } else {
            overheatTicks = 0;
        }

        // 亮度档位：按电压与额定电压的比例划分三档
        int powerLevel = 0;
        double rated = properties.ratedVoltage();
        if (rated > 0 && voltage >= rated * 0.85)
            powerLevel = 2;
        else if (rated > 0 && voltage >= rated * 0.45)
            powerLevel = 1;
        powerLevel = Math.min(powerLevel, properties.maxPowerLevel());
        setPowerLevel(powerLevel);

        // 生长灯：点亮时对灯具朝向的方块位置催熟作物
        if (powerLevel > 0 && bulbItem instanceof GrowthLampItem growthLamp && level instanceof ServerLevel serverLevel) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LightFixtureBlock)
                growthLamp.tickCrops(serverLevel, pos, state.getValue(LightFixtureBlock.FACING));
        }

        // 把状态推送到方块实体（非强制：内部有变化检测控制发包频率）
        syncToBlockEntity(false);
    }

    /** 把亮度档位写入方块状态 POWER（仅在方块为本模组灯具且值变化时更新）。 */
    private void setPowerLevel(int powerLevel) {
        if (!level.isLoaded(pos))
            return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LightFixtureBlock))
            return;
        if (state.getValue(LightFixtureBlock.POWER) != powerLevel)
            level.setBlockAndUpdate(pos, state.setValue(LightFixtureBlock.POWER, powerLevel));
    }

    /** 在方块中心广播闪光粒子（烧毁特效，服务端 → 客户端）。 */
    private void spawnBurnParticles() {
        if (level instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    /**
     * 把设备状态同步到方块实体。
     * 惰性查找并缓存 BE 引用；BE 已移除则断开引用。
     * playEffect 只传递一次（发送后立即清零）。
     */
    private void syncToBlockEntity(boolean force) {
        if (!level.isLoaded(pos))
            return;
        if (be == null && level.getBlockEntity(pos) instanceof LightFixtureBlockEntity found)
            be = found;
        if (be == null || be.isRemoved()) {
            be = null;
            return;
        }
        be.applyFromDevice(getBulbItem(), temperature, burned, color, playEffect, force);
        playEffect = false;
    }

    /** 从存档 NBT 读取设备状态。 */
    @Override
    public void read(CompoundTag tag) {
        if (tag.contains("Bulb")) {
            bulbItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Bulb")));
        } else {
            bulbItem = Items.AIR;
        }
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        if (tag.contains("Color"))
            color = DyeColor.byId(tag.getInt("Color"));
        else
            color = null;
    }

    /** 写入存档 NBT。 */
    @Override
    public void write(CompoundTag tag) {
        if (hasBulb())
            tag.putString("Bulb", BuiltInRegistries.ITEM.getKey(bulbItem).toString());
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Burned", burned);
        if (color != null)
            tag.putInt("Color", color.getId());
    }

    /** 方块不再是灯具时移除设备。 */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof LightFixtureBlock);
    }

    /** 组装发送给客户端的标签（在常规数据之上附加特效标记）。 */
    public CompoundTag writeClientTag() {
        CompoundTag tag = new CompoundTag();
        write(tag);
        if (playEffect)
            tag.putBoolean("Effect", true);
        return tag;
    }

    /** 客户端标签读取（客户端不保留设备实例，仅回灌数据用于调试/同步路径）。 */
    @SuppressWarnings("unused")
    public void readClientTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag);
    }
}
