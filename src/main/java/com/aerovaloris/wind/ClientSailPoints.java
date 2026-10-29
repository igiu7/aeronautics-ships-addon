package com.aerovaloris.wind;

/**
 * Ostatnio otrzymane pozycje żagli (współrzędne plotu, BlockPos.asLong).
 * Bez klas klienckich – bezpieczne do dotknięcia z handlera pakietu.
 */
public final class ClientSailPoints {
    private ClientSailPoints() {}

    private static volatile long[] points = new long[0];

    public static void receive(final SailPointsPayload p) {
        final long[] arr = new long[p.points().size()];
        for (int i = 0; i < arr.length; i++) arr[i] = p.points().get(i);
        points = arr;
    }

    public static long[] get() {
        return points;
    }

    public static void reset() {
        points = new long[0];
    }
}
