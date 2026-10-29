package com.aerovaloris.anchor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Kabestan z kotwicą, stawiany na pokładzie przy burcie.
 * <ul>
 *   <li>{@code FACING} – burta, na którą zrzucana jest kotwica (strona, w którą patrzył gracz przy stawianiu).</li>
 *   <li>{@code DEPLOYED} – kotwica jest w wodzie (encja {@link AnchorEntity}); w modelu bloku nie ma wtedy kotwicy.</li>
 * </ul>
 * Prawy klik: zrzuć kotwicę / zacznij wciągać / (podczas wciągania) zrzuć ponownie.
 */
public class AnchorWinchBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<AnchorWinchBlock> CODEC = simpleCodec(AnchorWinchBlock::new);
    public static final BooleanProperty DEPLOYED = BooleanProperty.create("deployed");

    // kształty dla FACING = NORTH (podstawa + słupki + bęben + belka nad burtą)
    private static final VoxelShape NORTH = Shapes.or(
            Block.box(1, 0, 3, 15, 3, 15),
            Block.box(1, 3, 7, 15, 12, 14),
            Block.box(6, 10, 0, 10, 13, 9));
    private static final VoxelShape SOUTH = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 13),
            Block.box(1, 3, 2, 15, 12, 9),
            Block.box(6, 10, 7, 10, 13, 16));
    private static final VoxelShape WEST = Shapes.or(
            Block.box(3, 0, 1, 15, 3, 15),
            Block.box(7, 3, 1, 14, 12, 15),
            Block.box(0, 10, 6, 9, 13, 10));
    private static final VoxelShape EAST = Shapes.or(
            Block.box(1, 0, 1, 13, 3, 15),
            Block.box(2, 3, 1, 9, 12, 15),
            Block.box(7, 10, 6, 16, 13, 10));

    public AnchorWinchBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(DEPLOYED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DEPLOYED);
    }

    /** Kotwica zrzucana w stronę, w którą patrzy gracz (na statku Sable – w układzie statku). */
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level instanceof final ServerLevel serverLevel) {
            AnchorManager.toggle(serverLevel, pos, state, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            final AnchorEntity anchor = AnchorManager.get(level, pos);
            if (anchor != null) anchor.discard();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
