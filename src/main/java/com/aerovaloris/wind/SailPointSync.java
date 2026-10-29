package com.aerovaloris.wind;

import com.aerovaloris.sail.SailThrustHandler;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

/**
 * Raz na sekundę wysyła graczom pozycje żagli na pobliskich statkach,
 * żeby klient wiedział, gdzie rysować smugi wiatru.
 */
public final class SailPointSync {
    private SailPointSync() {}

    /** Z jakiej odległości od statku gracz dostaje jego żagle. */
    private static final double SEND_RANGE = 160.0;
    /** Siatka uśredniania punktów (bloki) – mniej danych, ten sam efekt. */
    private static final int GRID = 2;

    private record ShipSails(double x, double y, double z, long[] points) {}

    public static void sync(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        final List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        final List<ShipSails> ships = new ArrayList<>();
        final List<SailThrustHandler.SailEntry> tmp = new ObjectArrayList<>();
        final LongOpenHashSet set = new LongOpenHashSet();

        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            tmp.clear();
            set.clear();
            SailThrustHandler.collectSails(subLevel, 1.0, tmp);
            if (tmp.isEmpty()) continue;

            for (final SailThrustHandler.SailEntry e : tmp) {
                final int x = Mth.floor(e.pos().x() / GRID) * GRID;
                final int y = Mth.floor(e.pos().y() / GRID) * GRID;
                final int z = Mth.floor(e.pos().z() / GRID) * GRID;
                set.add(BlockPos.asLong(x, y, z));
            }
            final Vector3dc p = subLevel.logicalPose().position();
            ships.add(new ShipSails(p.x(), p.y(), p.z(), set.toLongArray()));
        }

        final double r2 = SEND_RANGE * SEND_RANGE;
        for (final ServerPlayer player : players) {
            final LongArrayList out = new LongArrayList();
            for (final ShipSails ship : ships) {
                if (player.distanceToSqr(ship.x(), ship.y(), ship.z()) > r2) continue;
                for (final long l : ship.points()) {
                    if (out.size() >= SailPointsPayload.MAX_POINTS) break;
                    out.add(l);
                }
            }
            PacketDistributor.sendToPlayer(player, new SailPointsPayload(new ArrayList<>(out)));
        }
    }
}
