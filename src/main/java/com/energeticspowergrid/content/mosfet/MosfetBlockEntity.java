package com.energeticspowergrid.content.mosfet;

import java.util.List;

import com.energeticspowergrid.EPGBlockEntityTypes;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;

/**
 * MOS 管方块实体：承载三个可调参数（V_GS(th)、R_DS(on)、I_D max）的滚轮菜单，
 * 并在参数变化时把新值同步到世界存档中的 {@link MosfetDevice}。
 */
public class MosfetBlockEntity extends SmartBlockEntity {
    /** 阈值电压 V_GS(th) 菜单。 */
    protected MosfetScrollValues.Vth vth;
    /** 导通电阻 R_DS(on) 菜单。 */
    protected MosfetScrollValues.RdsOn rdsOn;
    /** 最大漏极电流 I_D max 菜单。 */
    protected MosfetScrollValues.IdMax idMax;

    public MosfetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 注册三个参数菜单：V_GS(th) 挂在栅极引脚上方，R_DS(on) 与 I_D max
     * 分别挂在漏极、源极引脚上方。每个菜单的回调都会触发 updateMosfet()，
     * 保证设备侧参数即时生效。
     */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        vth = new MosfetScrollValues.Vth(
                Component.translatable("energeticspowergrid.gui.mosfet.vth"), this, new ParamSlot(0));
        vth.withCallback(i -> updateMosfet());
        behaviours.add(vth);

        rdsOn = new MosfetScrollValues.RdsOn(
                Component.translatable("energeticspowergrid.gui.mosfet.rdson"), this, new ParamSlot(1));
        rdsOn.withCallback(i -> updateMosfet());
        behaviours.add(rdsOn);

        idMax = new MosfetScrollValues.IdMax(
                Component.translatable("energeticspowergrid.gui.mosfet.idmax"), this, new ParamSlot(2));
        idMax.withCallback(i -> updateMosfet());
        behaviours.add(idMax);
    }

    /** 当前阈值电压 V_GS(th)（单位：伏）。 */
    public double getVth() {
        return vth.getVth();
    }

    /** 当前导通电阻 R_DS(on)（单位：欧姆）。 */
    public double getRdsOn() {
        return rdsOn.getRdsOn();
    }

    /** 当前最大漏极电流 I_D max（单位：安）。 */
    public double getIdMax() {
        return idMax.getIdMax();
    }

    /**
     * 把三个参数同步到存档中的设备实例。
     * 仅在服务端执行（设备数据只存在于服务端的 DevicesSavedData）；
     * 回调在参数滚轮变化时被触发，这样设备下一次求解前就能拿到新参数。
     */
    private void updateMosfet() {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        MosfetDevice device = DevicesSavedData.load(serverLevel).getDevice(worldPosition, MosfetDevice.class);
        if (device != null) {
            device.vth = getVth();
            device.rdsOn = getRdsOn();
            device.idMax = getIdMax();
        }
    }

    /**
     * 三个参数槽位（未旋转、正面朝南时的基准坐标，单位：像素）。
     * MOS 管模型扁平贴地（本体仅 2 像素高，顶面 y=2），参数框悬浮在本体上方
     * 1 像素处，分别对准三个引脚的水平位置：栅极 (13, 8) 在一侧，漏极 (3, 5)
     * 与源极 (3, 11) 在另一侧。
     * <p>
     * 位置变换刻意走与 {@code MosfetBlock#NODES} 接线点完全相同的
     * {@link NodeConfigurator} 机制（含滚转时的 rotate(0,-90,0)）——
     * 该机制的变换约定与方块模型渲染天然一致（三极管接线点已在全部
     * 放置姿态下实测对齐），参数框复用它即可保证任何姿态下都贴在
     * 模型的对应位置上，不会出现"手推旋转坐标"式的错位。
     */
    private static final NodeConfigurator SLOTS =
            new NodeConfigurator.Builder()
                    .add(13f, 3f, 8f)    // V_GS(th)：栅极上方
                    .add(3f, 3f, 5f)     // R_DS(on)：漏极上方
                    .add(3f, 3f, 11f)    // I_D max：源极上方
                    .simple(Direction.UP);

    /**
     * 参数框位置的统一函数入口（与接线点同一套变换机制）：
     * 给定方块状态与参数序号，返回方块内的相对偏移（方块坐标单位）。
     * 滚转时槽位配置绕 Y 轴预旋转 -90°，与 mosfet_roll.json 的预旋转
     * 模型保持一致。要调参数框位置只改 {@link #SLOTS} 这一处。
     *
     * @param state MOS 管方块状态（提供 FACING 与 ROLL）
     * @param index 参数序号（0=V_GS(th)、1=R_DS(on)、2=I_D max）
     */
    public static Vec3 paramOffset(BlockState state, int index) {
        boolean roll = state.getValue(DirectionalRolledDeviceBlock.ROLL);
        NodeConfigurator rotated = roll ? SLOTS.rotate(new Vec3(0, -90, 0)) : SLOTS;
        return rotated.getNodePos(state.getValue(DirectionalRolledDeviceBlock.FACING), index);
    }

    /** 单个参数菜单的放置槽位：按索引从 paramOffset 取偏移点。 */
    private static class ParamSlot extends ValueBoxTransform {
        private final int index;

        ParamSlot(int index) {
            this.index = index;
        }

        /** 从统一的 paramOffset 函数入口取本槽位的相对偏移。 */
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return paramOffset(state, index);
        }

        /**
         * 决定菜单文本面的朝向与旋转角度。
         * <ul>
         * <li>地面 / 天花板——模型平躺，文本面<b>水平</b>，分别从上/下方
         *     阅读（符号经实测校准：xRot=+90 文字朝上、−90 朝下；
         *     这与 Create Sided 的 90/270 一致）；</li>
         * <li>墙面——模型立起贴墙，文本面<b>竖直</b>朝外，采用 Create
         *     Sided 的标准水平面公式 {@code yRot = horizontalAngle(面) + 180}：
         *     文字正对房间一侧（标签朝向），玩家平视即可读。</li>
         * </ul>
         * 滚转时板子在自身平面内旋转 90°，文字随之做同样的平面内旋转：
         * 地面/天花板绕竖直轴（yRot −= 90），墙面绕板面法线
         * （rotateZ −90°，在文本面平面内旋转，正对方向不变）。
         */
        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction facing = state.getValue(DirectionalRolledDeviceBlock.FACING);
            boolean roll = state.getValue(DirectionalRolledDeviceBlock.ROLL);
            float yRot;
            float xRot;
            float zRoll = 0f;
            if (facing.getAxis().isHorizontal()) {
                // 墙面：竖直文本面朝外（Create Sided 水平面标准公式）
                yRot = AngleHelper.horizontalAngle(facing) + 180f;
                xRot = 0f;
                if (roll)
                    zRoll = 0f;
            } else if (facing == Direction.UP) {
                // 地面：水平平躺，文字朝上
                yRot = 180f;
                xRot = 90f;
            } else {
                // 天花板：水平平躺，文字朝下
                yRot = 0f;
                xRot = -90f;
            }
            if (roll && !facing.getAxis().isHorizontal())
                yRot -= 90f;
            TransformStack.of(ms)
                    .rotateYDegrees(yRot)
                    .rotateXDegrees(xRot)
                    .rotateZDegrees(zRoll);
        }
    }
}
