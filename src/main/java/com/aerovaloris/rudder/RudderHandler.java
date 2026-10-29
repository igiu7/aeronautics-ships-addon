package com.aerovaloris.rudder;

import com.aerovaloris.Config;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Skręcanie statku sterem.
 * <p>
 * Model: ster nie zadaje "realistycznej" siły hydrodynamicznej, tylko steruje prędkością obrotu (yaw)
 * statku, jak w Sea of Thieves:
 * <pre>
 *   docelowa prędkość obrotu  ω = maxTurnRate · skuteczność(kąt) · v / (v + v0)
 *   promień skrętu            r = v / ω = (v + v0) / (maxTurnRate · skuteczność)
 * </pre>
 * Czyli im szybciej płyniesz, tym szybciej skręcasz, ale koło skrętu i tak rośnie z prędkością.
 * Moment obrotowy jest ograniczony (∝ liczba sterów · prędkość), więc ciężkie statki reagują wolniej.
 * Ster działa tylko w wodzie i tylko gdy statek ma prędkość wzdłuż swojej osi.
 * Wychylenie: z wału (RudderBlockEntity) albo z obrotu mechanical bearinga, na którym stoi ster.
 */
public final class RudderHandler {
    private RudderHandler() {}

    private static final Pose3d CONTRAPTION_POSE = new Pose3d();

