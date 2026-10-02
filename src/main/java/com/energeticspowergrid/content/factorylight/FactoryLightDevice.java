package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.content.bulb.GrowthLampItem;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 工厂灯（Factory Light）模拟电气设备。
 * <p>
 * 电气行为与 {@link com.energeticspowergrid.content.fixture.LightFixtureDevice} 基本一致：
 * preTick 把灯泡建模为温度相关电阻，postTick 依据电压定亮度档位、依据热损耗积分温度、
 * 过压/过热累计 4 tick 烧毁。额外特性：
 * <ul>
 *   <li>同轴相邻工厂灯之间通过极低阻值（{@link #SHARE_RESISTANCE}）桥接，
 *       使拼接的灯组在电气上互通，电流可以在灯段间流动；</li>
 *   <li>亮度档位 POWER 与 glow 差 1（POWER = glow + 1，1 表示有灯未通电）；</li>
 *   <li>亮度变化时同步触发向下投射光照的更新；生长灯的催熟作用于投射目标位置。</li>
 * </ul>
 */
public class FactoryLightDevice extends SimpleElectricalDevice {
    /** 环境温度基准（°C）。 */
    public static final float AMBIENT_TEMPERATURE = 22f;
    /** 相邻灯段之间的共享电阻（Ω），极低值近似导线，用于拼接灯组间传导电流。 */
    private static final double SHARE_RESISTANCE = 0.001;

    /** 当前安装的灯泡物品。 */
    @Nullable
    private Item bulbItem = Items.AIR;
    /** 灯丝温度（°C）。 */
    private float temperature = AMBIENT_TEMPERATURE;
    /** 是否烧毁。 */
    private boolean burned;
    /** 染色颜色。 */
    @Nullable
    private DyeColor color;
    /** 过压/过热连续 tick 计数。 */
    private int overheatTicks;
    /** 烧毁特效标记。 */
    private boolean playEffect;
    /** 缓存的方块实体引用（由 BE tick 反向挂载）。 */
    public FactoryLightBlockEntity be;

    public FactoryLightDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
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

    /** 获取灯丝温度。 */
    public float getTemperature() {
        return temperature;
    }

    /** 是否烧毁。 */
    public boolean isBurned() {
        return burned;
    }

    /** 获取染色颜色。 */
    @Nullable
    public DyeColor getColor() {
        return color;
    }

    /**
     * 换灯泡后重置状态：工厂灯熄灭档位是 1（而非 0，0 表示无灯泡），随后强制同步。
     */
    public void setInstalledBulb(@Nullable Item item, @Nullable DyeColor color) {
        this.bulbItem = item == null ? Items.AIR : item;
        this.color = color;
        this.temperature = AMBIENT_TEMPERATURE;
        this.burned = false;
        this.overheatTicks = 0;
        this.playEffect = false;
        setPowerLevel(hasBulb() ? 1 : 0);
        syncToBlockEntity(true);
    }

    /** 尝试染色：仅对可染色灯泡生效。 */
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
     * 电路构建阶段：
     * <ol>
     *   <li>与沿灯组轴向的相邻工厂灯建立低阻桥接（两条节点线各自桥接），
     *       使拼接灯组电气互通；</li>
     *   <li>若已安装未烧毁的灯泡，把节点 0-1 接入温度相关电阻（下限 0.1Ω）。</li>
     * </ol>
     */
    @Override
    public void preTick(BridgeCollector bridges) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FactoryLightBlock))
            return;
        Direction.Axis axis = state.getValue(FactoryLightBlock.HORIZONTAL_AXIS);
        shareWith(bridges, pos.relative(axis, 1), state);
        if (hasBulb() && !burned) {
            ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
            if (properties != null) {
                double resistance = Math.max(0.1, ((ILightBulb) bulbItem).resistanceFunction(temperature));
                bridges.builder(pos).resistor(0, 1, resistance);
            }
        }
    }

    /**
     * 与相邻工厂灯建立电气桥接：
     * 仅当邻居也是工厂灯且水平轴一致时，用两个极低阻值电阻
     * 分别桥接两灯的节点 0 与节点 1，近似把灯组连成同一条馈线。
     */
    private void shareWith(BridgeCollector bridges, BlockPos neighbor, BlockState self) {
        if (!level.isLoaded(neighbor))
            return;
        BlockState other = level.getBlockState(neighbor);
        if (!(other.getBlock() instanceof FactoryLightBlock))
            return;
        if (other.getValue(FactoryLightBlock.HORIZONTAL_AXIS) != self.getValue(FactoryLightBlock.HORIZONTAL_AXIS))
            return;
        // CEE 1.1.1 has bridge(Node, Node, ElectricalProperties) but not the double overload added in 1.2.0.
        ElectricalProperties share = ElectricalProperties.resistor(SHARE_RESISTANCE);
        bridges.bridge(new InWorldNode(0, pos), new InWorldNode(0, neighbor), share);
        bridges.bridge(new InWorldNode(1, pos), new InWorldNode(1, neighbor), share);
    }

    /**
     * 模拟结果处理阶段（同灯具，档位偏移 +1）：
     * 电压 ≥ 85% 额定 → glow=2；≥ 45% → glow=1；被 maxPowerLevel 截断后
     * 写入 POWER = glow + 1。烧毁时保持 POWER=1（有灯但熄灭）。
     * 生长灯点亮时对投射目标位置催熟作物。
     */
    @Override
    public void postTick(SimulationResults results) {
        // 无灯泡：档位 0
        if (!hasBulb()) {
            setPowerLevel(0);
            return;
        }
        ILightBulb bulb = (ILightBulb) bulbItem;
        ILightBulb.ThermalProperties properties = bulb.thermalProperties();
        if (properties == null)
            return;
        // 已烧毁：保持“有灯熄灭”档位
        if (burned) {
            setPowerLevel(1);
            return;
        }

        // 热学积分：发热 - 散热，除以 20（tick 换算）与热容
        double voltage = Math.abs(results.getVoltageAt(pos, 0, 1));
        double heat = results.getHeatLoss(pos, 0, 1);
        float dissipatedPower = properties.dissipationFactor() * (temperature - AMBIENT_TEMPERATURE);
        temperature += (float) ((heat - dissipatedPower) / 20.0 / properties.thermalMass());
        if (!Float.isFinite(temperature) || temperature < AMBIENT_TEMPERATURE)
            temperature = AMBIENT_TEMPERATURE;

        // 损坏判定：过压/过热连续 4 tick 且开启损坏配置
        boolean overvoltage = voltage >= properties.breakdownVoltage();
        boolean overheated = temperature >= properties.overheatTemperature();
        if (EPGConfigs.server().componentDamage.get() && (overvoltage || overheated)) {
            if (++overheatTicks >= 4) {
                burned = true;
                playEffect = true;
                temperature = AMBIENT_TEMPERATURE;
                setPowerLevel(1);
                spawnBurnParticles();
                syncToBlockEntity(true);
                return;
            }
        } else {
            overheatTicks = 0;
        }

        // 亮度档位：按电压比例划分，写入 POWER = glow + 1
        int glow = 0;
        double rated = properties.ratedVoltage();
        if (rated > 0 && voltage >= rated * 0.85)
            glow = 2;
        else if (rated > 0 && voltage >= rated * 0.45)
            glow = 1;
        glow = Math.min(glow, properties.maxPowerLevel());
        setPowerLevel(glow + 1);

        // 生长灯：对投射目标（灯下方落点）催熟作物
        if (glow > 0 && bulbItem instanceof GrowthLampItem growthLamp && level instanceof ServerLevel serverLevel) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FactoryLightBlock)
                growthLamp.tickCrops(serverLevel, projectionTarget(), Direction.DOWN);
        }
        syncToBlockEntity(false);
    }

    /**
     * 计算向下投射的目标位置：从灯往下逐格扫描，
     * 跟随已放置的光照方块与空气，直到投射范围尽头或被实体方块挡住。
     */
    private BlockPos projectionTarget() {
        int range = EPGConfigs.server().factoryLightProjectionRange.get();
        BlockPos last = pos;
        for (int i = 1; i < range; i++) {
            BlockPos below = pos.below(i);
            if (!level.isLoaded(below))
                break;
            BlockState state = level.getBlockState(below);
            if (state.getBlock() instanceof FactoryLightLightBlock)
                last = below;
            else if (!state.isAir())
                break;
            else
                last = below;
        }
        return last;
    }

    /**
     * 写入亮度档位方块状态，并联动触发向下投射光照的更新
     * （保证亮度变化时光照列立即刷新，而不用等 lazyTick 周期）。
     */
    private void setPowerLevel(int powerLevel) {
        if (!level.isLoaded(pos))
            return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FactoryLightBlock))
            return;
        if (state.getValue(FactoryLightBlock.POWER) != powerLevel)
            level.setBlockAndUpdate(pos, state.setValue(FactoryLightBlock.POWER, powerLevel));
        if (be != null && !be.isRemoved())
            be.projectDown(Math.max(powerLevel - 1, 0));
    }

    /** 广播烧毁闪光粒子。 */
    private void spawnBurnParticles() {
        if (level instanceof ServerLevel serverLevel)
            serverLevel.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    /** 惰性查找/校验 BE 引用并把状态推送过去（playEffect 发送一次即清零）。 */
    private void syncToBlockEntity(boolean force) {
        if (!level.isLoaded(pos))
            return;
        if (be == null && level.getBlockEntity(pos) instanceof FactoryLightBlockEntity found)
            be = found;
        if (be == null || be.isRemoved()) {
            be = null;
            return;
        }
        be.applyFromDevice(getBulbItem(), temperature, burned, color, playEffect, force);
        playEffect = false;
    }

    /** 从存档 NBT 读取。 */
    @Override
    public void read(CompoundTag tag) {
        if (tag.contains("Bulb"))
            bulbItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Bulb")));
        else
            bulbItem = Items.AIR;
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        color = tag.contains("Color") ? DyeColor.byId(tag.getInt("Color")) : null;
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

    /** 方块不再是工厂灯时移除设备。 */
    @Override
    public boolean shouldRemove(BlockState oldState, BlockState newState) {
        return !(newState.getBlock() instanceof FactoryLightBlock);
    }

    /** 客户端标签读取（回灌数据）。 */
    @SuppressWarnings("unused")
    public void readClientTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag);
    }
}
