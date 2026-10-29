package com.aerovaloris;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Konfiguracja moda.
 * SERVER - wartości rozgrywki (synchronizowane do klientów, osobne per świat).
 * CLIENT - wizualia (smugi wiatru, HUD, strzałka przodu żagla).
 */
public final class Config {
    private Config() {}

    // =====================================================================
    // SERVER
    // =====================================================================
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    // --- Ciąg żagli ---
    public static final ModConfigSpec.DoubleValue BASE_THRUST_PER_SAIL;
    public static final ModConfigSpec.DoubleValue CREATE_SAIL_THRUST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SYMMETRIC_SAIL_THRUST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SAIL_COUNT_EXPONENT;
    public static final ModConfigSpec.BooleanValue APPLY_THRUST_AT_CENTER_OF_MASS;
    public static final ModConfigSpec.DoubleValue MAX_SAIL_TURN_ANGLE;
    public static final ModConfigSpec.BooleanValue INVERT_CREATE_SAIL_FRONT;
    public static final ModConfigSpec.DoubleValue SAIL_AERO_TORQUE;

    // --- Wpływ wiatru na ciąg ---
    public static final ModConfigSpec.DoubleValue WIND_MIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WIND_MAX_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WIND_CURVE;

    // --- Zachowanie wiatru ---
    public static final ModConfigSpec.IntValue WIND_CHANGE_MIN_SECONDS;
    public static final ModConfigSpec.IntValue WIND_CHANGE_MAX_SECONDS;
    public static final ModConfigSpec.DoubleValue WIND_MAX_SHIFT_DEGREES;
    public static final ModConfigSpec.DoubleValue WIND_TURN_SPEED;
    public static final ModConfigSpec.DoubleValue WIND_MIN_STRENGTH;
    public static final ModConfigSpec.DoubleValue WIND_MAX_STRENGTH;
    public static final ModConfigSpec.DoubleValue RAIN_STRENGTH_BONUS;
    public static final ModConfigSpec.DoubleValue THUNDER_STRENGTH_BONUS;

    // --- Ster ---
    public static final ModConfigSpec.DoubleValue RUDDER_MAX_TURN_RATE;
    public static final ModConfigSpec.DoubleValue RUDDER_SPEED_HALF;
    public static final ModConfigSpec.DoubleValue RUDDER_MIN_SPEED;
    public static final ModConfigSpec.DoubleValue RUDDER_STATIONARY_FACTOR;
    public static final ModConfigSpec.DoubleValue RUDDER_RESPONSE_TIME;
    public static final ModConfigSpec.DoubleValue RUDDER_TORQUE;
    public static final ModConfigSpec.DoubleValue RUDDER_SHAFT_RANGE;
    public static final ModConfigSpec.DoubleValue RUDDER_MAX_DEFLECTION;
    public static final ModConfigSpec.BooleanValue RUDDER_INVERT;

    // --- Kotwica ---
    public static final ModConfigSpec.DoubleValue ANCHOR_DROP_SPEED;
    public static final ModConfigSpec.DoubleValue ANCHOR_RAISE_SPEED;
    public static final ModConfigSpec.DoubleValue ANCHOR_MAX_ROPE;
    public static final ModConfigSpec.DoubleValue ANCHOR_HOLD_RESPONSE;

    // --- Stabilność ---
    public static final ModConfigSpec.DoubleValue STABILIZER_STRENGTH;
    public static final ModConfigSpec.DoubleValue STABILIZER_UPRIGHT_TIME;
    public static final ModConfigSpec.DoubleValue STABILIZER_DAMPING_TIME;
    public static final ModConfigSpec.DoubleValue STABILIZER_DEADZONE;
    public static final ModConfigSpec.DoubleValue KEEL_RESPONSE;
    public static final ModConfigSpec.DoubleValue KEEL_KEEP_SPEED;

