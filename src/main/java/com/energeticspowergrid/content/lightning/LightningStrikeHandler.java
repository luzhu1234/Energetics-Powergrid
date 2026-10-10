package com.energeticspowergrid.content.lightning;

import com.energeticspowergrid.config.EPGConfigs;
import com.george_vi.electroenergetics.CEEDamageTypes;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.events.FinishElectricSimulationEvent;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.NortonProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雷击处理器：雷暴天气时对电气网络模拟真实落雷——拟真破坏链
 * "冲击过电压 → 空气闪络 → 工频续流电弧"。
 * <p>
 * <b>物理对应</b>：现实雷电持续几十微秒，焦耳热远不足以烧线；真正的破坏是
 * 冲击过电压击穿空气（闪络）后，电网自身的工频短路电流经电弧通道持续燃烧
 * （工频续流），直到继保护切除。本处理器按同一因果链建模：
 * <ol>
 * <li><b>冲击段</b>（默认 2 刻）：被击节点注入 Norton 电流源（0.1Ω 通道电阻），
 *     电压上限 ≈ 电流 × 0.1Ω。2 刻积温低于最低线材阈值——雷电流本身不烧线；</li>
 * <li><b>闪络判定</b>：在周围搜索放电目标并取<b>击穿电压要求最低</b>的路径——
 *     优先电气设备（相间短路，最猛），其次任意固体方块/垂直下方 earth，最后兜底阈值；
 *     击穿前空气是绝缘体（开路），只有"场强阈值"没有电阻——对应现实击穿场强；</li>
 * <li><b>电弧段（工频续流）</b>：电弧用<b>恒压降模型</b>（压降 = arcVoltsPerBlock ×
 *     弧长，与电流无关，对应现实电弧的负伏安特性）——以 Norton 等效恒压源实现
 *     （I_n = V_arc / 0.1Ω）。电源电压撑不起弧压降时电流归零、电弧自然熄灭
 *     （拉长熄弧原理）。续流经 CEE 原生导线积温烧线，继保天然参与；</li>
 * <li><b>电弧伤生</b>：电弧存在期间，附近所有生物（含玩家）会被额外电弧连接——
 *     身体支路（CEE 同款：间隙电阻 + 身体 10Ω + 接地接触 2333Ω）按被击点电压计算通过
 *     身体的电流，超过 CEE 的安全阈值即以 CEE 原版电击伤害类型与公式造成伤害
 *     （高压时 HV_ELECTROCUTION，伤害 = V/3000）。电弧无定时器（与 CEE 高压开关
 *     同逻辑）：只要电源撑得起弧压降就一直燃烧，撑不住或处于不稳定区随机抖动时熄灭。</li>
 * </ol>
 * <p>
 * 命中规则：只在已加载区块内、且至少有一根导线连接的电气节点中选 Y 最高者。
 * 电流符号：CEE 求解器 {@code rhs[node1] += I}，注入负值 = 从被击节点抽电流入地。
 */
public class LightningStrikeHandler {

    /** 雷电通道等效电阻（欧姆）：同时充当恒压降 Norton 等效的串联电阻 */
    private static final double STRIKE_RESISTANCE = 0.1;
    /** 对地扫描下探深度上限（格） */
    private static final int EARTH_SCAN_DEPTH = 128;

    // ---- 人体电击支路参数（与 CEE WireElectrocutionModule 同款，保证"走电力学本体的电击"） ----
    /** 人体身体电阻（欧姆） */
    private static final double BODY_RESISTANCE = 10;
    /** 人脚-大地接触电阻（欧姆） */
    private static final double EARTH_CONTACT_RESISTANCE = 2333;
    /** 空气间隙电阻基数（欧姆）——CEE 电击模块同款：间隙电阻 = 1444 + 距离²×1000 */
    private static final double AIR_GAP_BASE = 1444;
    private static final double AIR_GAP_PER_DISTANCE_SQR = 1000;
    /** 触电伤害的电流门槛（安培），低于此值不造成伤害 */
    private static final double MIN_ELECTROCUTION_CURRENT = 0.06;
    /** 高压电击判定线（伏）：超过则使用 HV_ELECTROCUTION 伤害类型与高压伤害公式 */
    private static final double HV_DAMAGE_VOLTAGE = 9900;
    /** 高压伤害公式的分段线（伏）：超过则伤害 = 电压/3000 */
    private static final double HV_DAMAGE_FORMULA_VOLTAGE = 14000;

