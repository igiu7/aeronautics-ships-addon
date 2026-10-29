package com.aerovaloris.physics;

import com.aerovaloris.Config;
import com.aerovaloris.rudder.RudderBlock;
import com.aerovaloris.sail.SailHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Stabilizator statków na wodzie (statki z żaglami albo sterem z tego moda, które unoszą się na wodzie).
 * <p>
 * Działa jak bardzo ciężki balast w kilu: gdy pokład się przechyla (na burtę albo na dziób/rufę),
 * statek jest prostowany z powrotem i wygaszane są kołysania. Moment jest liczony tak, żeby zmieniał
 * WYŁĄCZNIE przechył i przegłębienie – obrót w poziomie (skręt sterem) zostaje nietknięty.
 * <p>
 * Dzięki temu można robić ostre zakręty bez wywracania i statek nie "nurkuje" dziobem po skręcie
 * (precesja przy obrocie wokół nieosiowej bezwładności).
 */
public final class ShipStabilizer {
    private ShipStabilizer() {}

    private static final Vector3dc WORLD_UP = new Vector3d(0, 1, 0);

    public static void onPrePhysicsTick(final ForgeSablePrePhysicsTickEvent event) {
        final double strength = Config.STABILIZER_STRENGTH.get();
        if (strength <= 0) return;

        final SubLevelPhysicsSystem system = event.getPhysicsSystem();
        final ServerLevel level = system.getLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        for (final ServerSubLevel ship : container.getAllSubLevels()) {
            if (ship.isRemoved()) continue;
            if (!hasShipParts(ship, system)) continue;
            stabilize(system, level, ship, event.getTimeStep(), strength);
        }
    }

    /** Czy statek ma ster albo żagle (bezpośrednio lub na kontrapcjach). */
    private static boolean hasShipParts(final ServerSubLevel ship, final SubLevelPhysicsSystem system) {
        final ServerLevelPlot plot = ship.getPlot();
        for (final BlockSubLevelLiftProvider.LiftProviderContext ctx : plot.getLiftProviders()) {
            if (isPart(ctx.state())) return true;
        }
        for (final KinematicContraption c : plot.getContraptions()) {
            if (!c.sable$isValid()) continue;
            for (final BlockSubLevelLiftProvider.LiftProviderContext ctx : c.sable$liftProviders().values()) {
                if (isPart(ctx.state())) return true;
            }
        }
        return false;
    }

    private static boolean isPart(final BlockState state) {
        return state.getBlock() instanceof RudderBlock || SailHelper.isSail(state);
    }

    /** Czy pod środkiem masy (do kilku bloków w dół) jest woda – stabilizujemy tylko statki pływające. */
    private static boolean onWater(final ServerLevel level, final Vector3d comWorld) {
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= 8; dy++) {
            p.set(comWorld.x, comWorld.y - dy, comWorld.z);
            if (!level.isLoaded(p)) return false;
            if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static void stabilize(final SubLevelPhysicsSystem system, final ServerLevel level,
                                  final ServerSubLevel ship, final double dt, final double strength) {
        final MassData mass = ship.getMassTracker();
        if (mass == null || mass.isInvalid()) return;
        final Pose3d pose = ship.logicalPose();

        final Vector3d comWorld = pose.transformPosition(new Vector3d(mass.getCenterOfMass()));
        if (!onWater(level, comWorld)) return;

        final RigidBodyHandle handle = system.getPhysicsHandle(ship);
        if (handle == null) return;
        final Vector3d ang = handle.getAngularVelocity(new Vector3d());

        // oś "do góry" pokładu w świecie i kąt przechyłu
        final Vector3d up = pose.transformNormal(new Vector3d(0, 1, 0)).normalize();
        final Vector3d axis = up.cross(WORLD_UP, new Vector3d()); // obrót, który prostuje pokład
        final double sin = axis.length();
        final double angle = Math.atan2(sin, up.dot(WORLD_UP));

        final double uprightTime = Math.max(0.05, Config.STABILIZER_UPRIGHT_TIME.get());
        final Vector3d targetOmega = new Vector3d();
        if (sin > 1.0e-6) {
            // martwa strefa: drobne kołysanie z fal High Seas zostaje
            final double dead = Math.toRadians(Config.STABILIZER_DEADZONE.get());
            final double excess = Math.max(0.0, angle - dead);
            axis.normalize().mul(excess / uprightTime, targetOmega);
        }

        // tylko poziome składowe prędkości kątowej (przechył + przegłębienie); obrót wokół pionu zostaje
        final Vector3d omegaH = new Vector3d(ang.x, 0, ang.z);
        final double k = Math.min(1.0, dt / Math.max(1.0e-3, Config.STABILIZER_DAMPING_TIME.get())) * strength;
        final Vector3d dOmegaWorld = targetOmega.sub(omegaH, new Vector3d()).mul(k);
        dOmegaWorld.y = 0;
        if (dOmegaWorld.lengthSquared() < 1.0e-14) return;

        // ΔL = I · Δω, w układzie lokalnym statku
        final Vector3d dOmegaLocal = pose.transformNormalInverse(new Vector3d(dOmegaWorld));
        final Matrix3d inertia = new Matrix3d(mass.getInverseInertiaTensor()).invert();
        final Vector3d angImpulse = inertia.transform(dOmegaLocal, new Vector3d());
        if (!angImpulse.isFinite()) return;

        ship.getOrCreateQueuedForceGroup(ForceGroups.LEVITATION.get())
                .getForceTotal().applyTorqueImpulse(angImpulse);
    }
}
