package com.aerovaloris.rudder;

import com.aerovaloris.ModBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Ster statku napędzany wałem z Create (np. ze Steering Wheel z Create Simulated).
 * <ul>
 *   <li>Wał wchodzi od <b>góry</b> (oś Y).</li>
 *   <li>{@code FACING} – w którą stronę wystaje płetwa. Przy stawianiu płetwa zawsze patrzy
 *       w stronę gracza; przód statku to strona przeciwna.</li>
 *   <li>Wychylenie płetwy = obrót wału przeskalowany tak, że pełny skręt Steering Wheel
 *       (domyślnie ±180°) daje pełne wychylenie steru (±45°).</li>
 * </ul>
 * Implementuje {@link BlockSubLevelLiftProvider} z zerowymi współczynnikami tylko po to,
 * żeby Sable śledził go na statkach i w kontrapcjach (sam nie daje siły nośnej ani oporu).
 */
public class RudderBlock extends KineticBlock implements IBE<RudderBlockEntity>, BlockSubLevelLiftProvider {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public RudderBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    /** Płetwa w stronę gracza, zależnie od tego, z której strony patrzy na blok. */
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return true;
    }

    // ------------------------------------------------------------------ Create: kinetyka

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(final LevelReader world, final BlockPos pos, final BlockState state, final Direction face) {
        return face == Direction.UP;
    }

    @Override
    public Class<RudderBlockEntity> getBlockEntityClass() {
        return RudderBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RudderBlockEntity> getBlockEntityType() {
        return ModBlocks.RUDDER_BE.get();
    }

    /**
     * Klucz (bez Shifta) wyśrodkowuje ster: zeruje zapamiętany obrót wału.
     * Użyj, gdy koło stoi na 0°, a płetwa nie jest prosto (np. po starej wersji moda).
     */
    @Override
    public InteractionResult onWrenched(final BlockState state, final UseOnContext context) {
        final Level level = context.getLevel();
        if (level.getBlockEntity(context.getClickedPos()) instanceof final RudderBlockEntity be) {
            if (!level.isClientSide) be.recenter();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ obrót

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ------------------------------------------------------------------ Sable

    @Override
    public @NotNull Direction sable$getNormal(final BlockState state) {
        return state.getValue(FACING);
    }

    @Override
    public float sable$getParallelDragScalar() {
        return 0;
    }

    @Override
    public float sable$getDirectionlessDragScalar() {
        return 0;
    }

    @Override
    public float sable$getLiftScalar() {
        return 0;
    }
}
