package com.aerovaloris.client;

import com.aerovaloris.Config;
import com.aerovaloris.wind.ClientSailPoints;
import com.aerovaloris.wind.ClientWindState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Smugi wiatru w stylu Sea of Thieves, rysowane gęsto tylko wokół żagli statków: cienkie białe wstęgi, które płyną z wiatrem,
 * lekko falują i czasem robią pętlę.
 * <p>
 * Tor każdej smugi jest liczony analitycznie z czasu, więc ogon jest idealnie gładki
 * i nie trzeba trzymać historii punktów.
 */
public final class WindTrailRenderer {
    private WindTrailRenderer() {}

    private static final int SEGMENTS = 32;
    /** Długość ogona w sekundach – dłuższy ogon = smuga wygląda jak spokojnie płynąca wstęga. */
    private static final double TAIL_SECONDS = 1.1;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final List<Trail> TRAILS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final RibbonRenderer RIBBONS = new RibbonRenderer();
    private static long clientTicks = 0;

    private static final class Trail {
        Vec3 origin;
        Vec3 dir;
        Vec3 side;
        double t0;
        double life;
        double speed;
        double ampSide, ampUp, freq, phase, phase2;
        boolean loop;
        double loopStart, loopDur, loopR;
        float width;

        Vec3 pos(final double t) {
            final double tau = Math.max(0.0, t - this.t0);
            Vec3 p = this.origin.add(this.dir.scale(this.speed * tau));
            p = p.add(this.side.scale(this.ampSide * Math.sin(this.freq * tau + this.phase)));
            p = p.add(UP.scale(this.ampUp * Math.sin(this.freq * 0.7 * tau + this.phase2)));
            if (this.loop && tau > this.loopStart) {
                final double u = Mth.clamp((tau - this.loopStart) / this.loopDur, 0.0, 1.0);
                final double s = u * u * (3.0 - 2.0 * u); // smoothstep
                final double th = Math.PI * 2.0 * s;
                p = p.add(this.dir.scale(-this.loopR * Math.sin(th)))
                        .add(UP.scale(this.loopR * (1.0 - Math.cos(th))));
            }
            return p;
        }

        double age(final double t) {
            return t - this.t0;
        }
    }

    private static double time(final float partialTick) {
        return (clientTicks + partialTick) / 20.0;
    }

    public static void clear() {
        TRAILS.clear();
    }

    public static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        clientTicks++;

        final double now = time(0);
        final Iterator<Trail> it = TRAILS.iterator();
        while (it.hasNext()) {
            final Trail tr = it.next();
            if (tr.age(now) > tr.life + TAIL_SECONDS) it.remove();
        }

        NEARBY_SAILS.clear();
        if (!Config.SHOW_WIND_TRAILS.get() || !ClientWindState.hasData()) return;
        if (!mc.level.dimensionType().hasSkyLight()) return;

        final Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return;
        final Vec3 cam = camera.getPosition();

        // żagle w pobliżu kamery (pozycje z serwera w układzie plotu -> świat)
        final double view = Config.WIND_TRAIL_VIEW_DISTANCE.get();
        final double view2 = view * view;
        final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (final long packed : ClientSailPoints.get()) {
            mpos.set(packed);
            Vec3 p = new Vec3(mpos.getX() + 1.0, mpos.getY() + 1.0, mpos.getZ() + 1.0); // środek komórki 2x2x2
            final ClientSubLevel sub = Sable.HELPER.getContainingClient(mpos);
            if (sub != null) p = sub.renderPose(1.0f).transformPosition(p);
            if (p.distanceToSqr(cam) <= view2) NEARBY_SAILS.add(p);
        }
        if (NEARBY_SAILS.isEmpty()) return;

        final double strength = ClientWindState.strength(1.0f);
        final int byCount = NEARBY_SAILS.size() * 10;
        final int target = (int) Math.round(Math.min(Config.MAX_WIND_TRAILS.get(), byCount) * (0.3 + 0.7 * strength));

