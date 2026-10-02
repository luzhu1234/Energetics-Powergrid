package com.energeticspowergrid.content.factorylight;

import com.energeticspowergrid.EPGPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 工厂灯（Factory Light）方块实体渲染器。
 * <p>
 * 不渲染灯泡本体（外观完全由方块静态模型表达），只渲染灯罩下方的
 * “光锥/光线”特效层：按灯组段位选择对应形状的光线模型（单体/前段/中段/后段），
 * X 轴灯组的光线模型需要额外绕 Y 轴旋转 90°。
 * 使用加色混合（additive）+ 全亮光照渲染，灰度颜色乘以 alpha
 * （alpha 由温度与亮度档位共同决定，见 {@link FactoryLightBlockEntity#getAlpha}）。
 */
public class FactoryLightRenderer extends SafeBlockEntityRenderer<FactoryLightBlockEntity> {
    public FactoryLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** 主渲染：无灯泡或已烧毁则不渲染光线。 */
    @Override
    protected void renderSafe(FactoryLightBlockEntity be, float partialTicks, PoseStack matrices, MultiBufferSource consumer, int light, int overlay) {
        if (!be.hasBulb() || be.isBurned())
            return;
        var state = be.getBlockState();
        int part = state.getValue(FactoryLightBlock.PART);
        // 按段位选择光线模型；X 轴段位（4/5/6）复用 Z 轴模型并旋转 90°
        int rotation = 0;
        PartialModel lightModel = switch (part) {
            case 0 -> EPGPartialModels.FL_RAYS_SINGLE;  // 单体
            case 1 -> EPGPartialModels.FL_RAYS_FRONT;   // Z 轴负端
            case 2 -> EPGPartialModels.FL_RAYS_CENTER;  // Z 轴中段
            case 3 -> EPGPartialModels.FL_RAYS_BACK;    // Z 轴正端
            case 4 -> {
                rotation = 90;
                yield EPGPartialModels.FL_RAYS_FRONT;   // X 轴负端
            }
            case 5 -> {
                rotation = 90;
                yield EPGPartialModels.FL_RAYS_CENTER;  // X 轴中段
            }
            case 6 -> {
                rotation = 90;
                yield EPGPartialModels.FL_RAYS_BACK;    // X 轴正端
            }
            default -> EPGPartialModels.FL_RAYS_SINGLE;
        };
        // alpha 换算到 0~255 的灰度；> 0 才渲染光效
        int a = (int) (be.getAlpha() * 255);
        if (a > 0) {
            CachedBuffers.partial(lightModel, state)
                    .light(LightTexture.FULL_BRIGHT)
                    .rotateYCenteredDegrees(rotation)
                    .color(a, a, a, 255)
                    .disableDiffuse()
                    .renderInto(matrices, consumer.getBuffer(RenderTypes.additive()));
        }
    }
}