    /** 雷击所处阶段：冲击过电压 / 工频续流电弧 */
    private enum Phase { SURGE, ARC }

    /** 电弧放电目标类型 */
    private enum TargetType {
        /** 周围的电气设备：相间/网络间短路，导体对导体电弧 */
        DEVICE,
        /** 周围固体方块或垂直下方大地：对抽象地放电 */
        GROUND,
        /** 无任何目标的兜底球形放电 */
        SPHERE
    }

    /**
     * 一个候选放电目标。
     *
     * @param type       目标类型
     * @param deviceNode 目标为电气设备时的对端节点（其余为 null）
     * @param pos        目标位置（粒子表现用）
     * @param threshold  该路径的闪络电压阈值（V）
     * @param arcDrop    该路径电弧的恒定压降（V）= arcVoltsPerBlock × 距离
     */
    private record DischargeTarget(TargetType type, InWorldNode deviceNode, BlockPos pos,
                                   double threshold, double arcDrop) {
    }

    /**
     * 一次雷击的运行时状态。
     *
     * @param node      被击电气节点
     * @param strikePos 被击方块位置（粒子原点）
     * @param target    选定的放电目标（击穿要求最低的路径）
     * @param phase     当前阶段
     * @param ticksLeft 当前阶段剩余刻数（int 数组包装以便原地修改）
     */
    private record ActiveStrike(InWorldNode node, BlockPos strikePos, DischargeTarget target,
                                Phase phase, int[] ticksLeft) {
    }

    /** 进行中的雷击：按维度记录。ConcurrentHashMap 保证主线程与求解线程访问安全 */
    private static final Map<ResourceKey<Level>, ActiveStrike> ACTIVE_STRIKES = new ConcurrentHashMap<>();

    /** 缓存的 CEE 电击伤害源（每次解算完成时懒加载） */
    private static DamageSource electrocutionSource;
    private static DamageSource hvElectrocutionSource;

    /**
     * 把本类的游戏事件监听器注册到 NeoForge 游戏总线，由模组主类构造时调用。
     */
    public static void register() {
        NeoForge.EVENT_BUS.register(LightningStrikeHandler.class);
    }

