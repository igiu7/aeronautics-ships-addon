package com.aerovaloris.client;

import com.aerovaloris.Config;
import com.aerovaloris.sail.SailHelper;
import com.aerovaloris.wind.ClientWindState;
import com.aerovaloris.wind.WindMath;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Gdy patrzysz na żagiel, nad jego przednią stroną rysowana jest złota strzałka
 * (przód = kierunek, w którym żagiel pcha statek), a pod celownikiem pokazuje się
 * kierunek przodu i aktualny mnożnik od wiatru.
 */
public final class SailFrontIndicator {
    private SailFrontIndicator() {}

    /** Żagiel, na który patrzy gracz (aktualizowane co tick). */
    @Nullable
    private static BlockPos hoveredPos;
    @Nullable
    private static Direction hoveredFront;

    public static void tick() {
        hoveredPos = null;
        hoveredFront = null;

        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !Config.SHOW_SAIL_FRONT_INDICATOR.get()) return;
        final HitResult hit = mc.hitResult;
        if (!(hit instanceof final BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;

        final BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (!SailHelper.isSail(state)) return;

        hoveredPos = bhr.getBlockPos().immutable();
        hoveredFront = SailHelper.front(state);
    }

    @Nullable
    private static Pose3dc poseFor(final BlockPos pos, final float pt) {
        final ClientSubLevel sub = Sable.HELPER.getContainingClient(pos);
        return sub == null ? null : sub.renderPose(pt);
    }

    /** Przód żagla w świecie (z uwzględnieniem statku), albo null. */
    @Nullable
    public static Vec3 hoveredFrontWorld(final float pt) {
        if (hoveredPos == null || hoveredFront == null) return null;
        Vec3 front = Vec3.atLowerCornerOf(hoveredFront.getNormal());
        final Pose3dc pose = poseFor(hoveredPos, pt);
        if (pose != null) front = pose.transformNormal(front);
        return front;
    }

    /** Mnożnik od wiatru dla oglądanego żagla (bez uwzględnienia obrotu łożyska). */
    public static double hoveredWindMultiplier(final float pt) {
        final Vec3 front = hoveredFrontWorld(pt);
        if (front == null) return 1.0;
        final Vec3 h = new Vec3(front.x, 0, front.z);
        if (h.lengthSqr() < 1.0e-6) return 0.0;
        final Vec3 n = h.normalize();
        final Vec3 wind = ClientWindState.direction(pt);
        return WindMath.sailMultiplier(n.x * wind.x + n.z * wind.z, ClientWindState.strength(pt),
                Config.WIND_MIN_MULTIPLIER.get(), Config.WIND_MAX_MULTIPLIER.get(), Config.WIND_CURVE.get());
    }

    public static boolean isHovering() {
        return hoveredPos != null && hoveredFront != null;
    }

    public static void render(final RenderLevelStageEvent event) {
        if (!isHovering()) return;
        final float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        Vec3 center = Vec3.atCenterOf(hoveredPos);
        Vec3 front = Vec3.atLowerCornerOf(hoveredFront.getNormal());
        final Pose3dc pose = poseFor(hoveredPos, pt);
        if (pose != null) {
            center = pose.transformPosition(center);
            front = pose.transformNormal(front);
        }

        final Vec3 cam = event.getCamera().getPosition();
        final double pulse = 0.75 + 0.25 * Math.sin((System.currentTimeMillis() % 100000L) / 160.0);
        final float alpha = (float) (0.9 * pulse);

        final Vec3 start = center.add(front.scale(0.55));
        final Vec3 tip = center.add(front.scale(1.6));
        final Vec3 headBase = tip.subtract(front.scale(0.4));
        Vec3 perp = front.cross(cam.subtract(tip));
        if (perp.lengthSqr() < 1.0e-6) perp = front.cross(new Vec3(0, 1, 0));
        if (perp.lengthSqr() < 1.0e-6) perp = new Vec3(1, 0, 0);
        perp = perp.normalize().scale(0.3);

        final int r = 255, g = 200, b = 40;
        RIBBONS.begin(cam);
        RIBBONS.segment(start, tip, 0.06f, 0.06f, r, g, b, alpha * 0.6f, alpha);
        RIBBONS.segment(tip, headBase.add(perp), 0.06f, 0.06f, r, g, b, alpha, alpha);
        RIBBONS.segment(tip, headBase.subtract(perp), 0.06f, 0.06f, r, g, b, alpha, alpha);
        // przezroczysta wersja bez testu głębokości, żeby strzałka była widoczna też przez płótno
        RIBBONS.end(event.getModelViewMatrix(), true);

        RIBBONS.begin(cam);
        RIBBONS.segment(start, tip, 0.06f, 0.06f, r, g, b, alpha * 0.15f, alpha * 0.25f);
        RIBBONS.segment(tip, headBase.add(perp), 0.06f, 0.06f, r, g, b, alpha * 0.25f, alpha * 0.25f);
        RIBBONS.segment(tip, headBase.subtract(perp), 0.06f, 0.06f, r, g, b, alpha * 0.25f, alpha * 0.25f);
        RIBBONS.end(event.getModelViewMatrix(), false);
    }

    private static final RibbonRenderer RIBBONS = new RibbonRenderer();
}
