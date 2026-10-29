package com.aerovaloris.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Proste rysowanie "wstążek" (linii o szerokości zwróconych do kamery) w świecie.
 * Współrzędne podaje się w świecie, odejmowanie pozycji kamery dzieje się tutaj.
 */
public final class RibbonRenderer {
    private BufferBuilder builder;
    private Vec3 cam = Vec3.ZERO;

    public void begin(final Vec3 cameraPos) {
        this.cam = cameraPos;
        this.builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    /**
     * Segment wstążki od a do b. Szerokość i alfa mogą się różnić na końcach.
     */
    public void segment(final Vec3 a, final Vec3 b,
                        final float halfWidthA, final float halfWidthB,
                        final int r, final int g, final int bl,
                        final float alphaA, final float alphaB) {
        final Vec3 tangent = b.subtract(a);
        if (tangent.lengthSqr() < 1.0e-8) return;

        final Vec3 mid = a.add(b).scale(0.5);
        final Vec3 toCam = this.cam.subtract(mid);
        Vec3 side = tangent.cross(toCam);
        final double len = side.length();
        if (len < 1.0e-6) return;
        side = side.scale(1.0 / len);

        final Vec3 sa = side.scale(halfWidthA);
        final Vec3 sb = side.scale(halfWidthB);

        final int aa = clampAlpha(alphaA);
        final int ab = clampAlpha(alphaB);

        this.vertex(a.subtract(sa), r, g, bl, aa);
        this.vertex(a.add(sa), r, g, bl, aa);
        this.vertex(b.add(sb), r, g, bl, ab);
        this.vertex(b.subtract(sb), r, g, bl, ab);
    }

    private void vertex(final Vec3 p, final int r, final int g, final int b, final int a) {
        this.builder.addVertex((float) (p.x - this.cam.x), (float) (p.y - this.cam.y), (float) (p.z - this.cam.z))
                .setColor(r, g, b, a);
    }

    private static int clampAlpha(final float a) {
        return Math.max(0, Math.min(255, (int) (a * 255.0f)));
    }

    /**
     * Wysyła zbudowane wstążki do GPU.
     *
     * @param modelView macierz widoku z RenderLevelStageEvent (sam obrót kamery)
     * @param depthTest czy chować za blokami
     */
    public void end(final Matrix4f modelView, final boolean depthTest) {
        final MeshData mesh = this.builder.build();
        this.builder = null;
        if (mesh == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (depthTest) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        final Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(modelView);
        RenderSystem.applyModelViewMatrix();

        BufferUploader.drawWithShader(mesh);

        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