    /**
     * 每个服务端维度刻末：触发落雷判定 / 电弧防呆上限计数 / 播放电弧粒子。
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        EPGConfigs.CServer config = EPGConfigs.server();
        if (config == null || !config.enableLightningStrikes.get())
            return;

        if (!(event.getLevel() instanceof ServerLevel level))
            return;

        // 非雷暴天不触发新的落雷。已建立的电弧不随雷暴结束而消失——
        // 现实中电弧一旦建立就与雷电无关，靠网络电源的工频续流维持，直到自然熄弧
        if (!level.isThundering()) {
            ActiveStrike existing = ACTIVE_STRIKES.get(level.dimension());
            if (existing == null || existing.phase() == Phase.SURGE)
                ACTIVE_STRIKES.remove(level.dimension());
            return;
        }

        ActiveStrike strike = ACTIVE_STRIKES.get(level.dimension());
        if (strike != null) {
            if (strike.phase() == Phase.ARC) {
                spawnArcParticles(level, strike);
                // 电弧无定时器（与 CEE 高压开关同逻辑）：寿命只由熄弧物理判定决定。
                // arcDurationTicks 仅作防呆保险上限，0 = 无上限
                int cap = config.arcDurationTicks.get();
                if (cap > 0 && ++strike.ticksLeft()[0] >= cap)
                    ACTIVE_STRIKES.remove(level.dimension());
            } else {
                // 冲击阶段结束仍未闪络：撤除冲击，网络扛过这次雷击
                if (--strike.ticksLeft()[0] <= 0)
                    ACTIVE_STRIKES.remove(level.dimension());
            }
            return;
        }

        // 按平均间隔随机触发一次落雷
        int interval = config.lightningStrikeIntervalTicks.get();
        if (level.random.nextInt(interval) != 0)
            return;

        InWorldNode targetNode = findHighestNode(level);
        if (targetNode == null)
            return;

        // 搜索放电目标，取击穿要求最低的路径
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        DischargeTarget target = findDischargeTarget(level, sd, targetNode, config);

        ACTIVE_STRIKES.put(level.dimension(), new ActiveStrike(targetNode, targetNode.sourcePos(),
                target, Phase.SURGE, new int[] {config.lightningStrikeDurationTicks.get()}));

        // 原版闪电实体仅作视觉与音效表现（引燃/伤害是原版自身行为）
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setPos(Vec3.atCenterOf(targetNode.sourcePos()));
            level.addFreshEntity(bolt);
        }
    }

    /**
     * 电路建立阶段（每模拟刻一次）：
     * <ul>
     * <li>冲击阶段——被击节点经 Norton（0.1Ω ∥ −I 电流源）接到虚拟地，
     *     把被击点电位顶向负高压；</li>
     * <li>电弧阶段——按目标类型建立恒压降电弧：
     *     设备目标为导体对导体电弧（相间/网络间短路），其余目标对抽象地放电。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onAddToElectricGraph(AddToElectricGraphEvent event) {
        EPGConfigs.CServer config = EPGConfigs.server();
        if (config == null || !config.enableLightningStrikes.get())
            return;

        ActiveStrike strike = ACTIVE_STRIKES.get(event.level.dimension());
        if (strike == null)
            return;

        if (strike.phase() == Phase.SURGE) {
            // 冲击段：负电流源 = 从被击节点抽电流入地（负极性雷）
            AttachedNode groundNode = new AttachedNode(-1, "epg_lightning_ground");
            event.builder.connect(strike.node(), groundNode,
                    new NortonProperties(STRIKE_RESISTANCE, -config.lightningImpulseCurrent.get()));
            event.builder.ground(groundNode, 1000);
            return;
        }

        // 电弧段：恒压降模型的 Norton 等效——I_n = V_arc / R_s
        // （注入正电流到节点 1 一侧，使该侧电位高出对侧 V_arc，即电弧压降）
        DischargeTarget target = strike.target();
        double sourceAmps = target.arcDrop() / STRIKE_RESISTANCE;
        if (target.type() == TargetType.DEVICE && target.deviceNode() != null) {
            // 导体对导体电弧：被击节点为高电位侧
            event.builder.connect(strike.node(), target.deviceNode(),
                    new NortonProperties(STRIKE_RESISTANCE, sourceAmps));
        } else {
            AttachedNode groundNode = new AttachedNode(-1, "epg_lightning_ground");
            event.builder.connect(strike.node(), groundNode,
                    new NortonProperties(STRIKE_RESISTANCE, sourceAmps));
            event.builder.ground(groundNode, 1000);
        }
    }

    /**
     * 解算完成回调：
     * <ul>
     * <li>冲击阶段——闪络判定：|V| ≥ 闪络阈值即转入电弧阶段；</li>
     * <li>电弧阶段——熄弧判定（被击点电压撑不起弧压降 → 熄灭；不稳定区 2%/刻 随机熄弧）+
     *     附近生物电击（CEE 伤害类型与公式）。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onFinishElectricSimulation(FinishElectricSimulationEvent event) {
        EPGConfigs.CServer config = EPGConfigs.server();
        if (config == null || !config.enableLightningStrikes.get())
            return;

        ActiveStrike strike = ACTIVE_STRIKES.get(event.level.dimension());
        if (strike == null)
            return;

        ServerLevel level = event.level;
        SimulationResults results = event.results;
        double voltage;
        try {
            voltage = Math.abs(results.getVoltageAt(strike.node()));
        } catch (Exception e) {
            // 被击节点已不在电路中（线被烧断等）：电弧失去落点，直接移除
            ACTIVE_STRIKES.remove(level.dimension());
            return;
        }

        if (strike.phase() == Phase.SURGE) {
            if (voltage < strike.target().threshold())
                return; // 未闪络：网络扛过冲击

            // 闪络！进入工频续流阶段（计数器改作防呆上限的递增计数，从 0 开始）
            strike.ticksLeft()[0] = 0;
            ACTIVE_STRIKES.put(level.dimension(), new ActiveStrike(strike.node(), strike.strikePos(),
                    strike.target(), Phase.ARC, strike.ticksLeft()));
            return;
        }

        // ---- 电弧阶段 ----
        double arcDrop = strike.target().arcDrop();
        if (voltage < arcDrop * 0.9) {
            // 被击点电位撑不起弧压降：电弧熄灭（拉长熄弧）
            ACTIVE_STRIKES.remove(level.dimension());
            return;
        }
        // 电弧不稳定区（借鉴 CEE 高压开关的 2%/刻 随机熄弧）：电压仅略高于弧压降时，
        // 电弧每刻有 2% 概率提前熄灭——"将熄未熄"的闪烁挣扎，而非精确卡阈值突然消失
        if (voltage < arcDrop * 3 && level.random.nextFloat() > 0.98) {
            ACTIVE_STRIKES.remove(level.dimension());
            return;
        }

        electrocuteNearbyEntities(level, strike, results, voltage);
    }

    /**
     * 电弧伤生：对电弧产生源附近（放电搜索半径内）的所有生物建立电弧连接——
     * 按 CEE 同款人体支路（间隙电阻/10 + 身体 10Ω + 接地接触 2333Ω）计算通过
     * 身体的电流，超过 CEE 安全阈值（0.06A）即以 CEE 原版电击伤害类型与公式造成伤害。
     * 玩家在创造/旁观模式下豁免，其余生物（动物/怪物/村民等）与人体电学等效、同样触电。
     */
    private static void electrocuteNearbyEntities(ServerLevel level, ActiveStrike strike,
                                                  SimulationResults results, double voltage) {
        EPGConfigs.CServer config = EPGConfigs.server();
        double radius = config.dischargeSearchRadius.get();
        Vec3 origin = Vec3.atCenterOf(strike.strikePos());

        AABB box = AABB.ofSize(origin, radius * 2, radius * 2, radius * 2);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity instanceof ServerPlayer player
                    && (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                    || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR))
                continue;
            if (!entity.isAlive())
                continue;

