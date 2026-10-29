package com.aerovaloris.mixin;

import com.aerovaloris.Config;
import com.aerovaloris.sail.SailHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sable sam liczy siłę nośną i opór żagli (jak skrzydeł). Gdy żagiel na bearingu jest obrócony,
 * ta siła działa bokiem na wysokości masztu i daje moment przechylający statek na burtę.
 * <p>
 * Tutaj owijamy to wywołanie: siła liniowa (opór, dryf) zostaje, a moment obrotowy od żagli
 * jest skalowany przez {@code sailAerodynamicTorque} (domyślnie 0 = żagle nie przechylają statku).
 */
@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelMixin {

    @Unique
    private final Vector3d aeronauticsvaloris$angBefore = new Vector3d();

    @WrapOperation(
            method = "prePhysicsTick",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider;sable$contributeLiftAndDrag(Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderContext;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/companion/math/Pose3d;DLorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;Lorg/joml/Vector3d;Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderGroup;)V"),
            remap = false,
            require = 0)
    private void aeronauticsvaloris$scaleSailTorque(final BlockSubLevelLiftProvider provider,
                                                    final BlockSubLevelLiftProvider.LiftProviderContext ctx,
                                                    final ServerSubLevel subLevel, final Pose3d localPose,
                                                    final double timeStep,
                                                    final Vector3dc linearVelocity, final Vector3dc angularVelocity,
                                                    final Vector3d linearImpulse, final Vector3d angularImpulse,
                                                    final BlockSubLevelLiftProvider.LiftProviderGroup group,
                                                    final Operation<Void> original) {
        if (!SailHelper.isSail(ctx.state())) {
            original.call(provider, ctx, subLevel, localPose, timeStep, linearVelocity, angularVelocity,
                    linearImpulse, angularImpulse, group);
            return;
        }

        this.aeronauticsvaloris$angBefore.set(angularImpulse);
        original.call(provider, ctx, subLevel, localPose, timeStep, linearVelocity, angularVelocity,
                linearImpulse, angularImpulse, group);

        // before + (after - before) * scale
        final double scale = Config.SAIL_AERO_TORQUE.get();
        angularImpulse.sub(this.aeronauticsvaloris$angBefore).mul(scale).add(this.aeronauticsvaloris$angBefore);
    }
}
