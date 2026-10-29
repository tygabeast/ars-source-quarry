package com.tygabeast.arssourcequarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.block.MarkerBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws the Ley Marker's Source crystal: a small faceted cube stood on its point that turns on two axes at
 * once, like the gem in Ars Nouveau's Enchanter's Bow. Client only; one cube (6 quads) per visible marker,
 * no allocation per frame, and nothing is sent from the server.
 */
public class MarkerRenderer implements BlockEntityRenderer<MarkerBlockEntity> {
    private static final ResourceLocation GEM = ArsSourceQuarry.id("textures/block/source_gem.png");
    private static final float CENTER_Y = 6.75f / 16f;
    private static final float SIZE = 2.2f / 16f;
    private static final int ALPHA = 225;   // a touch translucent, like the bow's gem

    public MarkerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(MarkerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (be.getLevel() == null) return;
        BlockPos p = be.getBlockPos();
        // Per-marker phase so two markers next to each other do not turn in lockstep.
        float phase = (float) ((p.getX() * 31L + p.getZ() * 17L + p.getY() * 7L) & 63) * 0.1f;
        float angle = (be.getLevel().getGameTime() % 72000L + partialTick) / 10f + phase;   // radians, bow speed

        pose.pushPose();
        pose.translate(0.5f, CENTER_Y, 0.5f);
        pose.mulPose(Axis.XP.rotation(angle));
        pose.mulPose(Axis.YP.rotation(angle));
        pose.mulPose(Axis.ZP.rotationDegrees(45));   // stand the cube on its point
        pose.mulPose(Axis.XP.rotationDegrees(35.26f));
        pose.scale(SIZE, SIZE, SIZE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(GEM));
        cube(pose.last(), vc);
        pose.popPose();
    }

    private static void cube(PoseStack.Pose last, VertexConsumer vc) {
        Matrix4f m = last.pose();
        float h = 0.5f;
        // Each face uses one 2x2 cell of the 4x4 gem sheet (cell 0..3), so the bright corner turns per face.
        face(vc, m, last, 1, 0, 0, -1, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h);   // north
        face(vc, m, last, 2, 0, 0, 1, -h, -h, h, h, -h, h, h, h, h, -h, h, h);       // south
        face(vc, m, last, 0, -1, 0, 0, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h);  // west
        face(vc, m, last, 3, 1, 0, 0, h, -h, h, h, -h, -h, h, h, -h, h, h, h);       // east
        face(vc, m, last, 0, 0, 1, 0, -h, h, h, h, h, h, h, h, -h, -h, h, -h);       // up
        face(vc, m, last, 3, 0, -1, 0, -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h);  // down
    }

    private static void face(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, int cell, float nx, float ny, float nz,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        float u0 = (cell % 2) * 0.5f, v0 = (cell / 2) * 0.5f, u1 = u0 + 0.5f, v1 = v0 + 0.5f;
        vertex(vc, m, last, x0, y0, z0, u0, v1, nx, ny, nz);
        vertex(vc, m, last, x1, y1, z1, u1, v1, nx, ny, nz);
        vertex(vc, m, last, x2, y2, z2, u1, v0, nx, ny, nz);
        vertex(vc, m, last, x3, y3, z3, u0, v0, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, ALPHA).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, nx, ny, nz);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
