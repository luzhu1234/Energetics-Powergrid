package com.energeticspowergrid.content.fixture;

import com.energeticspowergrid.EPGPartialModels;
import com.energeticspowergrid.content.bulb.GrowthLampItem;
import com.energeticspowergrid.content.bulb.ILightBulb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 灯具（Light Fixture）方块实体渲染器。
 * <p>
 * 渲染分三层：
 * <ol>
 *   <li>灯泡外壳模型：按亮度档位/烧毁状态选择（关、亮、破碎），cutout 渲染；</li>
 *   <li>染色灯泡的彩色玻璃球壳：使用染色颜色着色，translucent 渲染；</li>
 *   <li>发光层：根据温度/档位得到的 alpha，以加色混合（additive）+ 全亮光照渲染光晕模型，
 *       颜色乘以 alpha 模拟亮度渐变；烧毁后不渲染光晕。</li>
 * </ol>
 */
public class LightFixtureRenderer extends SafeBlockEntityRenderer<LightFixtureBlockEntity> {
    public LightFixtureRenderer(BlockEntityRendererProvider.Context context) {
        super();
    }

    /** 主渲染逻辑：见类注释的三层结构。 */
    @Override
    protected void renderSafe(LightFixtureBlockEntity be, float partialTicks, PoseStack matrices, MultiBufferSource consumer, int light, int overlay) {
        if (!be.hasBulb())
            return;

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(LightFixtureBlock.FACING);
        Item bulb = be.getBulbItem();
        boolean growth = bulb instanceof GrowthLampItem;
        boolean dyed = be.getColor() != null && bulb instanceof ILightBulb lightBulb
                && lightBulb.thermalProperties() != null && lightBulb.thermalProperties().dyeable();

        // 第一层：灯泡外壳模型（随档位/烧毁状态变化）
        PartialModel model = modelFor(be, growth, dyed);
        if (model == null)
            return;

        var vb = consumer.getBuffer(RenderType.cutout());
        rotateToFacing(CachedBuffers.partial(model, state), facing)
                .translate(LightFixtureBlock.BULB_MODEL_OFFSET)
                .light(light)
                .renderInto(matrices, vb);

        // 第二层：可染色灯泡的彩色球壳（用染色的纹理漫反射色作为 RGB）
        int r = 255, g = 255, b = 255;
        DyeColor color = be.getColor();
        if (dyed && color != null) {
            int texDif = color.getTextureDiffuseColor();
            r = (texDif & 0xFF0000) >> 16;
            g = (texDif & 0xFF00) >> 8;
            b = texDif & 0xFF;
            vb = consumer.getBuffer(RenderType.translucent());
            rotateToFacing(CachedBuffers.partial(EPGPartialModels.DYED_LIGHT_BULB_BULB, state), facing)
                    .color(r, g, b, 255)
                    .translate(LightFixtureBlock.BULB_MODEL_OFFSET)
                    .light(light)
                    .renderInto(matrices, vb);
        }

        // 烧毁的灯泡不再渲染光晕
        if (be.isBurned())
            return;

        // 第三层：发光光晕（加色混合、全亮、关闭漫反射光照），alpha 决定亮度
        float a = be.getAlpha();
        if (a > 0) {
            PartialModel lightModel = growth ? EPGPartialModels.GROWTH_LAMP_LIGHT
                    : dyed ? EPGPartialModels.DYED_LIGHT_BULB_LIGHT
                    : EPGPartialModels.LIGHT_BULB_LIGHT;
            rotateToFacing(CachedBuffers.partial(lightModel, state), facing)
                    .translate(LightFixtureBlock.BULB_MODEL_OFFSET)
                    .light(LightTexture.FULL_BRIGHT)
                    .color((int) (a * r), (int) (a * g), (int) (a * b), 255)
                    .disableDiffuse()
                    .renderInto(matrices, consumer.getBuffer(RenderTypes.additive()));
        }
    }

    /**
     * 根据方块状态选择外壳模型：
     * 烧毁 → 破碎模型；POWER ≥ 1 → 点亮模型；否则 → 熄灭模型。
     * 生长灯与可染色灯泡各有独立的一套模型。
     */
    private PartialModel modelFor(LightFixtureBlockEntity be, boolean growth, boolean dyed) {
        int power = be.getBlockState().hasProperty(LightFixtureBlock.POWER) ? be.getBlockState().getValue(LightFixtureBlock.POWER) : 0;
        if (be.isBurned()) {
            if (growth)
                return EPGPartialModels.GROWTH_LAMP_BROKEN;
            return dyed ? EPGPartialModels.DYED_LIGHT_BULB_BROKEN : EPGPartialModels.LIGHT_BULB_BROKEN;
        }
        if (power >= 1) {
            if (growth)
                return EPGPartialModels.GROWTH_LAMP_ON;
            return dyed ? EPGPartialModels.DYED_LIGHT_BULB_ON : EPGPartialModels.LIGHT_BULB_ON;
        }
        if (growth)
            return EPGPartialModels.GROWTH_LAMP;
        return dyed ? EPGPartialModels.DYED_LIGHT_BULB : EPGPartialModels.LIGHT_BULB;
    }

    /**
     * 把模型缓冲按朝向旋转：UP 不旋转；DOWN 翻转 180°；
     * 水平朝向先绕 East 轴转 90°（贴到侧面），再按朝向的 YRot 绕 South 轴旋转。
     */
    public SuperByteBuffer rotateToFacing(SuperByteBuffer buffer, Direction facing) {
        return switch (facing) {
            case UP -> buffer;
            case DOWN -> buffer.rotateCentered((float) Math.PI, Direction.EAST);
            default -> {
                buffer.rotateCentered((float) Math.PI * 0.5f, Direction.EAST);
                yield buffer.rotateCentered((float) ((facing.toYRot()) / 180f * Math.PI), Direction.SOUTH);
            }
        };
    }
}