        // równomierne dokładanie po kilka na tick (bez "wybuchów" nowych smug)
        int spawnBudget = 4;
        while (TRAILS.size() < target && spawnBudget-- > 0) {
            spawn(NEARBY_SAILS.get(RANDOM.nextInt(NEARBY_SAILS.size())), strength, now);
        }
    }

    private static final List<Vec3> NEARBY_SAILS = new ArrayList<>();

    private static void spawn(final Vec3 sail, final double strength, final double now) {
        final Vec3 dir = ClientWindState.direction(1.0f);
        final double radius = Config.WIND_TRAIL_RADIUS.get();

        final Trail tr = new Trail();
        tr.dir = dir;
        tr.side = new Vec3(-dir.z, 0, dir.x);
        // wolno i długo – smuga spokojnie przepływa obok żagla zamiast przeskakiwać
        tr.speed = 2.5 + 3.0 * strength;
        tr.life = 3.5 + RANDOM.nextDouble() * 2.5;

        // losowy punkt w kuli wokół żagla, cofnięty pod wiatr – smuga przelatuje przez żagiel
        final double upwind = tr.speed * tr.life * 0.5;
        final double ox = (RANDOM.nextDouble() * 2.0 - 1.0) * radius;
        final double oy = (RANDOM.nextDouble() * 2.0 - 1.0) * radius;
        final double oz = (RANDOM.nextDouble() * 2.0 - 1.0) * radius;
        tr.origin = sail.add(ox - dir.x * upwind, oy, oz - dir.z * upwind);

        // łagodne, wolne falowanie (niska częstotliwość)
        tr.ampSide = 0.25 + RANDOM.nextDouble() * 0.45;
        tr.ampUp = 0.15 + RANDOM.nextDouble() * 0.3;
        tr.freq = 0.6 + RANDOM.nextDouble() * 0.7;
        tr.phase = RANDOM.nextDouble() * Math.PI * 2.0;
        tr.phase2 = RANDOM.nextDouble() * Math.PI * 2.0;

        tr.loop = RANDOM.nextFloat() < 0.18f;
        if (tr.loop) {
            tr.loopDur = 1.3 + RANDOM.nextDouble() * 0.5;
            tr.loopStart = 0.8 + RANDOM.nextDouble() * Math.max(0.1, tr.life - tr.loopDur - 1.2);
            // promień taki, żeby pętla była szybsza od ruchu do przodu – wtedy widać zawinięcie
            tr.loopR = 1.35 * tr.speed * tr.loopDur / (Math.PI * 2.0);
        }

        tr.width = 0.07f + RANDOM.nextFloat() * 0.05f;
        tr.t0 = now;
        TRAILS.add(tr);
    }

    private static double smooth(final double x) {
        return x * x * (3.0 - 2.0 * x);
    }

    public static void render(final RenderLevelStageEvent event) {
        if (TRAILS.isEmpty() || !Config.SHOW_WIND_TRAILS.get()) return;

        final float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        final double now = time(pt);
        final Vec3 cam = event.getCamera().getPosition();
        final float opacity = Config.WIND_TRAIL_OPACITY.get().floatValue();
        final float strengthAlpha = (float) Mth.clamp(0.35 + ClientWindState.strength(pt), 0.0, 1.0);

        RIBBONS.begin(cam);
        final double step = TAIL_SECONDS / SEGMENTS;

        for (final Trail tr : TRAILS) {
            final double age = tr.age(now);
            if (age < 0) continue;

            // pojawianie się i znikanie
            // długie, miękkie pojawianie się i znikanie (smoothstep)
            final double fadeIn = smooth(Mth.clamp(age / 0.9, 0.0, 1.0));
            final double fadeOut = smooth(Mth.clamp((tr.life - age) / 1.2, 0.0, 1.0));
            final float lifeAlpha = (float) (fadeIn * fadeOut) * opacity * strengthAlpha;
            if (lifeAlpha <= 0.003f) continue;

            Vec3 prev = tr.pos(now);
            for (int k = 1; k <= SEGMENTS; k++) {
                final double tk = now - k * step;
                if (tk < tr.t0) break;
                final Vec3 p = tr.pos(tk);

                final float fA = 1.0f - (k - 1) / (float) SEGMENTS;
                final float fB = 1.0f - k / (float) SEGMENTS;
                // przód smugi delikatnie zaostrzony, ogon zanika
                final float headA = Math.min(1.0f, (k - 1) / 2.0f + 0.35f);
                final float headB = Math.min(1.0f, k / 2.0f + 0.35f);

                RIBBONS.segment(prev, p,
                        tr.width * (0.35f + 0.65f * fA) * headA,
                        tr.width * (0.35f + 0.65f * fB) * headB,
                        236, 244, 255,
                        lifeAlpha * fA * fA * headA,
                        lifeAlpha * fB * fB * headB);
                prev = p;
            }
        }

        RIBBONS.end(event.getModelViewMatrix(), true);
    }
}
