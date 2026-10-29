package com.aerovaloris.wind;

import com.aerovaloris.aeronauticsvaloris;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Serwer -> klient: pozycje żagli na statkach w pobliżu gracza (współrzędne plotu Sable,
 * spakowane jak BlockPos.asLong). Klient przelicza je na świat przez pozę statku
 * i rysuje smugi wiatru tylko wokół nich.
 */
public record SailPointsPayload(List<Long> points) implements CustomPacketPayload {

    public static final int MAX_POINTS = 2048;

    public static final Type<SailPointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(aeronauticsvaloris.MODID, "sail_points"));

    public static final StreamCodec<ByteBuf, SailPointsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MAX_POINTS)), SailPointsPayload::points,
            SailPointsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
