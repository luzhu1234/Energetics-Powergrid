package com.energeticspowergrid.content.excitation.mixin;

import com.energeticspowergrid.config.EPGConfigs;
import com.energeticspowergrid.content.excitation.EPGRotorFractional;
import com.energeticspowergrid.content.excitation.ExcitationFields;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetwork;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 重写转子所携带的磁铁数量，使其成为"有效磁铁数"——由统一励磁池推导而来：
 * 原版定子贡献固定场强（默认 100），励磁定子贡献其实时场强，
 * 1000 场强等价于一台原版定子的磁铁量。
 * <p>
 * 取整后的 {@code magnets} 字段仍用于存档与客户端同步；碳刷功率与动力网络应力
 * 则通过鸭子接口 {@link EPGRotorFractional} 读取未取整的连续值——否则功率会以
 * 一整块磁铁的输出（640rpm 下约 30kW）为台阶跳变，微调励磁完全无效。
 * <p>
 * 客户端绝不重新扫描：它只能看见原版定子，会把励磁份额清掉（励磁保存在
 * 服务端设备数据里），因此客户端只依赖服务端在数值变化时推送的同步值。
 * 这样两侧在相同数据上运行完全相同的原版公式。
 */
@Mixin(AlternatorRotorBlockEntity.class)
public abstract class AlternatorRotorBlockEntityMixin implements EPGRotorFractional {
    /**
     * 原版每台定子给转子加 3 块磁铁，所以用该比率即可让 1000 场强等价于一台定子。
     */
    @Unique
    private static final double EPG$MAGNETS_PER_STATOR = 3;

    /**
     * 未取整的有效磁铁数（-1 = 尚未计算，碳刷侧应回退到 int magnets）。
     * int {@code magnets} 只用于存档与客户端同步；功率与应力的连续化
     * 全靠这个浮点值，否则功率会以一整块磁铁的输出为台阶跳变。
     */
    @Unique
    private float epg$fractionalMagnets = -1;

    @Override
    public float epg$getFractionalMagnets() {
        return epg$fractionalMagnets;
    }

    @Override
    public int epg$getMagnets() {
        return magnets;
    }

    /**
     * 注入目标持有的真实磁铁数字段。写入它（而不是旁路记录）是整个设计的关键：
     * 应力、发电、NBT、客户端同步都基于这个字段，天然保持一致。
     */
    @Shadow(remap = false)
    int magnets;

    /**
     * 为什么在 HEAD 注入并取消原方法：原版 lazyTick 会按"数到几台原版定子"重算磁铁数，
     * 会把励磁定子的贡献整个覆盖掉。直接替换整个实现，用统一励磁池重新推导。
     */
    @Inject(method = "lazyTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void epg$excitationLazyTick(CallbackInfo ci) {
        ci.cancel();
        AlternatorRotorBlockEntity self = (AlternatorRotorBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel))
            return; // 客户端：保留服务端同步来的数值，绝不能用"只含原版定子"的扫描结果覆盖它

        // 汇总四周槽位的带符号励磁和（励磁定子实时值 + 原版定子固定值）
        double sum = ExcitationFields.excitationSum(serverLevel, self.getBlockPos(), self.getBlockState());
        // 折算有效磁铁数：B = I·U·y 的场强先取模（方向只影响极性，不影响拉力），
        // 再按配置的"每台定子等效场强"换算成磁铁数
        double effectiveMagnets = EPG$MAGNETS_PER_STATOR
                * Math.abs(sum)
                / Math.max(1, EPGConfigs.server().excitationFieldPerStator.getF());

        // 记录未取整的连续值（供碳刷功率与下方应力计算使用）。
        // 即使 int 部分没变，小数部分的变化也要反映到应力上，否则功率仍是台阶。
        float previousFractional = epg$fractionalMagnets;
        epg$fractionalMagnets = (float) effectiveMagnets;

        // 防溢出钳制：场强极端时限制在 int 上限的一半，避免后续应力计算溢出
        int magnetCount = effectiveMagnets >= Integer.MAX_VALUE / 2d
                ? Integer.MAX_VALUE / 2
                : (int) Math.max(0, Math.round(effectiveMagnets));
        // int 磁铁数与小数部分都没有变化时才什么都不做，避免网络抖动
        boolean magnetsChanged = this.magnets != magnetCount;
        boolean fractionalChanged = Math.abs(previousFractional - epg$fractionalMagnets) > 1e-4f;
        if (!magnetsChanged && !fractionalChanged)
            return;

        this.magnets = magnetCount;
        if (self.hasNetwork()) {
            // 磁铁数或小数部分变化都意味着应力贡献变化，通知动力网络重算
            // （calculateStressApplied 已被注入为连续版本，返回值即小数应力）
            self.getOrCreateNetwork().updateStressFor(self, self.calculateStressApplied());
            // 原版也是在应力更新的同时发送数据包，这样客户端就不会在服务端
            // 认为未过载时误以为转子已过载（两侧数据保持一致）
            self.sendData();
        }
    }

    /**
     * 机械侧应力同样连续化：原版按 {@code (int 磁铁数 + 0.125) × 倍率} 计算，
     * 应力会以一整块磁铁的量跳变。改用小数有效磁铁数，使动力网络的应力消耗
     * 与碳刷的电功率使用同一份连续值，两侧能量收支保持一致。
     * <p>
     * {@code lastStressApplied} 是父类 KineticBlockEntity 的 protected 字段，
     * @Shadow 无法作用于继承成员，改经 {@link KineticBlockEntityAccessor} 写入
     * ——Create 在网络重建时会用这个字段重放应力，必须与返回值保持一致。
     */
    @Inject(method = "calculateStressApplied", at = @At("HEAD"), cancellable = true, remap = false)
    private void epg$fractionalStress(CallbackInfoReturnable<Float> cir) {
        com.george_vi.electroenergetics.config.CRotor rotorConfig = com.george_vi.electroenergetics.config.CEEConfigs.server().rotorValues;
        // 未初始化（-1，区块刚加载、首次 lazyTick 之前）回退到 int 磁铁数——
        // 否则 (-1 + 0.125) × 48 = -42 的负应力会出现在动力网络上
        float magnets = epg$fractionalMagnets >= 0 ? epg$fractionalMagnets : this.magnets;
        float impact = (magnets + 0.125f)
                * rotorConfig.rotorPowerMultiplier.getF()
                * rotorConfig.rotorStressMultiplier.getF();
        ((KineticBlockEntityAccessor) (Object) this).epg$setLastStressApplied(impact);
        cir.setReturnValue(impact);
    }

    /**
     * 把连续磁铁数同步到客户端：write 时追加自定义 NBT 标签，read 时还原。
     * 客户端的护目镜读数与 overstress 判断都依赖这个值——客户端的 lazyTick
     * 不做励磁扫描（会丢掉励磁份额），全靠服务端同步。
     */
    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void epg$writeFractionalMagnets(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        tag.putFloat("EPG$FractionalMagnets", epg$fractionalMagnets);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void epg$readFractionalMagnets(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        // 只在标签存在时还原；旧存档没有该键，保持 -1（未初始化）由首次 lazyTick 补上
        if (tag.contains("EPG$FractionalMagnets"))
            epg$fractionalMagnets = tag.getFloat("EPG$FractionalMagnets");
    }
}
