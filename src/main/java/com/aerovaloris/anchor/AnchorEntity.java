package com.aerovaloris.anchor;

import com.aerovaloris.Config;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Kotwica w wodzie. Wisi na linie pod kabestanem ({@link AnchorWinchBlock}) i:
 * <ol>
 *   <li>{@link State#DROPPING} – opada ze stałą prędkością pod punktem zaczepienia (podąża za statkiem),</li>
 *   <li>{@link State#LANDED} – leży na dnie, nie rusza się; statek jest trzymany ({@link AnchorHoldHandler}),</li>
 *   <li>{@link State#RAISING} – jest wciągana; czas = długość liny / prędkość wciągania.</li>
 * </ol>
 * Ruch jest liczony ręcznie (noPhysics), więc kotwica nie zahacza o bloki statku Sable,
 * tylko o bloki świata (dno).
 */
public class AnchorEntity extends Entity {

    /** Wysokość ucha kotwicy nad jej spodem [bloki] (zgodna z modelem). */
    public static final double TOP = 1.48;
    /** Odległość koła linowego od środka bloku w stronę burty [bloki] (zgodna z modelem). */
    public static final double ATTACH_OUT = 0.75;
    public static final double ATTACH_UP = 7.0 / 16.0; // spód koła linowego

    public enum State { DROPPING, LANDED, RAISING }

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> WINCH =
            SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> FACING =
            SynchedEntityData.defineId(AnchorEntity.class, EntityDataSerializers.INT);

    /** Aktualna długość liny (od koła do ucha kotwicy) [bloki]. */
    private double ropeLength = 0;
    /** Punkt (świat, XZ), w którym statek ma być trzymany – zaczep liny w chwili dotarcia na dno. */
    private double holdX, holdZ;
    private int soundTimer = 0;
    private boolean wasInWater = false;

    public AnchorEntity(final EntityType<? extends AnchorEntity> type, final Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AnchorEntity create(final ServerLevel level, final BlockPos winch, final Direction facing) {
        final AnchorEntity e = new AnchorEntity(com.aerovaloris.ModBlocks.ANCHOR_ENTITY.get(), level);
        e.entityData.set(WINCH, winch.immutable());
        e.entityData.set(FACING, facing.get2DDataValue());
        e.entityData.set(STATE, State.DROPPING.ordinal());
        final Vec3 attach = e.attachWorld();
        e.setPos(attach.x, attach.y - TOP, attach.z);
        return e;
    }

    // ------------------------------------------------------------------ dane

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        builder.define(STATE, State.DROPPING.ordinal());
        builder.define(WINCH, BlockPos.ZERO);
        builder.define(FACING, Direction.NORTH.get2DDataValue());
    }

    public State getState() {
        final int i = this.entityData.get(STATE);
        return State.values()[Math.max(0, Math.min(State.values().length - 1, i))];
    }

    public void setState(final State state) {
        this.entityData.set(STATE, state.ordinal());
    }

    public BlockPos getWinchPos() {
        return this.entityData.get(WINCH);
    }

    public Direction getFacing() {
        return Direction.from2DDataValue(this.entityData.get(FACING));
    }

    public double getRopeLength() {
        return this.ropeLength;
    }

    public double getHoldX() {
        return this.holdX;
    }

    public double getHoldZ() {
        return this.holdZ;
    }

    /** Punkt zaczepienia liny w układzie bloku (plot Sable albo świat, jeśli kabestan nie jest na statku). */
    public static Vec3 attachLocal(final BlockPos winch, final Direction facing) {
        return new Vec3(
                winch.getX() + 0.5 + facing.getStepX() * ATTACH_OUT,
                winch.getY() + ATTACH_UP,
                winch.getZ() + 0.5 + facing.getStepZ() * ATTACH_OUT);
    }

    /** Punkt zaczepienia w świecie (serwer – logiczna poza statku). */
    public Vec3 attachWorld() {
        return Sable.HELPER.projectOutOfSubLevel(this.level(), attachLocal(this.getWinchPos(), this.getFacing()));
    }

    // ------------------------------------------------------------------ logika

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof final ServerLevel level)) return;

        if (!AnchorManager.register(this)) {
            // dla tego kabestanu jest już inna kotwica (np. stara, wczytana z dysku)
            this.discard();
            return;
        }

        final BlockPos winch = this.getWinchPos();
        if (!level.isLoaded(winch)) return; // statek nie jest wczytany – czekamy

        final BlockState winchState = level.getBlockState(winch);
        if (!(winchState.getBlock() instanceof AnchorWinchBlock) || !winchState.getValue(AnchorWinchBlock.DEPLOYED)) {
            this.discard();
            return;
        }

        final Vec3 attach = this.attachWorld();
        switch (this.getState()) {
            case DROPPING -> this.tickDropping(level, attach);
            case LANDED -> this.ropeLength = Math.max(0, this.position().add(0, TOP, 0).distanceTo(attach));
            case RAISING -> this.tickRaising(level, attach, winch, winchState);
        }

        // plusk przy wejściu do wody
        final boolean inWater = level.isLoaded(this.blockPosition())
                && level.getFluidState(this.blockPosition()).is(FluidTags.WATER);
        if (inWater && !this.wasInWater && this.getState() == State.DROPPING) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0f, 0.7f);
        }
        this.wasInWater = inWater;
    }

    private void tickDropping(final ServerLevel level, final Vec3 attach) {
        final double maxLen = Config.ANCHOR_MAX_ROPE.get();
        final double newLen = Math.min(maxLen, this.ropeLength + Config.ANCHOR_DROP_SPEED.get() / 20.0);
        final double bottomY = attach.y - newLen - TOP;

        final BlockPos at = BlockPos.containing(attach.x, bottomY, attach.z);
        if (!level.isLoaded(at)) {
            this.setPos(attach.x, this.getY(), attach.z);
            return;
        }

        final double groundTop = this.groundTop(level, at);
        if (!Double.isNaN(groundTop) || bottomY <= level.getMinBuildHeight()) {
            final double y = Double.isNaN(groundTop) ? level.getMinBuildHeight() : groundTop;
            this.setPos(attach.x, y, attach.z);
            this.ropeLength = Math.max(0, attach.y - y - TOP);
            this.holdX = attach.x;
            this.holdZ = attach.z;
            this.setState(State.LANDED);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5f, 0.6f);
            return;
        }

        this.ropeLength = newLen;
        this.setPos(attach.x, bottomY, attach.z);

        if (newLen >= maxLen) return; // lina się skończyła – kotwica wisi, statek nie jest trzymany
        if (--this.soundTimer <= 0) {
            this.soundTimer = 4;
            level.playSound(null, attach.x, attach.y, attach.z, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.6f, 0.8f);
        }
    }

    /**
     * Górna krawędź twardego bloku świata w {@code at} (albo w bloku nad nim, jeśli kotwica już w niego weszła),
     * NaN jeśli to woda/powietrze.
     */
    private double groundTop(final ServerLevel level, final BlockPos at) {
        final BlockState state = level.getBlockState(at);
        final VoxelShape shape = state.getCollisionShape(level, at);
        if (shape.isEmpty()) return Double.NaN;
        return at.getY() + shape.max(Direction.Axis.Y);
    }

    private void tickRaising(final ServerLevel level, final Vec3 attach, final BlockPos winch, final BlockState winchState) {
        this.ropeLength = Math.max(0, this.ropeLength - Config.ANCHOR_RAISE_SPEED.get() / 20.0);

        final Vec3 target = new Vec3(attach.x, attach.y - this.ropeLength - TOP, attach.z);
        final Vec3 cur = this.position();
        // pionowo wg długości liny, poziomo kotwica dociąga się pod zaczep (lina się prostuje)
        this.setPos(cur.x + (target.x - cur.x) * 0.15, target.y, cur.z + (target.z - cur.z) * 0.15);

        if (--this.soundTimer <= 0) {
            this.soundTimer = 8;
            level.playSound(null, attach.x, attach.y, attach.z, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5f, 0.7f);
        }

        if (this.ropeLength <= 0.0) {
            level.setBlock(winch, winchState.setValue(AnchorWinchBlock.DEPLOYED, false), 3);
            level.playSound(null, attach.x, attach.y, attach.z, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.BLOCKS, 1.0f, 0.8f);
            this.discard();
        }
    }

    /** Ile sekund potrwa wciąganie z obecnej głębokości. */
    public double raiseSeconds() {
        return this.ropeLength / Math.max(0.01, Config.ANCHOR_RAISE_SPEED.get());
    }

    public void startRaising() {
        final Vec3 attach = this.attachWorld();
        this.ropeLength = Math.max(this.ropeLength, this.position().add(0, TOP, 0).distanceTo(attach));
        this.setState(State.RAISING);
    }

    public void startDropping() {
        this.setState(State.DROPPING);
    }

    // ------------------------------------------------------------------ różne

    @Override
    public boolean hurt(final DamageSource source, final float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(final double distance) {
        return distance < 256.0 * 256.0;
    }

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {
        NbtUtils.readBlockPos(tag, "Winch").ifPresent(p -> this.entityData.set(WINCH, p));
        this.entityData.set(FACING, tag.getInt("Facing"));
        this.entityData.set(STATE, tag.getInt("State"));
        this.ropeLength = tag.getDouble("Rope");
        this.holdX = tag.getDouble("HoldX");
        this.holdZ = tag.getDouble("HoldZ");
    }

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {
        tag.put("Winch", NbtUtils.writeBlockPos(this.getWinchPos()));
        tag.putInt("Facing", this.entityData.get(FACING));
        tag.putInt("State", this.entityData.get(STATE));
        tag.putDouble("Rope", this.ropeLength);
        tag.putDouble("HoldX", this.holdX);
        tag.putDouble("HoldZ", this.holdZ);
    }
}