    static {
        final ModConfigSpec.Builder b = SERVER_BUILDER;

        b.comment("Ciąg generowany przez żagle na statkach (sub-levelach Sable)").push("sails");
        BASE_THRUST_PER_SAIL = b
                .comment("Bazowy ciąg jednego bloku żagla przy braku wiatru [N]. Domyślnie mały.",
                        "Dla porównania: śmigło andezytowe ma ciąg ~1.0 * obroty.")
                .defineInRange("baseThrustPerSail", 15.0, 0.0, 10000.0);
        CREATE_SAIL_THRUST_MULTIPLIER = b
                .comment("Mnożnik ciągu dla zwykłych żagli z Create (białe/kolorowe, bez ramek).")
                .defineInRange("createSailThrustMultiplier", 1.0, 0.0, 100.0);
        SYMMETRIC_SAIL_THRUST_MULTIPLIER = b
                .comment("Mnożnik ciągu dla Symmetric Sail (domyślnie taki sam jak zwykły żagiel).")
                .defineInRange("symmetricSailThrustMultiplier", 1.0, 0.0, 100.0);
        SAIL_COUNT_EXPONENT = b
                .comment("Jak całkowity ciąg rośnie z liczbą żagli: ciąg = bazowy * liczba^wykładnik.",
                        "1.0 = liniowo, <1.0 = malejące zyski (np. 0.85).")
                .defineInRange("sailCountExponent", 1.0, 0.1, 2.0);
        APPLY_THRUST_AT_CENTER_OF_MASS = b
                .comment("true = ciąg przykładany w środku masy (statek się nie przechyla ani nie obraca od żagli).",
                        "false = ciąg przykładany w miejscu każdego żagla (realistycznie, ale wysokie maszty pochylają dziób).")
                .define("applyThrustAtCenterOfMass", true);
        MAX_SAIL_TURN_ANGLE = b
                .comment("Maksymalny kąt [stopnie], o jaki żagle na łożysku (mechanical bearing) mogą być obrócone względem przodu statku.",
                        "Obrót ponad ten kąt nie daje lepszego łapania wiatru (jak w Sea of Thieves - nie da się 'oszukać' wiatru obracając żagle tyłem).")
                .defineInRange("maxSailTurnAngle", 90.0, 0.0, 180.0);
        INVERT_CREATE_SAIL_FRONT = b
                .comment("Odwraca przód zwykłych żagli z Create, jeśli w twojej wersji wychodzi odwrotnie.")
                .define("invertCreateSailFront", false);
        SAIL_AERO_TORQUE = b
                .comment("Skala momentu obrotowego z aerodynamiki Sable dla żagli (siła nośna/opór działające na wysokości masztu).",
                        "0 = żagle nie przechylają statku na burtę (także obrócone bearingiem), 1 = oryginalne zachowanie Sable.",
                        "Siła liniowa (opór, dryf) zostaje bez zmian.")
                .defineInRange("sailAerodynamicTorque", 0.0, 0.0, 1.0);
        b.pop();

        b.comment("Jak kąt żagla względem wiatru mnoży ciąg").push("wind_effect");
        WIND_MIN_MULTIPLIER = b
                .comment("Mnożnik ciągu przy wietrze wiejącym prosto w przód żagla (pod wiatr) przy pełnej sile wiatru.")
                .defineInRange("windMinMultiplier", 0.15, 0.0, 10.0);
        WIND_MAX_MULTIPLIER = b
                .comment("Mnożnik ciągu przy wietrze wiejącym prosto w plecy żagla (z wiatrem) przy pełnej sile wiatru.")
                .defineInRange("windMaxMultiplier", 2.5, 0.0, 50.0);
        WIND_CURVE = b
                .comment("Kształt krzywej: 1.0 = liniowo od kąta, >1 = trzeba dokładniej ustawić żagiel, <1 = łagodniej.")
                .defineInRange("windCurve", 1.5, 0.1, 5.0);
        b.pop();

        b.comment("Globalny wiatr w stylu Sea of Thieves").push("wind");
        WIND_CHANGE_MIN_SECONDS = b
                .comment("Minimalny czas [s] do kolejnej zmiany kierunku wiatru.")
                .defineInRange("windChangeMinSeconds", 900, 5, 36000);
        WIND_CHANGE_MAX_SECONDS = b
                .comment("Maksymalny czas [s] do kolejnej zmiany kierunku wiatru.")
                .defineInRange("windChangeMaxSeconds", 900, 5, 36000);
        WIND_MAX_SHIFT_DEGREES = b
                .comment("Maksymalna zmiana kierunku przy jednej zmianie wiatru [stopnie].")
                .defineInRange("windMaxShiftDegrees", 135.0, 0.0, 180.0);
        WIND_TURN_SPEED = b
                .comment("Jak szybko wiatr skręca do nowego kierunku [stopnie/s]. 1.5 = obrót o 90° trwa ok. minuty.")
                .defineInRange("windTurnSpeed", 1.5, 0.01, 360.0);
        WIND_MIN_STRENGTH = b
                .comment("Minimalna siła wiatru (0-1).")
                .defineInRange("windMinStrength", 0.35, 0.0, 1.0);
        WIND_MAX_STRENGTH = b
                .comment("Maksymalna siła wiatru (0-1).")
                .defineInRange("windMaxStrength", 1.0, 0.0, 1.0);
        RAIN_STRENGTH_BONUS = b
                .comment("Dodatkowa siła wiatru podczas deszczu.")
                .defineInRange("rainStrengthBonus", 0.15, 0.0, 1.0);
        THUNDER_STRENGTH_BONUS = b
                .comment("Dodatkowa siła wiatru podczas burzy (sumuje się z deszczem).")
                .defineInRange("thunderStrengthBonus", 0.3, 0.0, 1.0);
        b.pop();

        b.comment("Ster (rudder). Docelowa prędkość skrętu = maxTurnRate * wychylenie * v / (v + speedForHalfTurnRate).",
                "Szybciej = szybszy skręt, ale większe koło.").push("rudder");
        RUDDER_MAX_TURN_RATE = b
                .comment("Maksymalna prędkość obrotu statku przy pełnym wychyleniu i dużej prędkości [stopnie/s].")
                .defineInRange("maxTurnRate", 40.0, 0.0, 360.0);
        RUDDER_SPEED_HALF = b
                .comment("Prędkość [m/s], przy której statek osiąga połowę maxTurnRate. Większa = większe koła przy małej prędkości.")
                .defineInRange("speedForHalfTurnRate", 3.0, 0.1, 100.0);
        RUDDER_MIN_SPEED = b
                .comment("Poniżej tej prędkości [m/s] ster nic nie robi.")
                .defineInRange("minSpeed", 0.0, 0.0, 20.0);
        RUDDER_STATIONARY_FACTOR = b
                .comment("Jaka część maxTurnRate jest dostępna już przy 0 m/s (0 = brak skrętu w miejscu, 1 = pełny skręt niezależnie od prędkości).")
                .defineInRange("stationaryTurnFactor", 0.35, 0.0, 1.0);
        RUDDER_RESPONSE_TIME = b
                .comment("Jak szybko statek dochodzi do docelowej prędkości skrętu [s].")
                .defineInRange("responseTime", 0.4, 0.01, 30.0);
        RUDDER_TORQUE = b
                .comment("Maksymalny moment jednego steru [Nm], mnożony przez (1 + prędkość w m/s). Ogranicza skręt ciężkich statków.")
                .defineInRange("torquePerSpeed", 3000.0, 0.0, 1.0e7);
        RUDDER_SHAFT_RANGE = b
                .comment("Ile stopni obrotu wału daje pełne wychylenie steru.",
                        "Ustaw tak samo jak limit kąta w Steering Wheel (domyślnie 180°), żeby pełny skręt koła = pełne wychylenie.")
                .defineInRange("shaftDegreesForFullDeflection", 180.0, 1.0, 3600.0);
        RUDDER_MAX_DEFLECTION = b
                .comment("Maksymalne wychylenie płetwy steru [stopnie].")
                .defineInRange("maxDeflection", 45.0, 1.0, 90.0);
        RUDDER_INVERT = b
                .comment("Odwraca kierunek skrętu względem obrotu wału (gdyby koło skręcało w złą stronę).")
                .define("invertSteering", false);
        b.pop();

        b.comment("Kotwica (kabestan na statku)").push("anchor");
        ANCHOR_DROP_SPEED = b
                .comment("Prędkość opadania kotwicy [bloki/s].")
                .defineInRange("dropSpeed", 8.0, 0.5, 100.0);
        ANCHOR_RAISE_SPEED = b
                .comment("Prędkość wciągania kotwicy [bloki/s]. Czas wciągania = głębokość / ta wartość.")
                .defineInRange("raiseSpeed", 1.5, 0.05, 100.0);
        ANCHOR_MAX_ROPE = b
                .comment("Maksymalna długość liny [bloki]. Jeśli kotwica nie sięgnie dna, wisi i nie trzyma statku.")
                .defineInRange("maxRopeLength", 128.0, 4.0, 512.0);
        ANCHOR_HOLD_RESPONSE = b
                .comment("Jak szybko kotwica na dnie zatrzymuje statek [s]. Mniej = ostrzejsze szarpnięcie.")
                .defineInRange("holdResponse", 0.15, 0.01, 30.0);
        b.pop();

        b.comment("Stabilność statków na wodzie (statki z żaglami albo sterem)").push("stability");
        STABILIZER_STRENGTH = b
                .comment("Siła prostowania statku (przechył na burtę i na dziób/rufę). 0 = wyłączone, 1 = pełne.",
                        "Nie wpływa na skręcanie (obrót w poziomie).")
                .defineInRange("stabilizerStrength", 1.0, 0.0, 1.0);
        STABILIZER_UPRIGHT_TIME = b
                .comment("W jakim czasie [s] statek wraca do pionu z przechyłu. Mniej = sztywniejszy statek.")
                .defineInRange("uprightTime", 0.8, 0.05, 30.0);
        STABILIZER_DAMPING_TIME = b
                .comment("Jak szybko [s] wygaszane jest kołysanie na burtę i na dziób.")
                .defineInRange("rollDampingTime", 0.25, 0.01, 30.0);
        STABILIZER_DEADZONE = b
                .comment("Przechył [stopnie], którego stabilizator nie poprawia (lekkie kołysanie na falach zostaje).")
                .defineInRange("deadzoneDegrees", 3.0, 0.0, 45.0);
        KEEL_RESPONSE = b
                .comment("Kil (działa, gdy ster jest w wodzie): jak szybko [s] wygaszany jest boczny poślizg.",
                        "Statek w zakręcie płynie za dziobem zamiast bokiem, więc woda nie kładzie go na burtę. 0 = wyłączony.")
                .defineInRange("keelResponse", 0.35, 0.0, 30.0);
        KEEL_KEEP_SPEED = b
                .comment("Jaka część bocznej prędkości zamienia się w ruch do przodu w zakręcie (0 = cała tracona, 1 = zachowana).")
                .defineInRange("keelKeepSpeed", 0.6, 0.0, 1.0);
        b.pop();
    }

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    // =====================================================================
    // CLIENT
    // =====================================================================
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_WIND_TRAILS;
    public static final ModConfigSpec.IntValue MAX_WIND_TRAILS;
    public static final ModConfigSpec.DoubleValue WIND_TRAIL_RADIUS;
    public static final ModConfigSpec.DoubleValue WIND_TRAIL_VIEW_DISTANCE;
    public static final ModConfigSpec.DoubleValue WIND_TRAIL_OPACITY;
    public static final ModConfigSpec.BooleanValue SHOW_SAIL_FRONT_INDICATOR;

