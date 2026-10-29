package com.aerovaloris.mixin;

import com.aerovaloris.sail.SailHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dodaje Symmetric Sail z Create Simulated przód i tył (właściwość {@code front_positive}).
 * <p>
 * Modele bloku nie wymagają zmian – warianty w blockstate JSON dopasowują się tylko po {@code axis},
 * więc nowa właściwość jest ignorowana przy renderowaniu.
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.symmetric_sail.SymmetricSailBlock")
public abstract class SymmetricSailBlockMixin extends RotatedPillarBlock {

    protected SymmetricSailBlockMixin(final Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SailHelper.FRONT_POSITIVE);
    }

    /**
     * Przód = kierunek, w którym patrzy gracz (gracz stoi ZA żaglem).
     * To tylko wstępna wartość (m.in. dla podglądu po stronie klienta) – ostateczny przód ustawia
     * {@link com.aerovaloris.sail.SailInteractionHandler#onBlockPlaced}, który obsługuje też
     * stawianie przez placement helper (klik w istniejący żagiel), gdzie getStateForPlacement nie jest wołane.
     */
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void aeronauticsvaloris$setFront(final BlockPlaceContext ctx, final CallbackInfoReturnable<BlockState> cir) {
        final BlockState state = cir.getReturnValue();
        if (state == null || !state.hasProperty(SailHelper.FRONT_POSITIVE)) return;

        final Direction look = ctx.getNearestLookingDirection();
        cir.setReturnValue(state.setValue(SailHelper.FRONT_POSITIVE,
                look.getAxisDirection() == Direction.AxisDirection.POSITIVE));
    }

    /** Obrót (struktury, kontrapcje, schematy) obraca też przód. */
    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        if (!state.hasProperty(SailHelper.FRONT_POSITIVE)) return super.rotate(state, rotation);
        final Direction front = SailHelper.front(state);
        if (front == null) return super.rotate(state, rotation);
        return SailHelper.withFront(state, rotation.rotate(front));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        if (!state.hasProperty(SailHelper.FRONT_POSITIVE)) return super.mirror(state, mirror);
        final Direction front = SailHelper.front(state);
        if (front == null) return super.mirror(state, mirror);
        return SailHelper.withFront(state, mirror.mirror(front));
    }
}