            double distance = entity.position().distanceTo(origin);
            if (distance > radius)
                continue;

            // 生物身体支路总电阻：电弧间隙（空气电阻的 1/10）+ 身体 + 接地接触
            double gapResistance = (AIR_GAP_BASE + distance * distance * AIR_GAP_PER_DISTANCE_SQR) / 10;
            double bodyPathResistance = gapResistance + BODY_RESISTANCE + EARTH_CONTACT_RESISTANCE;
            double current = voltage / bodyPathResistance;
            if (current <= MIN_ELECTROCUTION_CURRENT)
                continue;

            // CEE 原版伤害公式：低压按电流对数+线性，高压按电压/3000
            float damage = voltage < HV_DAMAGE_FORMULA_VOLTAGE
                    ? (float) (Math.log(current * 10.5) * 1.9 + 3.6 + 4.84 * current)
                    : (float) (voltage / 3000);
            DamageSource source = getDamageSource(level, voltage > HV_DAMAGE_VOLTAGE);
            entity.hurt(source, damage);
        }
    }

    /** 懒加载 CEE 电击伤害源（普通电击 / 高压电击） */
    private static DamageSource getDamageSource(ServerLevel level, boolean hv) {
        Registry<DamageType> registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        if (hv) {
            if (hvElectrocutionSource == null)
                hvElectrocutionSource = new DamageSource(registry.getHolderOrThrow(CEEDamageTypes.HV_ELECTROCUTION));
            return hvElectrocutionSource;
        }
        if (electrocutionSource == null)
            electrocutionSource = new DamageSource(registry.getHolderOrThrow(CEEDamageTypes.ELECTROCUTION));
        return electrocutionSource;
    }

    /**
     * 服务器完全停止时清空所有维度的雷击状态，防止跨存档残留。
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_STRIKES.clear();
    }

    /**
     * 在已加载区块中寻找 Y 坐标最高、且至少接有一根导线的电气节点。
     * 孤立节点（未接线的端子）被击毫无影响，直接跳过。
     *
     * @return Y 最高的已连接节点；没有符合条件者返回 null（空过）
     */
    private static InWorldNode findHighestNode(ServerLevel level) {
        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        InWorldNode best = null;
        int bestY = Integer.MIN_VALUE;
        for (InWorldNode node : sd.getNodes()) {
            BlockPos pos = node.sourcePos();
            if (!level.isLoaded(pos))
                continue; // 只在已加载区块内寻找
            if (sd.getConnections(node).isEmpty())
                continue; // 跳过无导线连接的孤立节点
            if (pos.getY() > bestY) {
                bestY = pos.getY();
                best = node;
            }
        }
        return best;
    }

    /**
     * 搜索放电目标：取击穿电压要求最低的路径（电弧永远走最容易击穿的路）。
     * <ol>
     * <li>优先级 1——放电搜索半径内的<b>电气设备</b>（有节点的方块）：
     *     导体对导体闪络（相间/网络间短路），现实中破坏力最大的故障；</li>
     * <li>优先级 2——半径内<b>任意固体方块</b>（就近表面放电）或<b>垂直下方 earth</b>
     *     （CEE earth 标签，与接地棒同源判定），二者取阈值较低者；</li>
     * <li>兜底——什么目标都没有（纯浮空）：使用 flashoverFallbackVolts 球形放电。</li>
     * </ol>
     * 所有阈值统一为 {@code flashoverVoltsPerBlock × 距离}，击穿前空气是绝缘体，
     * 只按场强阈值判定，不引入任何"空气电阻"。
     */
    private static DischargeTarget findDischargeTarget(ServerLevel level, InfrastructureSavedData sd,
                                                       InWorldNode struckNode, EPGConfigs.CServer config) {
        int radius = config.dischargeSearchRadius.get();
        double perBlock = config.flashoverVoltsPerBlock.get();
        double arcPerBlock = config.arcVoltsPerBlock.get();
        BlockPos origin = struckNode.sourcePos();
        Vec3 originCenter = Vec3.atCenterOf(origin);

        DischargeTarget bestDevice = null;
        DischargeTarget bestBlock = null;

        // 半径立方体扫描：最近的电气设备 / 最近的固体方块
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (pos.equals(origin))
                continue;
            double distance = Vec3.atCenterOf(pos).distanceTo(originCenter);
            if (distance <= 0)
                continue;

            var nodesAt = sd.getNodesAt(pos.immutable());
            if (!nodesAt.isEmpty() && bestDevice == null) {
                InWorldNode deviceNode = nodesAt.get(0).node;
                // 跳过未接线的空端子：打到孤立端子上形不成短路回路，与落雷选点同规则
                if (sd.getConnections(deviceNode).isEmpty())
                    continue;
                bestDevice = new DischargeTarget(TargetType.DEVICE, deviceNode, pos.immutable(),
                        perBlock * distance, arcPerBlock * distance);
            } else if (nodesAt.isEmpty() && bestBlock == null) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir())
                    bestBlock = new DischargeTarget(TargetType.GROUND, null, pos.immutable(),
                            perBlock * distance, arcPerBlock * distance);
            }
            if (bestDevice != null && bestBlock != null)
                break; // 都找到了，最近的即最优
        }

        // 用户约定的优先级：有电气设备优先（相间短路）；否则在"就近方块"与"垂直大地"间取阈值较低者
        DischargeTarget earth = findVerticalEarth(level, origin, perBlock, arcPerBlock);
        if (bestDevice != null)
            return bestDevice;
        if (bestBlock != null && (earth == null || bestBlock.threshold() <= earth.threshold()))
            return bestBlock;
        if (earth != null)
            return earth;

        // 兜底：球形放电
        return new DischargeTarget(TargetType.SPHERE, null, origin.below(),
                config.flashoverFallbackVolts.get(),
                arcPerBlock * 3);
    }

    /**
     * 从被击点垂直向下扫描最近的大地方块（CEE earth 标签）。
     *
     * @return 目标（含垂直距离阈值）；扫描深度内没有则返回 null
     */
    private static DischargeTarget findVerticalEarth(ServerLevel level, BlockPos origin,
                                                     double perBlock, double arcPerBlock) {
        int bottom = level.getMinBuildHeight();
        for (int y = origin.getY() - 1; y >= bottom && origin.getY() - y <= EARTH_SCAN_DEPTH; y--) {
            BlockPos p = origin.atY(y);
            if (level.getBlockState(p).is(CEETags.EARTH)) {
                double distance = Math.max(origin.getY() - y, 1);
                return new DischargeTarget(TargetType.GROUND, null, p, perBlock * distance, arcPerBlock * distance);
            }
        }
        return null;
    }

    /**
     * 电弧粒子表现：被击点 → 目标点的电弧线 + 目标点球形火花云（就近不规则击穿）；
     * 若附近有玩家，附加被击点 → 玩家的斜向电弧（对应正在触电的放电通路）。
     */
    private static void spawnArcParticles(ServerLevel level, ActiveStrike strike) {
        Vec3 origin = Vec3.atCenterOf(strike.strikePos());
        DischargeTarget target = strike.target();
        Vec3 targetPoint = Vec3.atCenterOf(target.pos());

        // 主电弧：被击点 → 目标点的线段
        spawnSparkLine(level, origin, targetPoint, 6);
        // 目标点球形火花云
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                targetPoint.x, targetPoint.y, targetPoint.z, 8, 0.5, 0.5, 0.5, 0.2);
        level.sendParticles(ParticleTypes.SMOKE, targetPoint.x, targetPoint.y, targetPoint.z, 3, 0.2, 0.1, 0.2, 0.02);
        // 附着点烟
        level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 2, 0.2, 0.1, 0.2, 0.02);

        // 生物电弧：被击点 → 附近每个生物（对应正在触电的放电通路）
        double radius = EPGConfigs.server().dischargeSearchRadius.get();
        AABB box = AABB.ofSize(origin, radius * 2, radius * 2, radius * 2);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity instanceof ServerPlayer player
                    && (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                    || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR))
                continue;
            if (!entity.isAlive() || entity.position().distanceTo(origin) > radius)
                continue;
            Vec3 hit = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            spawnSparkLine(level, origin, hit, 4);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    hit.x, hit.y, hit.z, 4, 0.3, entity.getBbHeight() * 0.4, 0.3, 0.15);
        }
    }

    /** 沿线段随机撒电火花粒子 */
    private static void spawnSparkLine(ServerLevel level, Vec3 from, Vec3 to, int count) {
        for (int i = 0; i < count; i++) {
            double t = level.random.nextDouble();
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    2, 0.15, 0.1, 0.15, 0.15);
        }
    }
}
