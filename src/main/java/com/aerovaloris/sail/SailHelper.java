package com.aerovaloris.sail;

import com.aerovaloris.Config;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rozpoznawanie żagli i ich "przodu".
 * <ul>
 *   <li>Symmetric Sail (Create Simulated) – ma dodaną przez mixin właściwość {@link #FRONT_POSITIVE}.</li>
 *   <li>Zwykłe żagle Create (white_sail, red_sail, ...) – przód to normalna z Sable (FACING odwrócone).</li>
 *   <li>Ramki żagli (sail_frame) nie dają ciągu.</li>
 * </ul>
 */
public final class SailHelper {
    private SailHelper() {}

    /** true = przód żagla wskazuje dodatni kierunek osi AXIS. */
    public static final BooleanProperty FRONT_POSITIVE = BooleanProperty.create("front_positive");

    public enum SailKind { NONE, CREATE_SAIL, SYMMETRIC_SAIL }

    private static final Map<Block, SailKind> CACHE = new ConcurrentHashMap<>();

    public static SailKind kind(final BlockState state) {
        return CACHE.computeIfAbsent(state.getBlock(), SailHelper::computeKind);
    }

    private static SailKind computeKind(final Block block) {
        if (block.getStateDefinition().getProperties().contains(FRONT_POSITIVE)) {
            return SailKind.SYMMETRIC_SAIL;
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if ("create".equals(id.getNamespace())
                && id.getPath().endsWith("_sail")
                && block instanceof BlockSubLevelLiftProvider) {
            return SailKind.CREATE_SAIL;
        }
        return SailKind.NONE;
    }

    public static boolean isSymmetricSail(final BlockState state) {
        return kind(state) == SailKind.SYMMETRIC_SAIL;
    }

    public static boolean isSail(final BlockState state) {
        return kind(state) != SailKind.NONE;
    }

    /**
     * Przód żagla w układzie bloku (plot/kontrapcja), albo null jeśli to nie żagiel.
     */
    @Nullable
    public static Direction front(final BlockState state) {
        return switch (kind(state)) {
            case SYMMETRIC_SAIL -> {
                final Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
                final boolean positive = state.getValue(FRONT_POSITIVE);
                yield Direction.get(positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis);
            }
            case CREATE_SAIL -> {
                Direction d = ((BlockSubLevelLiftProvider) state.getBlock()).sable$getNormal(state);
                if (Config.INVERT_CREATE_SAIL_FRONT.get()) d = d.getOpposite();
                yield d;
            }
            case NONE -> null;
        };
    }

    public static double thrustMultiplier(final SailKind kind) {
        return switch (kind) {
            case SYMMETRIC_SAIL -> Config.SYMMETRIC_SAIL_THRUST_MULTIPLIER.get();
            case CREATE_SAIL -> Config.CREATE_SAIL_THRUST_MULTIPLIER.get();
            case NONE -> 0.0;
        };
    }

    /**
     * Ustawia przód dowolnego żagla (symmetric albo Create) na podany kierunek.
     * Dla żagla Create zmienia FACING tak, żeby {@link #front(BlockState)} zwracało {@code front}.
     */
    public static BlockState withFrontAny(final BlockState state, final Direction front) {
        return switch (kind(state)) {
            case SYMMETRIC_SAIL -> withFront(state, front);
            case CREATE_SAIL -> {
                if (!state.hasProperty(BlockStateProperties.FACING)) yield state;
                // przód Create = FACING odwrócone (normalna Sable), chyba że config odwraca
                final Direction facing = Config.INVERT_CREATE_SAIL_FRONT.get() ? front : front.getOpposite();
                yield state.setValue(BlockStateProperties.FACING, facing);
            }
            case NONE -> state;
        };
    }

    /** Oś, wzdłuż której żagiel ma przód/tył. */
    @Nullable
    public static Direction.Axis frontAxis(final BlockState state) {
        final Direction f = front(state);
        return f == null ? null : f.getAxis();
    }

    /** Zwraca stan symmetric sail z przodem ustawionym na podany kierunek (oś też się zmienia). */
    public static BlockState withFront(final BlockState state, final Direction front) {
        return state.setValue(BlockStateProperties.AXIS, front.getAxis())
                .setValue(FRONT_POSITIVE, front.getAxisDirection() == Direction.AxisDirection.POSITIVE);
    }
}
