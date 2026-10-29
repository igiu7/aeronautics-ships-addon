package com.aerovaloris.wind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tickowanie wiatru po stronie serwera i synchronizacja z klientami. */
public final class WindManager {
    private WindManager() {}

    private static final int SYNC_INTERVAL = 10;

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(WindSyncPayload.TYPE, WindSyncPayload.STREAM_CODEC, WindManager::handleClient)
                .playToClient(SailPointsPayload.TYPE, SailPointsPayload.STREAM_CODEC,
                        (payload, ctx) -> ctx.enqueueWork(() -> ClientSailPoints.receive(payload)));
    }

    private static void handleClient(final WindSyncPayload payload, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientWindState.receive(payload));
    }

    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof final ServerLevel level)) return;
        if (!level.dimensionType().hasSkyLight()) return;

        final WindData data = WindData.get(level);
        data.tick(level);

        if (level.getGameTime() % SYNC_INTERVAL == 0) {
            sync(level, false);
        }
        if (level.getGameTime() % 20 == 5) {
            SailPointSync.sync(level);
        }
    }

    public static WindSyncPayload payloadFor(final ServerLevel level, final boolean snap) {
        final WindData data = WindData.get(level);
        return new WindSyncPayload((float) data.bearing(), (float) data.effectiveStrength(level), snap);
    }

    public static void sync(final ServerLevel level, final boolean snap) {
        PacketDistributor.sendToPlayersInDimension(level, payloadFor(level, snap));
    }

    public static void syncTo(final ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payloadFor(player.serverLevel(), true));
    }

    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof final ServerPlayer sp) syncTo(sp);
    }

    public static void onChangeDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof final ServerPlayer sp) syncTo(sp);
    }

    public static void onRespawn(final PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof final ServerPlayer sp) syncTo(sp);
    }
}
