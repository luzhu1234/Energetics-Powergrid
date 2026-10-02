package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.EPGBlocks;
import com.energeticspowergrid.config.EPGConfigs;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.Block.UPDATE_ALL_IMMEDIATE;

/**
 * 工厂灯（Factory Light）方块实体。
 * <p>
 * 职责与 {@link com.energeticspowergrid.content.fixture.LightFixtureBlockEntity} 类似：
 * 保存灯泡/温度/烧毁/颜色的显示状态，并由模拟设备同步。
 * 额外职责是“向下投射光照”：点亮时在本方块正下方的空气列中放置
 * {@link FactoryLightLightBlock} 虚拟光照方块（lazyTick 周期性维护），
 * 使灯挂在半空时也能照亮下方的厂房地面。
 */
public class FactoryLightBlockEntity extends SmartBlockEntity {
    /** 当前安装的灯泡物品。 */
    private Item bulbItem = Items.AIR;
    /** 灯丝温度（°C），由模拟设备同步。 */
    private float temperature = FactoryLightDevice.AMBIENT_TEMPERATURE;
    /** 灯泡是否烧毁。 */
    private boolean burned;
    /** 染色颜色（仅可染色灯泡）。 */
    @Nullable
    private DyeColor color;
    /** 特效标记：烧毁闪光粒子。 */
    private boolean playEffect;
    /** 上一次投射光照命中的方块位置，用于检测投射目标变化（换目标时先清理旧光照）。 */
    private BlockPos lastHitBlock;

