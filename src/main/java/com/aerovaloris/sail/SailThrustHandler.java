package com.aerovaloris.sail;

import com.aerovaloris.Config;
import com.aerovaloris.wind.WindData;
import com.aerovaloris.wind.WindMath;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

/**
 * Dodaje ciąg żagli do statków Sable.
 * <p>
 * Dla każdego sub-levelu (statku) zbieramy wszystkie żagle – postawione bezpośrednio na statku
 * oraz na kontrapcjach (mechanical bearing itp.) postawionych na statku.
 * <ul>
 *   <li><b>Kierunek ciągu</b> = przód żagla w układzie statku BEZ obrotu łożyska
 *       (statek zawsze płynie "do przodu", jak w Sea of Thieves).</li>
 *   <li><b>Skuteczność</b> = rzeczywisty (obrócony przez łożysko) przód żagla porównany z kierunkiem wiatru.
 *       Obracając żagle łożyskiem łapiesz więcej lub mniej wiatru.</li>
 * </ul>
 */
public final class SailThrustHandler {
    private SailThrustHandler() {}

    public record SailEntry(SailHelper.SailKind kind,
                             Vector3d pos,        // pozycja w plocie
                             Vector3d thrustDir,  // kierunek ciągu w układzie plotu (bez obrotu łożyska)
                             Vector3d catchDir,   // rzeczywisty przód (z obrotem łożyska) w układzie plotu
                             boolean onContraption) {}

    private static final List<SailEntry> SAILS = new ObjectArrayList<>();
    private static final Pose3d CONTRAPTION_POSE = new Pose3d();