    static {
        final ModConfigSpec.Builder b = CLIENT_BUILDER;
        b.push("visuals");
        SHOW_WIND_TRAILS = b.comment("Białe smugi wiatru w powietrzu (jak w Sea of Thieves).")
                .define("showWindTrails", true);
        MAX_WIND_TRAILS = b.comment("Maksymalna liczba smug naraz (przy pełnej sile wiatru). Smugi pojawiają się tylko przy żaglach.")
                .defineInRange("maxWindTrails", 180, 0, 1000);
        WIND_TRAIL_RADIUS = b.comment("Jak daleko od żagla mogą się pojawiać smugi [bloki].")
                .defineInRange("windTrailRadius", 6.0, 0.5, 32.0);
        WIND_TRAIL_VIEW_DISTANCE = b.comment("Smugi rysowane są tylko przy żaglach bliżej kamery niż ta odległość [bloki].")
                .defineInRange("windTrailViewDistance", 64.0, 8.0, 256.0);
        WIND_TRAIL_OPACITY = b.comment("Przezroczystość smug (0-1).")
                .defineInRange("windTrailOpacity", 0.55, 0.0, 1.0);
        SHOW_SAIL_FRONT_INDICATOR = b.comment("Strzałka pokazująca przód żagla, gdy na niego patrzysz.")
                .define("showSailFrontIndicator", true);
        b.pop();
    }

    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
}
