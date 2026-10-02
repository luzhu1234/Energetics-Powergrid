package com.energeticspowergrid.content.fixture;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 灯具（Light Fixture）方块实体。
 * <p>
 * 保存灯具的“显示侧”状态：安装的灯泡物品、灯丝温度、是否烧毁、染色颜色。
 * 本体不参与电路计算——真正的电气模拟在服务端全局的 {@link LightFixtureDevice} 中进行，
 * 设备每 tick 通过 {@link #applyFromDevice} 把温度/烧毁/颜色等数据同步过来，
 * 方块实体再把状态暴露给渲染器与 NBT 存储。
 * <p>
 * 灯泡透明度（发光贴图 alpha）由温度和亮度档位共同决定。
 */
public class LightFixtureBlockEntity extends SmartBlockEntity {
    /** 当前安装的灯泡物品；无灯泡时为 Items.AIR。 */
    private Item bulbItem = Items.AIR;
    /** 灯丝温度（°C），由模拟设备同步；环境温度为 22°C。 */
    private float temperature = LightFixtureDevice.AMBIENT_TEMPERATURE;
    /** 灯泡是否已因过压/过热烧毁。 */
    private boolean burned;
    /** 染色颜色；仅可染色灯泡有效，null 表示未染色。 */
    @Nullable
    private DyeColor color;
    /** 标记：需要客户端播放闪光粒子（烧毁瞬间效果）。 */
    private boolean playEffect;

    public LightFixtureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 无附加行为（如 Bailey 式仓储/机械臂交互等）。 */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /**
     * 获取服务端侧与本方块关联的模拟设备实例。
     * 设备数据保存在世界级 SavedData（DevicesSavedData）中，而非方块实体 NBT。
     */
    @Nullable
    public LightFixtureDevice getDevice() {
        if (!(level instanceof ServerLevel serverLevel))
            return null;
        return DevicesSavedData.load(serverLevel).getDevice(worldPosition, LightFixtureDevice.class);
    }

    /** 是否安装了有效灯泡（物品必须实现 ILightBulb）。 */
    public boolean hasBulb() {
        return bulbItem != null && bulbItem != Items.AIR && bulbItem instanceof ILightBulb;
    }

    /** 获取灯泡物品，空位时返回 AIR 而非 null。 */
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

    /** 获取灯丝温度（°C）。 */
    public float getTemperature() {
        return temperature;
    }

    /**
     * 计算发光贴图的透明度 alpha（0~1），供渲染器使用。
     * <p>
     * x 为归一化温度：600°C 起 <2span style="margin:0">步，1400°C 达到 1。
     * <ul>
     *   <li>亮度档 2（满亮度）→ 直接不透明（alpha = 1）；</li>
     *   <li>亮度档 1（低亮度）→ 取平方曲线与 0.5625 的较大值，保证微光可见；</li>
     *   <li>熄灭 → 仅按温度余晖显示（平方曲线），温度冷却后逐渐变暗。</li>
     * </ul>
     */
    public float getAlpha() {
        float x = Mth.clamp((temperature - 600f) / (1400f - 600f), 0, 1);
        int powerLevel = getBlockState().hasProperty(LightFixtureBlock.POWER) ? getBlockState().getValue(LightFixtureBlock.POWER) : 0;
        if (powerLevel == 2)
            return 1;
        if (powerLevel == 1)
            return Math.max(0.5625f, x * x);
        return x * x;
    }

    /** 复制一份灯泡物品堆，用于掉落物；无灯泡或已烧毁时返回空堆。 */
    public ItemStack copyBulbStack() {
        if (!hasBulb() || burned)
            return ItemStack.EMPTY;
        return new ItemStack(bulbItem);
    }

    /**
     * 由模拟设备调用：整体覆盖本地状态。
     * 仅当灯泡/烧毁/颜色发生变化或温度波动超过 8°C 时才向客户端发包，避免网络风暴；
     * force = true 时强制发包（例如换灯泡后必须立即同步）。
     */
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

    /**
     * 换灯泡入口（由方块交互触发）。
     * 成功后在服务端把新灯泡/颜色同步到模拟设备（设备的电阻取决于灯泡类型），
     * 并 notifyUpdate 强制客户端刷新。
     */
    public boolean replaceBulb(Player player, InteractionHand hand, ItemStack usedStack) {
        if (level == null)
            return false;
        boolean result = replaceBulbInternal(player, hand, usedStack);
        if (result && !level.isClientSide) {
            LightFixtureDevice device = getDevice();
            if (device != null)
                device.setInstalledBulb(hasBulb() ? bulbItem : Items.AIR, color);
            notifyUpdate();
        }
        return result;
    }

    /**
     * 换灯泡的具体逻辑（服务端实际执行，客户端预演）：
     * <ul>
     *   <li>usedStack 为空 → 拧下灯泡放回玩家手中（烧毁的灯泡直接丢弃）；</li>
     *   <li>原本无灯泡 → 安装新灯泡；若副手拿着染料且灯泡可染色，则同时染色；</li>
     *   <li>已烧毁 → 右键清除烧毁的灯泡；</li>
     *   <li>手持相同灯泡 → 取出灯泡并入堆；</li>
     *   <li>创造模式 → 直接取下灯泡。</li>
     * </ul>
     */
    private boolean replaceBulbInternal(Player player, InteractionHand hand, ItemStack usedStack) {
        // 情况一：空手（或空堆）——取出灯泡
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
        // 情况二：原本无灯泡——安装新灯泡（副手染料可同时染色）
        if (!hasBulb()) {
            if (!level.isClientSide && usedStack.getItem() instanceof ILightBulb) {
                bulbItem = usedStack.getItem();
                burned = false;
                temperature = LightFixtureDevice.AMBIENT_TEMPERATURE;
                color = null;
                if (((ILightBulb) bulbItem).thermalProperties() != null && ((ILightBulb) bulbItem).thermalProperties().dyeable()
                        && player.getOffhandItem().getItem() instanceof DyeItem dye)
                    color = dye.getDyeColor();
                if (!player.isCreative())
                    usedStack.shrink(1);
            }
            return true;
        }
        // 情况三：已烧毁——右键直接清除
        if (burned) {
            if (!level.isClientSide)
                clearBulb();
            return true;
        }
        // 情况四：手持同种灯泡——取出并并入玩家手中堆叠
        if (bulbItem == usedStack.getItem() && usedStack.getCount() < usedStack.getMaxStackSize()) {
            if (!level.isClientSide) {
                if (!player.isCreative())
                    usedStack.grow(1);
                clearBulb();
            }
            return true;
        }
        // 情况五：创造模式——直接取下
        if (player.isCreative()) {
            if (!level.isClientSide)
                clearBulb();
            return true;
        }
        return false;
    }

    /** 清空灯泡状态：重置为无灯泡、未烧毁、未染色、环境温度。 */
    private void clearBulb() {
        bulbItem = Items.AIR;
        burned = false;
        color = null;
        temperature = LightFixtureDevice.AMBIENT_TEMPERATURE;
    }

    /**
     * 染色：仅对已安装且可染色（ThermalProperties.dyeable()）的灯泡生效。
     * 服务端同时把颜色同步到模拟设备，保证重载/重启后颜色一致。
     */
    public ItemInteractionResult setColor(DyeColor color) {
        if (!hasBulb())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ILightBulb.ThermalProperties properties = ((ILightBulb) bulbItem).thermalProperties();
        if (properties == null || !properties.dyeable())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        this.color = color;
        if (!level.isClientSide) {
            LightFixtureDevice device = getDevice();
            if (device != null)
                device.trySetColor(color);
            notifyUpdate();
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * 每 tick 逻辑（很轻量）：
     * <ul>
     *   <li>客户端：若 playEffect 置位（灯泡烧毁事件同步过来），在方块中心播放闪光粒子；</li>
     *   <li>服务端：把自身引用挂到模拟设备上（device.be），供设备免查找地推送数据。</li>
     * </ul>
     */
    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide && playEffect) {
            var center = worldPosition.getCenter();
            level.addParticle(ParticleTypes.FLASH, center.x, center.y, center.z, 0, 0, 0);
            playEffect = false;
        }
        if (level instanceof ServerLevel && getDevice() instanceof LightFixtureDevice device)
            device.be = this;
    }

    /** 渲染包围盒限制为本方块一格，避免灯具发光贴图参与过大范围的剔除计算。 */
    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition);
    }

    /** 蓝图（Schematicannon）需求：若安装了未烧毁的灯泡，打印时需额外消耗该灯泡。 */
    @Override
    public ItemRequirement getRequiredItems(BlockState state) {
        if (hasBulb() && !burned)
            return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, bulbItem);
        return ItemRequirement.NONE;
    }

    /** 序列化：灯泡注册名、温度、烧毁标记、颜色；playEffect 只在本次客户端包中传递一次。 */
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

    /** 安全序列化（蓝图保存用）：不包含 playEffect 等临时状态。 */
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

    /** 反序列化：恢复灯泡、温度、烧毁、颜色与特效标记。 */
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Bulb"))
            bulbItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Bulb")));
        else
            bulbItem = Items.AIR;
        temperature = tag.contains("Temperature") ? tag.getFloat("Temperature") : LightFixtureDevice.AMBIENT_TEMPERATURE;
        burned = tag.getBoolean("Burned");
        color = tag.contains("Color") ? DyeColor.byId(tag.getInt("Color")) : null;
        playEffect = tag.getBoolean("Effect");
    }
}
