package com.aerovaloris.wind;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Stan wiatru po stronie klienta. Bez odwołań do klas klienckich,
 * więc można go bezpiecznie dotykać z handlera pakietu.
 */
public final class ClientWindState {
    private ClientWindState() {}

    private static double targetBearing = 0;
    private static double targetStrength = 0;
    private static double bearing = 0;
    private static double strength = 0;
    private static double prevBearing = 0;
    private static double prevStrength = 0;
    private static boolean received = false;

    public static void receive(final WindSyncPayload p) {
        targetBearing = p.bearing();
        targetStrength = p.strength();
        if (p.snap() || !received) {
            bearing = prevBearing = targetBearing;
            strength = prevStrength = targetStrength;
        }
        received = true;
    }

    /** Płynne dociąganie do wartości z serwera, raz na tick klienta. */
    public static void tick() {
        prevBearing = bearing;
        prevStrength = strength;
        final double diff = Mth.wrapDegrees(targetBearing - bearing);
        bearing = Mth.positiveModulo(bearing + diff * 0.08, 360.0);
        strength += (targetStrength - strength) * 0.08;
    }

    public static void reset() {
        received = false;
        strength = prevStrength = targetStrength = 0;
    }

    public static boolean hasData() {
        return received;
    }

    public static double bearing(final float partialTick) {
        return prevBearing + Mth.wrapDegrees(bearing - prevBearing) * partialTick;
    }

    public static double strength(final float partialTick) {
        return Mth.lerp(partialTick, prevStrength, strength);
    }

    public static Vec3 direction(final float partialTick) {
        return WindMath.bearingToVec(bearing(partialTick));
    }
}
