package com.example.desktopmonitor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MonitorBlockEntityRenderer implements BlockEntityRenderer<MonitorBlockEntity, MonitorBlockEntityRenderState> {
    public MonitorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public MonitorBlockEntityRenderState createRenderState() {
        return new MonitorBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(MonitorBlockEntity blockEntity, MonitorBlockEntityRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.facing = blockEntity.getBlockState().getValue(MonitorBlock.FACING);
    }

    @Override
    public void submit(MonitorBlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        if (!DesktopMonitorFrame.isAvailable()) {
            return;
        }

        poseStack.pushPose();
        poseStack.rotateDegrees(com.mojang.math.Axis.YP, switch (state.facing) {
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(DesktopMonitorFrame.TEXTURE),
            (pose, vertices) -> drawScreen(pose, vertices, state.lightCoords));
        poseStack.popPose();
    }

    private static void drawScreen(PoseStack.Pose pose, VertexConsumer vertices, int light) {
        float left = 1.0f / 16.0f;
        float right = 15.0f / 16.0f;
        float bottom = 5.0f / 16.0f;
        float top = 13.0f / 16.0f;
        float front = 9.01f / 16.0f;

        // The texture's top row is v=0; the front of the block faces south.
        vertex(vertices, pose, left, bottom, front, 0, 1, light);
        vertex(vertices, pose, right, bottom, front, 1, 1, light);
        vertex(vertices, pose, right, top, front, 1, 0, light);
        vertex(vertices, pose, left, top, front, 0, 0, light);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light) {
        vertices.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setUv1(0, 0)
            .setLight(light)
            .setNormal(pose, 0, 0, 1);
    }
}
