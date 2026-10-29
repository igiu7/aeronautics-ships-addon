package com.aerovaloris.rudder;

import com.aerovaloris.Config;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Całkuje obrót wału (jak Steering Wheel – wał obraca się o tyle stopni, o ile obrócisz koło)
 * i zamienia go na wychylenie płetwy.
 */
public class RudderBlockEntity extends KineticBlockEntity {

    /**
     * Skumulowany obrót wału [stopnie]. NIE jest obcinany do zakresu steru – obcinamy dopiero wychylenie.
     * Dzięki temu, gdy koło ma większy limit niż config, ster nie "gubi" pozycji.
     */
    private float shaftAngle = 0;
    /**
     * Steering Wheel obraca wał "sekwencyjnie" (jak Sequenced Gearshift): o dokładnie N stopni.
     * Prędkość utrzymuje się jednak jeszcze 1-2 ticki, więc bez tego limitu ster dostawał
     * kilka stopni za dużo przy każdym ruchu i z czasem się rozjeżdżał. Tak samo robi Mechanical Bearing.
     */
    private double sequencedAngleLimit = -1;
    private float prevShaftAngle = 0;
    private boolean needsSync = false;

    public RudderBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        this.prevShaftAngle = this.shaftAngle;

        final float speed = this.getSpeed();
        if (speed != 0) {
            float angular = convertToAngular(speed);
            if (this.sequencedAngleLimit >= 0) {
                angular = (float) Mth.clamp(angular, -this.sequencedAngleLimit, this.sequencedAngleLimit);
                this.sequencedAngleLimit = Math.max(0, this.sequencedAngleLimit - Math.abs(angular));
            }
            // zabezpieczenie przed nieskończonym kręceniem silnikiem
            this.shaftAngle = Mth.clamp(this.shaftAngle + angular, -36000f, 36000f);
            this.needsSync = true;
        } else if (this.needsSync && this.level != null && !this.level.isClientSide) {
            // po zatrzymaniu wału wyrównaj stan klienta z serwerem
            this.needsSync = false;
            this.sendData();
        }
    }

    @Override
    public void onSpeedChanged(final float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (this.sequenceContext != null && this.sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE) {
            this.sequencedAngleLimit = this.sequenceContext.getEffectiveValue(this.getTheoreticalSpeed());
        } else {
            this.sequencedAngleLimit = -1;
        }
    }

    /** Wyśrodkowanie płetwy (klucz). */
    public void recenter() {
        this.shaftAngle = 0;
        this.prevShaftAngle = 0;
        this.sequencedAngleLimit = -1;
        this.sendData();
        this.setChanged();
    }

    private static float range() {
        return (float) Math.max(1.0, Config.RUDDER_SHAFT_RANGE.get());
    }

    /** Wychylenie płetwy [stopnie], prawoskrętnie wokół +Y (tak jak w modelu). */
    public float getDeflection(final float partialTicks) {
        final float range = range();
        final float shaft = Mth.clamp(Mth.lerp(partialTicks, this.prevShaftAngle, this.shaftAngle), -range, range);
        float deflection = shaft / range * (float) (double) Config.RUDDER_MAX_DEFLECTION.get();
        if (Config.RUDDER_INVERT.get()) deflection = -deflection;
        return deflection;
    }

    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("ShaftAngle", this.shaftAngle);
        if (this.sequencedAngleLimit >= 0) compound.putDouble("SequencedAngleLimit", this.sequencedAngleLimit);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.shaftAngle = compound.getFloat("ShaftAngle");
        this.sequencedAngleLimit = compound.contains("SequencedAngleLimit") ? compound.getDouble("SequencedAngleLimit") : -1;
        if (!clientPacket) this.prevShaftAngle = this.shaftAngle;
    }
}
