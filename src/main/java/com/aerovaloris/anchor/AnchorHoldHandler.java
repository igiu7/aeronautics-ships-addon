package com.aerovaloris.anchor;

import com.aerovaloris.Config;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Kotwica na dnie trzyma statek w miejscu:
 * <ul>
 *   <li>poziomo – prędkość dociągana do zera, a punkt zaczepu liny ściągany z powrotem
 *       do miejsca, w którym kotwica chwyciła (statek nie dryfuje od wiatru/żagli),</li>
 *   <li>obrót wokół osi pionowej – wygaszany do zera.</li>
 * </ul>
 * Pion i przechyły zostają fizyce (wyporność High Seas / Sable).
 */
public final class AnchorHoldHandler {
    private AnchorHoldHandler() {}

    public static void onPrePhysicsTick(final ForgeSablePrePhysicsTickEvent event) {
        final SubLevelPhysicsSystem system = event.getPhysicsSystem();
        final ServerLevel level = system.getLevel();
        final double dt = event.getTimeStep();

        for (final AnchorEntity anchor : AnchorManager.all(level)) {
            if (anchor.getState() != AnchorEntity.State.LANDED) continue;
            if (!level.isLoaded(anchor.getWinchPos())) continue;

            final SubLevel sub = Sable.HELPER.getContaining(level, anchor.getWinchPos());
            if (!(sub instanceof final ServerSubLevel ship) || ship.isRemoved()) continue;
            hold(system, ship, anchor, dt);
        }
    }

    private static void hold(final SubLevelPhysicsSystem system, final ServerSubLevel ship,
                             final AnchorEntity anchor, final double dt) {
        final MassData mass = ship.getMassTracker();
        if (mass == null || mass.isInvalid()) return;
        final RigidBodyHandle handle = system.getPhysicsHandle(ship);
        if (handle == null) return;

        final Pose3d pose = ship.logicalPose();
        final Vec3 local = AnchorEntity.attachLocal(anchor.getWinchPos(), anchor.getFacing());
        final Vector3d attach = pose.transformPosition(new Vector3d(local.x, local.y, local.z));

        final Vector3d lin = handle.getLinearVelocity(new Vector3d());
        final Vector3d ang = handle.getAngularVelocity(new Vector3d());

        final double response = Math.max(1.0e-3, Config.ANCHOR_HOLD_RESPONSE.get());
        final double k = Math.min(1.0, dt / response);

        // docelowa prędkość: powrót zaczepu nad miejsce chwycenia (sprężyna, ograniczona)
        double tvx = (anchor.getHoldX() - attach.x) / 1.0;
        double tvz = (anchor.getHoldZ() - attach.z) / 1.0;
        final double tv = Math.sqrt(tvx * tvx + tvz * tvz);
        final double maxReturn = 3.0;
        if (tv > maxReturn) {
            tvx *= maxReturn / tv;
            tvz *= maxReturn / tv;
        }

        final double m = mass.getMass();
        final Vector3d impulseWorld = new Vector3d((tvx - lin.x) * m * k, 0, (tvz - lin.z) * m * k);
        final Vector3d impulseLocal = pose.transformNormalInverse(impulseWorld);
        if (!impulseLocal.isFinite()) return;

        final QueuedForceGroup group = ship.getOrCreateQueuedForceGroup(ForceGroups.DRAG.get());
        group.applyAndRecordPointForce(new Vector3d(mass.getCenterOfMass()), impulseLocal);

        // wygaszenie obrotu wokół pionu
        final double deltaYaw = -ang.y * k;
        if (Math.abs(deltaYaw) > 1.0e-7) {
            final Vector3d dOmegaLocal = pose.transformNormalInverse(new Vector3d(0, deltaYaw, 0));
            final Matrix3d inertia = new Matrix3d(mass.getInverseInertiaTensor()).invert();
            final Vector3d angImpulse = inertia.transform(dOmegaLocal, new Vector3d());
            if (angImpulse.isFinite()) group.getForceTotal().applyTorqueImpulse(angImpulse);
        }
    }
}
