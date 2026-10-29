package com.aerovaloris.anchor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rejestr kotwic po stronie serwera: kabestan (pozycja bloku) → encja kotwicy.
 * Pozwala znaleźć kotwicę z bloku (prawy klik) i z fizyki (trzymanie statku),
 * nawet gdy kotwica leży daleko od plotu statku.
 */
public final class AnchorManager {
    private AnchorManager() {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, AnchorEntity>> ANCHORS = new HashMap<>();

    private static Map<BlockPos, AnchorEntity> map(final Level level) {
        return ANCHORS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
    }

    /** @return false, jeśli ten kabestan ma już inną, żywą kotwicę */
    public static boolean register(final AnchorEntity anchor) {
        final Map<BlockPos, AnchorEntity> m = map(anchor.level());
        final BlockPos key = anchor.getWinchPos();
        final AnchorEntity existing = m.get(key);
        if (existing == null || existing.isRemoved()) {
            m.put(key, anchor);
            return true;
        }
        return existing == anchor;
    }

    public static void unregister(final AnchorEntity anchor) {
        final Map<BlockPos, AnchorEntity> m = ANCHORS.get(anchor.level().dimension());
        if (m != null) m.remove(anchor.getWinchPos(), anchor);
    }

    @Nullable
    public static AnchorEntity get(final Level level, final BlockPos winch) {
        final Map<BlockPos, AnchorEntity> m = ANCHORS.get(level.dimension());
        if (m == null) return null;
        final AnchorEntity e = m.get(winch);
        return e == null || e.isRemoved() ? null : e;
    }

    /** Żywe kotwice w wymiarze; usunięte (rozładowane, zniszczone) są przy okazji wyrzucane z rejestru. */
    public static Collection<AnchorEntity> all(final Level level) {
        final Map<BlockPos, AnchorEntity> m = ANCHORS.get(level.dimension());
        if (m == null || m.isEmpty()) return List.of();
        m.values().removeIf(AnchorEntity::isRemoved);
        return new ArrayList<>(m.values());
    }

    /** Czy statek ma kotwicę leżącą na dnie (wtedy żagle nie pchają statku). */
    public static boolean isAnchored(final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        final Level level = ship.getLevel();
        for (final AnchorEntity a : all(level)) {
            if (a.getState() != AnchorEntity.State.LANDED) continue;
            if (!level.isLoaded(a.getWinchPos())) continue;
            if (dev.ryanhcode.sable.Sable.HELPER.getContaining(level, a.getWinchPos()) == ship) return true;
        }
        return false;
    }

    public static void onServerStopped(final ServerStoppedEvent event) {
        ANCHORS.clear();
    }

    /** Prawy klik w kabestan. */
    public static void toggle(final ServerLevel level, final BlockPos pos, final BlockState state, final Player player) {
        AnchorEntity anchor = state.getValue(AnchorWinchBlock.DEPLOYED) ? get(level, pos) : null;

        if (anchor == null) {
            // zrzut (także gdy blok mówi "zrzucona", ale kotwica zginęła – np. po złożeniu statku)
            anchor = AnchorEntity.create(level, pos, state.getValue(AnchorWinchBlock.FACING));
            level.setBlock(pos, state.setValue(AnchorWinchBlock.DEPLOYED, true), 3);
            level.addFreshEntity(anchor);
            register(anchor);
            level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
            message(player, Component.translatable("aeronauticsvaloris.anchor.dropping"));
            return;
        }

        switch (anchor.getState()) {
            case DROPPING, LANDED -> {
                anchor.startRaising();
                message(player, Component.translatable("aeronauticsvaloris.anchor.raising",
                        String.format("%.0f", Math.ceil(anchor.raiseSeconds()))));
            }
            case RAISING -> {
                anchor.startDropping();
                message(player, Component.translatable("aeronauticsvaloris.anchor.dropping"));
            }
        }
        level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8f, 0.7f);
    }

    private static void message(final Player player, final Component text) {
        if (player != null) player.displayClientMessage(text.copy().withStyle(ChatFormatting.AQUA), true);
    }
}
