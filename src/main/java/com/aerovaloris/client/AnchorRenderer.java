package com.aerovaloris.client;

import com.aerovaloris.ModBlocks;
import com.aerovaloris.aeronauticsvaloris;
import com.aerovaloris.anchor.AnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Kotwica (model bloku "block/anchor") + lina od ucha kotwicy do koła linowego kabestanu.
 * Punkt zaczepu jest liczony z płynnej pozy statku Sable (renderPose), więc lina
 * trzyma się kabestanu także na płynącym statku.
 */
public class AnchorRenderer extends EntityRenderer<AnchorEntity> {

    public static final PartialModel ANCHOR =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(aeronauticsvaloris.MODID, "block/anchor"));
    public static final ResourceLocation ROPE =
            ResourceLocation.fromNamespaceAndPath(aeronauticsvaloris.MODID, "textures/block/anchor_rope.png");

    private static final float ROPE_HALF_WIDTH = 0.07f;
    private static final double SEGMENT = 0.5;
    /** Dłuższej liny nie rysujemy (to zawsze błąd pozycji, nie prawdziwa lina). */
    private static final double MAX_RENDER_LENGTH = 1024.0;
    private static final int MAX_SEGMENTS = 2048;

    /** Wywołane z konstruktora klienta, żeby PartialModel zarejestrował się przed ładowaniem modeli. */
    public static void init() {
    }

    public AnchorRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(final AnchorEntity entity, final Frustum frustum, final double camX, final double camY, final double camZ) {
        // lina bywa bardzo długa – nie odcinamy po bounding boxie samej kotwicy
        return entity.distanceToSqr(camX, camY, camZ) < 256.0 * 256.0;
    }

    @Override
    public ResourceLocation getTextureLocation(final AnchorEntity entity) {
        return ROPE;
    }

    @Override
    public void render(final AnchorEntity entity, final float yaw, final float partialTick, final PoseStack ms,
                       final MultiBufferSource buffers, final int light) {
        final Direction facing = entity.getFacing();
        final BlockState ref = ModBlocks.ANCHOR_WINCH.get().defaultBlockState();

        // --- kotwica ---
        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180f));
        ms.translate(-0.5, 0, -0.5);
        CachedBuffers.partial(ANCHOR, ref)
                .light(light)
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        ms.popPose();

        // --- lina ---
        final Vec3 origin = entity.getPosition(partialTick);
        final Vec3 attach = attachWorld(entity, partialTick);
        // klient chwilowo nie zna sub-levelu statku -> attach to surowe współrzędne plotu Sable (miliony bloków);
        // taka lina przepełniała bufor wierzchołków i wywalała JVM (EXCEPTION_ACCESS_VIOLATION)
        if (attach == null || attach.distanceToSqr(origin) > MAX_RENDER_LENGTH * MAX_RENDER_LENGTH) {
            super.render(entity, yaw, partialTick, ms, buffers, light);
            return;
        }
        final Vec3 bottom = new Vec3(0, AnchorEntity.TOP, 0);
        final Vec3 top = attach.subtract(origin);
        renderRope(entity.level(), origin, ms, buffers.getBuffer(RenderType.entityCutoutNoCull(ROPE)), bottom, top);

        super.render(entity, yaw, partialTick, ms, buffers, light);
    }

    private static Vec3 attachWorld(final AnchorEntity entity, final float partialTick) {
        final BlockPos winch = entity.getWinchPos();
        final Vec3 local = AnchorEntity.attachLocal(winch, entity.getFacing());
        final ClientSubLevel sub = Sable.HELPER.getContainingClient(winch);
        if (sub != null) return sub.renderPose(partialTick).transformPosition(local);
        // kabestan w plocie Sable, ale sub-level jeszcze/już nieobecny na kliencie – nie ma gdzie zaczepić liny
        return Sable.HELPER.isInPlotGrid(entity.level(), winch) ? null : local;
    }

    /** Lina jako dwie skrzyżowane wstęgi z teksturą, pocięte na krótkie odcinki (UV 0..1 na odcinek). */
    private static void renderRope(final Level level, final Vec3 origin, final PoseStack ms, final VertexConsumer vc,
                                   final Vec3 from, final Vec3 to) {
        final Vec3 d = to.subtract(from);
        final double len = d.length();
        if (len < 1.0e-3) return;
        final Vec3 dir = d.scale(1.0 / len);

        Vec3 side1 = dir.cross(new Vec3(1, 0, 0));
        if (side1.lengthSqr() < 1.0e-4) side1 = dir.cross(new Vec3(0, 0, 1));
        side1 = side1.normalize().scale(ROPE_HALF_WIDTH);
        final Vec3 side2 = dir.cross(side1).normalize().scale(ROPE_HALF_WIDTH);

        final PoseStack.Pose pose = ms.last();
        final int segments = Mth.clamp(Mth.ceil(len / SEGMENT), 1, MAX_SEGMENTS);
        for (int i = 0; i < segments; i++) {
            final Vec3 a = from.add(d.scale(i / (double) segments));
            final Vec3 b = from.add(d.scale((i + 1) / (double) segments));
            final int la = LevelRenderer.getLightColor(level, BlockPos.containing(origin.add(a)));
            final int lb = LevelRenderer.getLightColor(level, BlockPos.containing(origin.add(b)));
            quad(vc, pose, a, b, side1, la, lb);
            quad(vc, pose, a, b, side2, la, lb);
        }
    }

    private static void quad(final VertexConsumer vc, final PoseStack.Pose pose, final Vec3 a, final Vec3 b,
                             final Vec3 side, final int lightA, final int lightB) {
        vertex(vc, pose, a.subtract(side), 0f, 1f, lightA);
        vertex(vc, pose, a.add(side), 1f, 1f, lightA);
        vertex(vc, pose, b.add(side), 1f, 0f, lightB);
        vertex(vc, pose, b.subtract(side), 0f, 0f, lightB);
    }

    private static void vertex(final VertexConsumer vc, final PoseStack.Pose pose, final Vec3 p,
                               final float u, final float v, final int light) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 1f, 0f);
    }
}
