package com.aerovaloris.wind;

import com.aerovaloris.aeronauticsvaloris;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serwer -> klient: aktualny wiatr w wymiarze gracza. */
public record WindSyncPayload(float bearing, float strength, boolean snap) implements CustomPacketPayload {

    public static final Type<WindSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(aeronauticsvaloris.MODID, "wind_sync"));

    public static final StreamCodec<ByteBuf, WindSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, WindSyncPayload::bearing,
            ByteBufCodecs.FLOAT, WindSyncPayload::strength,
            ByteBufCodecs.BOOL, WindSyncPayload::snap,
            WindSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
