package com.aerovaloris.wind;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Wspólna matematyka wiatru.
 * <p>
 * Kierunek wiatru trzymamy jako kurs kompasowy (bearing) w stopniach,
 * w który wiatr WIEJE: 0 = północ (-Z), 90 = wschód (+X), 180 = południe (+Z), 270 = zachód (-X).
 */
public final class WindMath {
    private WindMath() {}

    /** Jednostkowy wektor poziomy dla kursu kompasowego [stopnie]. */
    public static Vec3 bearingToVec(final double bearingDeg) {
        final double r = Math.toRadians(bearingDeg);
        return new Vec3(Math.sin(r), 0.0, -Math.cos(r));
    }

    /** Kurs kompasowy [0,360) wektora (składowa Y ignorowana). */
    public static double vecToBearing(final double x, final double z) {
        double deg = Math.toDegrees(Math.atan2(x, -z));
        if (deg < 0) deg += 360.0;
        return deg;
    }

    /** Kurs kompasowy, w którą stronę patrzy gracz o danym yaw (minecraftowy yaw). */
    public static double yawToBearing(final float yaw) {
        // MC: yaw 0 = południe (+Z), 90 = zachód, 180 = północ, -90 = wschód
        return Mth.positiveModulo(yaw + 180.0, 360.0);
    }

    public static double wrapDegrees(final double deg) {
        return Mth.wrapDegrees(deg);
    }

    /**
     * Mnożnik ciągu żagla.
     *
     * @param catchDot  iloczyn skalarny (przód żagla · kierunek wiatru), -1 = pod wiatr, 1 = z wiatrem
     * @param strength  siła wiatru 0-1
     */
    public static double sailMultiplier(final double catchDot, final double strength,
                                        final double minMul, final double maxMul, final double curve) {
        final double t = Math.pow(Mth.clamp((catchDot + 1.0) * 0.5, 0.0, 1.0), curve);
        final double target = minMul + (maxMul - minMul) * t;
        // przy słabym wietrze mnożnik dąży do 1 (sam bazowy ciąg)
        return Mth.lerp(Mth.clamp(strength, 0.0, 1.0), 1.0, target);
    }

    private static final String[] DIR_KEYS = {
            "north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"
    };

    /** Nazwa kierunku (8 stron świata) dla kursu kompasowego. */
    public static Component bearingName(final double bearingDeg) {
        final int idx = Math.floorMod((int) Math.round(bearingDeg / 45.0), 8);
        return Component.translatable("aeronauticsvaloris.dir." + DIR_KEYS[idx]);
    }

    /** Nazwa kierunku dla dowolnego wektora (uwzględnia góra/dół). */
    public static Component directionName(final Vec3 v) {
        final double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
        if (Math.abs(v.y) > horiz * 1.5) {
            return Component.translatable(v.y > 0 ? "aeronauticsvaloris.dir.up" : "aeronauticsvaloris.dir.down");
        }
        return bearingName(vecToBearing(v.x, v.z));
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** Strzałka względem kierunku patrzenia (0 = przed tobą). */
    public static String relativeArrow(final double relativeDeg) {
        final int idx = Math.floorMod((int) Math.round(relativeDeg / 45.0), 8);
        return ARROWS[idx];
    }
}