    public static void onPrePhysicsTick(final ForgeSablePrePhysicsTickEvent event) {
        final SubLevelPhysicsSystem system = event.getPhysicsSystem();
        final ServerLevel level = system.getLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            apply(system, level, subLevel, event.getTimeStep());
        }
    }

    private static final class Accumulator {
        int count;
        double effect;          // suma skuteczności (-1..1) sterów w wodzie
        final Vector3d forward = new Vector3d(); // suma kierunków "do przodu" w świecie
    }

    private static void apply(final SubLevelPhysicsSystem system, final ServerLevel level,
                              final ServerSubLevel subLevel, final double dt) {
        final MassData mass = subLevel.getMassTracker();
        if (mass == null || mass.isInvalid()) return;

        final Pose3d pose = subLevel.logicalPose();
        final ServerLevelPlot plot = subLevel.getPlot();
        final Accumulator acc = new Accumulator();

        // stery bezpośrednio na statku
        for (final BlockSubLevelLiftProvider.LiftProviderContext ctx : plot.getLiftProviders()) {
            if (!(ctx.state().getBlock() instanceof RudderBlock)) continue;
            final Vector3d pos = new Vector3d(ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5);
            // isLoaded: nigdy nie wymuszaj ładowania chunków z wątku fizyki
            final double deflection = level.isLoaded(ctx.pos())
                    && level.getBlockEntity(ctx.pos()) instanceof final RudderBlockEntity be
                    ? be.getDeflection(1.0f) : 0.0;
            addRudder(level, pose, ctx.state(), pos, deflection, acc);
        }

        // stery na kontrapcjach (mechanical bearing sterowany np. Steering Wheel)
        for (final KinematicContraption contraption : plot.getContraptions()) {
            if (!contraption.sable$isValid()) continue;
            final var providers = contraption.sable$liftProviders();
            if (providers.isEmpty()) continue;
            contraption.sable$getLocalPose(CONTRAPTION_POSE, system.getPartialPhysicsTick());

            for (final BlockSubLevelLiftProvider.LiftProviderContext ctx : providers.values()) {
                if (!(ctx.state().getBlock() instanceof RudderBlock)) continue;
                final Direction facing = ctx.state().getValue(RudderBlock.FACING);
                final Vector3d base = new Vector3d(facing.getStepX(), 0, facing.getStepZ());
                final Vector3d rotated = CONTRAPTION_POSE.transformNormal(new Vector3d(base));
                // wychylenie = obrót łożyska (wokół osi Y statku)
                final double extra = Math.toDegrees(signedYaw(base, rotated));
                final Vector3d pos = CONTRAPTION_POSE.transformPosition(
                        new Vector3d(ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5));
                addRudder(level, pose, ctx.state(), pos, extra, acc);
            }
        }

        if (acc.count == 0) return;

        final RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
        if (handle == null) return;

        final Vector3d forward = acc.forward.normalize(new Vector3d());
        if (!forward.isFinite()) return;

        final Vector3d linVel = handle.getLinearVelocity(new Vector3d());
        final Vector3d angVel = handle.getAngularVelocity(new Vector3d());
        double speed = linVel.x * forward.x + linVel.z * forward.z;

        // --- KIL: wygaszanie bocznego poślizgu w środku masy ---
        // Bez tego po skręcie kadłub płynie bokiem, a opór wody działający pod środkiem masy
        // (Sable / High Seas) kładzie statek na burtę. Siła przyłożona w środku masy nie daje
        // żadnego momentu, a prędkość zaczyna podążać za dziobem (jak w Sea of Thieves).
        final double keelTau = Config.KEEL_RESPONSE.get();
        if (keelTau > 0) {
            final double latX = linVel.x - speed * forward.x;
            final double latZ = linVel.z - speed * forward.z;
            final double kKeel = Math.min(1.0, dt / keelTau);
            final double m = mass.getMass();
            // część bocznej prędkości zamieniona na ruch do przodu (statek nie traci całej prędkości w zakręcie)
            final double lat = Math.sqrt(latX * latX + latZ * latZ);
            final double keep = Config.KEEL_KEEP_SPEED.get() * lat * Math.signum(speed == 0 ? 1 : speed);
            final Vector3d keelWorld = new Vector3d(
                    (-latX + keep * forward.x) * m * kKeel, 0,
                    (-latZ + keep * forward.z) * m * kKeel);
            final Vector3d keelLocal = pose.transformNormalInverse(keelWorld);
            if (keelLocal.isFinite()) {
                subLevel.getOrCreateQueuedForceGroup(ForceGroups.DRAG.get())
                        .applyAndRecordPointForce(new Vector3d(mass.getCenterOfMass()), keelLocal);
            }
        }

        final double minSpeed = Config.RUDDER_MIN_SPEED.get();
        if (Math.abs(speed) < minSpeed) return;

        // na wstecznym (wyraźnym, nie przy ~0 m/s) ster działa odwrotnie i słabiej
        double effect = acc.effect / acc.count;
        if (speed < -0.5) {
            effect = -effect * 0.5;
        }
        speed = Math.abs(speed);

        final double maxRate = Math.toRadians(Config.RUDDER_MAX_TURN_RATE.get());
        final double v0 = Config.RUDDER_SPEED_HALF.get();
        // skręt od 0 m/s: część bazowa + część rosnąca z prędkością
        final double base = Config.RUDDER_STATIONARY_FACTOR.get();
        final double speedFactor = base + (1.0 - base) * speed / (speed + v0);
        final double targetYawRate = maxRate * effect * speedFactor;

        // dociągaj prędkość obrotu do celu ze stałą czasową
        final double tau = Config.RUDDER_RESPONSE_TIME.get();
        final double deltaYaw = (targetYawRate - angVel.y) * Math.min(1.0, dt / Math.max(tau, 1.0e-3));
        if (Math.abs(deltaYaw) < 1.0e-6) return;

        // Δω (świat) -> Δω (lokalnie) -> ΔL = I · Δω
        final Vector3d deltaOmegaLocal = pose.transformNormalInverse(new Vector3d(0, deltaYaw, 0));
        final Matrix3d inertia = new Matrix3d(mass.getInverseInertiaTensor()).invert();
        final Vector3d angularImpulse = inertia.transform(deltaOmegaLocal, new Vector3d());
        if (!angularImpulse.isFinite()) return;

        // ograniczenie momentu: ∝ liczba sterów · (1 + prędkość)
        final double maxImpulse = Config.RUDDER_TORQUE.get() * acc.count * (1.0 + Math.min(speed, 20.0)) * dt;
        final double len = angularImpulse.length();
        if (len > maxImpulse && len > 0) angularImpulse.mul(maxImpulse / len);

        final QueuedForceGroup group = subLevel.getOrCreateQueuedForceGroup(ForceGroups.PROPULSION.get());
        group.getForceTotal().applyTorqueImpulse(angularImpulse);
    }

    /**
     * Dodaje jeden ster, jeśli jest w wodzie.
     *
     * @param deflectionDegrees wychylenie płetwy (z wału albo z obrotu łożyska)
     */
    private static void addRudder(final ServerLevel level, final Pose3d pose, final BlockState state,
                                  final Vector3d plotPos, final double deflectionDegrees, final Accumulator acc) {
        final Vector3d world = pose.transformPosition(new Vector3d(plotPos));
        final BlockPos at = BlockPos.containing(world.x, world.y, world.z);
        // getFluidState na niezaładowanym chunku wymusiłby jego synchroniczne ładowanie - pomijamy
        if (!level.isLoaded(at) || !level.isLoaded(at.below())) return;
        if (!level.getFluidState(at).is(FluidTags.WATER) && !level.getFluidState(at.below()).is(FluidTags.WATER)) {
            return;
        }

        final Direction facing = state.getValue(RudderBlock.FACING);
        // "do przodu" statku = przeciwnie do płetwy (bez wychylenia)
        final Vector3d fwd = pose.transformNormal(new Vector3d(-facing.getStepX(), 0, -facing.getStepZ()));
        fwd.y = 0;
        if (fwd.lengthSquared() < 1.0e-6) return;
        fwd.normalize();

        // kąt płetwy dodatni (prawoskrętnie wokół +Y) = dodatnia prędkość kątowa statku wokół +Y.
        // (sprawdzone w grze – poprzedni, odwrotny znak skręcał w złą stronę)
        final double angle = Mth.clamp(deflectionDegrees, -90.0, 90.0);
        // skuteczność: rośnie do 45°, potem spada (płetwa "staje w poprzek")
        final double eff = Math.sin(Math.toRadians(2.0 * Math.abs(angle))) * Math.signum(angle);

        acc.count++;
        acc.effect += eff;
        acc.forward.add(fwd);
    }

    /** Kąt [rad] obrotu wektora a do b wokół +Y (prawoskrętnie), tylko XZ. */
    private static double signedYaw(final Vector3d a, final Vector3d b) {
        // obrót o +θ wokół +Y: (x, z) -> (x cos + z sin, -x sin + z cos)
        // => sin θ = a.z*b.x - a.x*b.z, cos θ = a.x*b.x + a.z*b.z
        final double sin = a.z * b.x - a.x * b.z;
        final double cos = a.x * b.x + a.z * b.z;
        return Math.atan2(sin, cos);
    }
}