    public FactoryLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // 每 20 tick（1 秒）执行一次 lazyTick，维护向下投射的光照
        setLazyTickRate(20);
    }

    /** 无附加行为。 */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /** 获取服务端侧与本方块关联的模拟设备实例（存于世界级 SavedData）。 */
    @Nullable
    public FactoryLightDevice getDevice() {
        if (!(level instanceof ServerLevel serverLevel))
            return null;
        return DevicesSavedData.load(serverLevel).getDevice(worldPosition, FactoryLightDevice.class);
    }

    /** 是否安装了有效灯泡。 */
    public boolean hasBulb() {
        return bulbItem != null && bulbItem != Items.AIR && bulbItem instanceof ILightBulb;
    }

    /** 获取灯泡物品，空位返回 AIR。 */
    public Item getBulbItem() {
        return bulbItem == null ? Items.AIR : bulbItem;
    }

    /** 灯泡是否烧毁。 */
    public boolean isBurned() {
        return burned;
    }

    /** 获取染色颜色（可能为 null）。 */
    @Nullable
    public DyeColor getColor() {
        return color;
    }

    /**
     * 计算发光贴图 alpha（0~1），同灯具逻辑，但档位取 POWER-1
     * （因为工厂灯 POWER=1 表示“有灯未通电”）。
     */
    public float getAlpha() {
        float x = Mth.clamp((temperature - 600f) / (1400f - 600f), 0, 1);
        int powerLevel = Math.max(getBlockState().getValue(FactoryLightBlock.POWER) - 1, 0);
        if (powerLevel == 2)
            return 1;
        if (powerLevel == 1)
            return Math.max(0.5625f, x * x);
        return x * x;
    }

    /** 当前亮度档位（0~2），即 POWER-1。 */
    public int getPowerLevel() {
        return Math.max(getBlockState().getValue(FactoryLightBlock.POWER) - 1, 0);
    }

    /** 复制灯泡物品堆用于掉落；无灯泡或烧毁时返回空堆。 */
    public ItemStack copyBulbStack() {
        if (!hasBulb() || burned)
            return ItemStack.EMPTY;
        return new ItemStack(bulbItem);
    }

    /** 由模拟设备调用：整体覆盖本地状态，仅在实际变化时向客户端发包（同灯具）。 */
    public void applyFromDevice(Item item, float temperature, boolean burned, @Nullable DyeColor color, boolean playEffect, boolean force) {
        boolean changed = this.bulbItem != item || this.burned != burned || this.color != color || Math.abs(this.temperature - temperature) > 8f || force;
        this.bulbItem = item;
        this.temperature = temperature;
        this.burned = burned;
        this.color = color;
        this.playEffect = playEffect;
        if (changed && !level.isClientSide)
            sendData();
    }

    /** 换灯泡入口：执行换灯逻辑并把结果同步到模拟设备（同灯具）。 */
    public boolean replaceBulb(Player player, InteractionHand hand, ItemStack usedStack) {
        if (level == null)
            return false;
        boolean result = replaceBulbInternal(player, hand, usedStack);
        if (result && !level.isClientSide) {
            FactoryLightDevice device = getDevice();
            if (device != null)
                device.setInstalledBulb(hasBulb() ? bulbItem : Items.AIR, color);
            notifyUpdate();
        }
        return result;
    }

    /** 换灯泡具体逻辑（取下/安装/清除烧毁/并堆/创造模式），与灯具完全一致。 */
    private boolean replaceBulbInternal(Player player, InteractionHand hand, ItemStack usedStack) {
        // 空手：取出灯泡
        if (usedStack == null || usedStack.isEmpty()) {
            if (!hasBulb())
                return false;
            if (!level.isClientSide) {
                if (!burned)
                    player.setItemInHand(hand, new ItemStack(bulbItem));
                clearBulb();
            }
            return true;
        }
        // 无灯泡：安装（副手染料可同时染色）
        if (!hasBulb()) {
            if (!level.isClientSide && usedStack.getItem() instanceof ILightBulb) {
                bulbItem = usedStack.getItem();
                burned = false;
                temperature = FactoryLightDevice.AMBIENT_TEMPERATURE;
                color = null;
                if (((ILightBulb) bulbItem).thermalProperties() != null && ((ILightBulb) bulbItem).thermalProperties().dyeable()
                        && player.getOffhandItem().getItem() instanceof DyeItem dye)
                    color = dye.getDyeColor();
                if (!player.isCreative())
                    usedStack.shrink(1);
            }
            return true;
        }
        // 已烧毁：右键清除
        if (burned) {
            if (!level.isClientSide)
                clearBulb();
            return true;
        }
        // 手持同种灯泡：取出并并堆
        if (bulbItem == usedStack.getItem() && usedStack.getCount() < usedStack.getMaxStackSize()) {
            if (!level.isClientSide) {
                if (!player.isCreative())
                    usedStack.grow(1);
                clearBulb();
            }
            return true;
        }
        // 创造模式：直接取下
        if (player.isCreative()) {
            if (!level.isClientSide)
                clearBulb();
            return true;
        }
        return false;
    }

    /** 清空灯泡状态并重置温度。 */
    private void clearBulb() {
        bulbItem = Items.AIR;
        burned = false;
        color = null;
        temperature = FactoryLightDevice.AMBIENT_TEMPERATURE;
    }

    /** 染色：仅对已安装且可染色的灯泡生效，并同步到设备。 */
    public ItemInteractionResult setColor(DyeColor color) {
        if (!hasBulb())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
        if (properties == null || !properties.dyeable())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        this.color = color;
        if (!level.isClientSide) {
            FactoryLightDevice device = getDevice();
            if (device != null)
                device.trySetColor(color);
            notifyUpdate();
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 每 tick：客户端播放烧毁闪光；服务端把自身引用挂到设备（device.be）。 */
    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide && playEffect) {
            var center = worldPosition.getCenter();
            level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0, 0, 0);
            playEffect = false;
        }
        if (level instanceof ServerLevel && getDevice() instanceof FactoryLightDevice device)
            device.be = this;
    }

    /**
     * 低频 tick（每 20 tick 一次）：服务端维护向下投射的光照方块。
     * 亮度档位变化时也会被设备端 setPowerLevel 立即触发一次。
     */
    @Override
    public void lazyTick() {
        super.lazyTick();
        if (!level.isClientSide)
            projectDown(getPowerLevel());
    }

    /**
     * 向下投射光照：
     * <ul>
     *   <li>bulbPower > 0：沿正下方逐格扫描至投射范围（配置 factoryLightProjectionRange），
     *       遇到已有光照方块则更新其亮度档（bulbPower-1）；遇到空气则放置新的光照方块；
     *       遇到实体方块则停止——若命中位置与上次不同，说明投射路径被改变，先清理旧光照；</li>
     *   <li>bulbPower == 0（熄灭）：清除正下方范围内全部光照方块。</li>
     * </ul>
     */
    public void projectDown(int bulbPower) {
        if (level == null || level.isClientSide)
            return;
        int range = EPGConfigs.server().factoryLightProjectionRange.get();
        if (lastHitBlock == null)
            lastHitBlock = worldPosition;
        if (bulbPower > 0) {
            // 点亮：逐格放置/更新光照方块直到被实体方块挡住
            for (int i = 1; i < range; ++i) {
                var pos = worldPosition.below(i);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof FactoryLightLightBlock) {
                    // 已有光照方块：档位不同则更新（档位比灯低一级）
                    if (state.getValue(FactoryLightLightBlock.POWER) != bulbPower - 1)
                        level.setBlock(pos, state.setValue(FactoryLightLightBlock.POWER, bulbPower - 1), UPDATE_ALL_IMMEDIATE);
                } else if (state.isAir()) {
                    // 空气：放置新的光照方块
                    level.setBlock(pos, EPGBlocks.FACTORY_LIGHT_LIGHT.getDefaultState()
                            .setValue(FactoryLightLightBlock.POWER, bulbPower - 1), UPDATE_ALL_IMMEDIATE);
                } else {
                    // 实体方块：投射被挡，若命中位置变了则清理旧光照
                    if (!lastHitBlock.equals(pos)) {
                        lastHitBlock = pos;
                        removeLights();
                    }
                    break;
                }
            }
        } else {
            // 熄灭：清除下方整条光照方块
            for (int i = 1; i < range; ++i) {
                var pos = worldPosition.below(i);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof FactoryLightLightBlock)
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL_IMMEDIATE);
                else
                    break;
            }
        }
    }

    /**
     * 清理本灯向下投射的全部光照方块（拆除方块/投射路径变化时调用）。
     * 遇到下一个工厂灯方块则提前停止（那是别的灯的投射区域）。
     */
    public void removeLights() {
        if (level == null)
            return;
        int range = EPGConfigs.server().factoryLightProjectionRange.get();
        for (int i = 0; i < range; ++i) {
            var pos = worldPosition.below(i);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof FactoryLightLightBlock) {
                if (level.getBlockEntity(pos) instanceof FactoryLightLightBlockEntity light)
                    light.onDelete();
            }
            if (state.getBlock() instanceof FactoryLightBlock && !this.getBlockPos().equals(pos))
                break;
        }
    }

    /** 渲染包围盒：向下延伸投射范围，保证光照方块的渲染/更新不因灯的包围盒过小而被裁剪。 */
    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0, EPGConfigs.server().factoryLightProjectionRange.get(), 0);
    }

    /** 蓝图需求：安装了未烧毁的灯泡时需额外消耗该灯泡。 */
    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        if (hasBulb() && !burned)
            return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, bulbItem);
        return ItemRequirement.NONE;
    }

    /** 序列化：灯泡/温度/烧毁/颜色 + 单次特效标记。 */
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (hasBulb())
            tag.putString("Bulb", BuiltInRegistries.ITEM.getKey(bulbItem).toString());
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Burned", burned);
        if (color != null)
            tag.putInt("Color", color.getId());
        if (playEffect) {
            tag.putBoolean("Effect", true);
            playEffect = false;
        }
    }

    /** 安全序列化（蓝图保存）：不含临时特效标记。 */
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        if (hasBulb())
            tag.putString("Bulb", BuiltInRegistries.ITEM.getKey(bulbItem).toString());
        tag.putFloat("Temperature", temperature);
        tag.putBoolean("Burned", burned);
        if (color != null)
            tag.putInt("Color", color.getId());
    }

    /** 反序列化：恢复灯泡/温度/烧毁/颜色与特效标记。 */
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Bulb"))
            bulbItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Bulb")));
        else
            bulbItem = Items.AIR;
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : FactoryLightDevice.AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        color = tag.contains("Color") ? DyeColor.byId(tag.getInt("Color")) : null;
        playEffect = tag.getBoolean("Effect");
    }
}
