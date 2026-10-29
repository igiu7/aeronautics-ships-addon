package com.aerovaloris.wind;

import com.aerovaloris.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Stan wiatru dla jednego wymiaru, zapisywany razem ze światem.
 * Wiatr powoli skręca do losowego docelowego kierunku i co kilka minut wybiera nowy cel,
 * podobnie jak w Sea of Thieves.
 */
public class WindData extends SavedData {
    public static final String NAME = "aeronauticsvaloris_wind";

    /** Aktualny kurs, w który wieje wiatr [stopnie]. */
    private double bearing;
    /** Docelowy kurs [stopnie]. */
    private double targetBearing;
    /** Bazowa siła 0-1 (bez pogody). */
    private double strength;
    private double targetStrength;
    /** Ticki do kolejnej zmiany. */
    private int ticksUntilChange;
    /** true jeśli kierunek ustawiono komendą - nie zmieniaj automatycznie. */
    private boolean locked;

    public WindData() {
        final RandomSource r = RandomSource.create();
        this.bearing = r.nextDouble() * 360.0;
        this.targetBearing = this.bearing;
        this.strength = 0.6;
        this.targetStrength = 0.6;
        this.ticksUntilChange = 20 * Config.WIND_CHANGE_MIN_SECONDS.get();
    }

    public static SavedData.Factory<WindData> factory() {
        return new SavedData.Factory<>(WindData::new, WindData::load, null);
    }

    public static WindData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static WindData load(final CompoundTag tag, final HolderLookup.Provider provider) {
        final WindData d = new WindData();
        d.bearing = tag.getDouble("bearing");
        d.targetBearing = tag.getDouble("targetBearing");
        d.strength = tag.getDouble("strength");
        d.targetStrength = tag.getDouble("targetStrength");
        d.ticksUntilChange = tag.getInt("ticksUntilChange");
        d.locked = tag.getBoolean("locked");
        return d;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider) {
        tag.putDouble("bearing", this.bearing);
        tag.putDouble("targetBearing", this.targetBearing);
        tag.putDouble("strength", this.strength);
        tag.putDouble("targetStrength", this.targetStrength);
        tag.putInt("ticksUntilChange", this.ticksUntilChange);
        tag.putBoolean("locked", this.locked);
        return tag;
    }

    /** Wywoływane raz na tick serwera dla danego wymiaru. */
    public void tick(final ServerLevel level) {
        final RandomSource rand = level.getRandom();

        if (!this.locked && --this.ticksUntilChange <= 0) {
            this.pickNewTarget(rand);
        }

        // skręcanie do celu
        final double diff = WindMath.wrapDegrees(this.targetBearing - this.bearing);
        final double maxStep = Config.WIND_TURN_SPEED.get() / 20.0;
        this.bearing = Mth.positiveModulo(this.bearing + Mth.clamp(diff, -maxStep, maxStep), 360.0);

        // wolna zmiana siły
        this.strength += (this.targetStrength - this.strength) * 0.0025;

        this.setDirty();
    }

    public void pickNewTarget(final RandomSource rand) {
        final double maxShift = Config.WIND_MAX_SHIFT_DEGREES.get();
        // rozkład bardziej skupiony na mniejszych zmianach
        final double shift = (rand.nextDouble() * 2.0 - 1.0) * (0.35 + 0.65 * rand.nextDouble()) * maxShift;
        this.targetBearing = Mth.positiveModulo(this.bearing + shift, 360.0);

        final double minS = Config.WIND_MIN_STRENGTH.get();
        final double maxS = Math.max(minS, Config.WIND_MAX_STRENGTH.get());
        this.targetStrength = minS + rand.nextDouble() * (maxS - minS);

        final int minT = Config.WIND_CHANGE_MIN_SECONDS.get();
        final int maxT = Math.max(minT, Config.WIND_CHANGE_MAX_SECONDS.get());
        this.ticksUntilChange = 20 * (minT + rand.nextInt(maxT - minT + 1));
        this.setDirty();
    }

    /** Ustawienie komendą. */
    public void set(final double bearing, final double strength, final boolean instant, final boolean lock) {
        this.targetBearing = Mth.positiveModulo(bearing, 360.0);
        this.targetStrength = Mth.clamp(strength, 0.0, 1.0);
        if (instant) {
            this.bearing = this.targetBearing;
            this.strength = this.targetStrength;
        }
        this.locked = lock;
        this.setDirty();
    }

    public void unlock(final RandomSource rand) {
        this.locked = false;
        this.pickNewTarget(rand);
    }

    public double bearing() {
        return this.bearing;
    }

    public double targetBearing() {
        return this.targetBearing;
    }

    public double baseStrength() {
        return this.strength;
    }

    public boolean locked() {
        return this.locked;
    }

    public int ticksUntilChange() {
        return this.ticksUntilChange;
    }

    /** Siła z uwzględnieniem pogody, 0-1. */
    public double effectiveStrength(final ServerLevel level) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0.0;
        }
        double s = this.strength;
        s += Config.RAIN_STRENGTH_BONUS.get() * level.getRainLevel(1.0f);
        s += Config.THUNDER_STRENGTH_BONUS.get() * level.getThunderLevel(1.0f);
        return Mth.clamp(s, 0.0, 1.0);
    }

    public Vec3 direction() {
        return WindMath.bearingToVec(this.bearing);
    }
}