    public static void onPrePhysicsTick(final ForgeSablePrePhysicsTickEvent event) {
        final SubLevelPhysicsSystem system = event.getPhysicsSystem();
        final ServerLevel level = system.getLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        final double baseThrust = Config.BASE_THRUST_PER_SAIL.get();
        if (baseThrust <= 0) return;

        final WindData wind = WindData.get(level);
        final Vec3 windDir = wind.direction();
        final double windStrength = wind.effectiveStrength(level);

        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            try {
                applySails(system, subLevel, event.getTimeStep(), baseThrust, windDir, windStrength);
            } finally {
                SAILS.clear();
            }
        }
    }

    private static void applySails(final SubLevelPhysicsSystem system, final ServerSubLevel subLevel,
                                   final double timeStep, final double baseThrust,
                                   final Vec3 windDir, final double windStrength) {
        final MassData mass = subLevel.getMassTracker();
        if (mass == null || mass.isInvalid()) return;
        // kotwica na dnie: żagle nie przepychają statku (inaczej wlókłby kotwicę z wiatrem)
        if (com.aerovaloris.anchor.AnchorManager.isAnchored(subLevel)) return;

        collectSails(subLevel, system.getPartialPhysicsTick(), SAILS);

        final int count = SAILS.size();
        if (count == 0) return;

        // ciąg rośnie z liczbą żagli: bazowy * N^wykładnik  ->  na jeden żagiel: bazowy * N^(wykładnik-1)
        final double perSail = baseThrust * Math.pow(count, Config.SAIL_COUNT_EXPONENT.get() - 1.0);

        final double minMul = Config.WIND_MIN_MULTIPLIER.get();
        final double maxMul = Config.WIND_MAX_MULTIPLIER.get();
        final double curve = Config.WIND_CURVE.get();
        final double maxTurn = Math.toRadians(Config.MAX_SAIL_TURN_ANGLE.get());
        final boolean atCom = Config.APPLY_THRUST_AT_CENTER_OF_MASS.get();

        final Pose3d pose = subLevel.logicalPose();
        final Vector3dc com = mass.getCenterOfMass();
        final QueuedForceGroup group = subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());

        for (final SailEntry sail : SAILS) {
            // kierunek ciągu w świecie, rzut na poziom
            final Vector3d thrustWorld = pose.transformNormal(new Vector3d(sail.thrustDir()));
            thrustWorld.y = 0;
            final double horizontal = thrustWorld.length();
            if (horizontal < 1.0e-3) continue; // żagiel leży płasko – nie łapie wiatru
            thrustWorld.div(horizontal);

            // rzeczywisty przód żagla (z obrotem łożyska) w świecie
            final Vector3d catchWorld = pose.transformNormal(new Vector3d(sail.catchDir()));
            catchWorld.y = 0;
            final double catchLen = catchWorld.length();
            if (catchLen < 1.0e-3) {
                catchWorld.set(thrustWorld);
            } else {
                catchWorld.div(catchLen);
            }

            if (sail.onContraption()) {
                clampTurn(thrustWorld, catchWorld, maxTurn);
            }

            final double catchDot = catchWorld.x * windDir.x + catchWorld.z * windDir.z;
            final double windMul = WindMath.sailMultiplier(catchDot, windStrength, minMul, maxMul, curve);

            final double magnitude = perSail * SailHelper.thrustMultiplier(sail.kind()) * horizontal * windMul;
            if (magnitude <= 0) continue;

            // impuls = siła * dt, w układzie lokalnym statku
            final Vector3d impulse = thrustWorld.mul(magnitude * timeStep);
            pose.transformNormalInverse(impulse);

            final Vector3d point = atCom ? new Vector3d(com) : new Vector3d(sail.pos());
            group.applyAndRecordPointForce(point, impulse);
        }
    }

    /**
     * Zbiera wszystkie żagle statku: postawione bezpośrednio na nim i na kontrapcjach (łożyskach).
     * Pozycje i kierunki w układzie plotu.
     */
    public static void collectSails(final ServerSubLevel subLevel, final double partialTick, final List<SailEntry> out) {
        final ServerLevelPlot plot = subLevel.getPlot();

        // --- żagle bezpośrednio na statku ---
        for (final BlockSubLevelLiftProvider.LiftProviderContext ctx : plot.getLiftProviders()) {
            final SailHelper.SailKind kind = SailHelper.kind(ctx.state());
            if (kind == SailHelper.SailKind.NONE) continue;
            final Direction front = SailHelper.front(ctx.state());
            if (front == null) continue;

            final Vector3d dir = new Vector3d(front.getStepX(), front.getStepY(), front.getStepZ());
            final Vector3d pos = new Vector3d(ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5);
            out.add(new SailEntry(kind, pos, dir, new Vector3d(dir), false));
        }

        // --- żagle na kontrapcjach (łożyska) ---
        for (final KinematicContraption contraption : plot.getContraptions()) {
            if (!contraption.sable$isValid()) continue;
            // łożyska śmigieł zwracają pustą mapę, więc śmigła z Aeronautics są pomijane automatycznie
            final var providers = contraption.sable$liftProviders();
            if (providers.isEmpty()) continue;

            contraption.sable$getLocalPose(CONTRAPTION_POSE, partialTick);

            for (final BlockSubLevelLiftProvider.LiftProviderContext ctx : providers.values()) {
                final SailHelper.SailKind kind = SailHelper.kind(ctx.state());
                if (kind == SailHelper.SailKind.NONE) continue;
                final Direction front = SailHelper.front(ctx.state());
                if (front == null) continue;

                final Vector3d thrustDir = new Vector3d(front.getStepX(), front.getStepY(), front.getStepZ());
                final Vector3d catchDir = CONTRAPTION_POSE.transformNormal(new Vector3d(thrustDir));
                final Vector3d pos = CONTRAPTION_POSE.transformPosition(
                        new Vector3d(ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5));
                out.add(new SailEntry(kind, pos, thrustDir, catchDir, true));
            }
        }
    }

    /**
     * Ogranicza kąt między przodem statku (a) a obróconym żaglem (b) do maxTurn, wynik zapisuje w b.
     * Oba wektory poziome i znormalizowane.
     */
    private static void clampTurn(final Vector3d a, final Vector3d b, final double maxTurn) {
        final double cross = a.x * b.z - a.z * b.x;
        final double dot = a.x * b.x + a.z * b.z;
        final double angle = Math.atan2(cross, dot);
        if (Math.abs(angle) <= maxTurn) return;

        final double clamped = Math.copySign(maxTurn, angle);
        final double c = Math.cos(clamped);
        final double s = Math.sin(clamped);
        b.set(a.x * c - a.z * s, 0, a.z * c + a.x * s);
    }
}
