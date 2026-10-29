package com.aerovaloris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

/**
 * Obejście crasha {@code ConcurrentModificationException} w
 * {@code PersistentEntitySectionManager.updateChunkStatus} (dedyk + statki Sable).
 * <p>
 * Przy zmianie statusu chunka vanilla przechodzi po żywej liście encji sekcji i dla każdej
 * woła callbacki (koniec tickowania / śledzenia → m.in. EntityLeaveLevelEvent). Jeśli któryś z nich
 * (Sable, siedzenia/kontrapcje Create, inny mod) doda albo usunie encję w tej samej sekcji,
 * iterator się wywraca i serwer crashuje.
 * <p>
 * Tutaj każde {@code EntitySection.getEntities()} w tej klasie zwraca strumień po <b>kopii</b> listy,
 * więc modyfikacja w trakcie nie może już przerwać iteracji. Tak samo robi np. Paper.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin {

    @WrapOperation(
            method = "*",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntitySection;getEntities()Ljava/util/stream/Stream;"),
            require = 0)
    private Stream<?> aeronauticsvaloris$snapshotEntities(final EntitySection<?> section,
                                                          final Operation<Stream<?>> original) {
        return original.call(section).toList().stream();
    }
}
