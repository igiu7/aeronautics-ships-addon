package com.aerovaloris.sail;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.aerovaloris.wind.WindMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * - Po postawieniu Symmetric Sail gracz dostaje informację, gdzie jest przód.
 * - Klucz (Create Wrench) kliknięty w płaską stronę Symmetric Sail odwraca przód/tył.
 *   Kliknięcie w krawędź działa jak zwykle (obrót osi), a Shift+klucz podnosi blok.
 */
public final class SailInteractionHandler {
    private SailInteractionHandler() {}

    /**
     * Po postawieniu żagla (zwykłego lub przez placement helper, który kopiuje stan sąsiada)
     * ustawiamy przód zawsze w stronę, w którą patrzy gracz.
     */
    public static void onBlockPlaced(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof final ServerPlayer player)) return;
        if (!(event.getLevel() instanceof final Level level)) return;
        final BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!SailHelper.isSail(state)) return;

        final Direction desired = frontFromPlayer(player, level, pos, state);
        if (desired != null && desired != SailHelper.front(state)) {
            state = SailHelper.withFrontAny(state, desired);
            level.setBlock(pos, state, 3);
        }

        if (SailHelper.isSymmetricSail(state)) {
            sendFrontInfo(player, level, pos, state, "aeronauticsvaloris.sail.placed");
        }
    }

    /**
     * Przód wzdłuż osi żagla, zgodny z kierunkiem patrzenia gracza.
     * Na statku wektor patrzenia przeliczamy do układu statku (plotu).
     */
    private static Direction frontFromPlayer(final ServerPlayer player, final Level level, final BlockPos pos, final BlockState state) {
        final Direction.Axis axis = SailHelper.frontAxis(state);
        if (axis == null) return null;

        Vec3 look = player.getLookAngle();
        Vec3 toBlock = Vec3.atCenterOf(pos);
        Vec3 eye = player.getEyePosition();
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) {
            look = subLevel.logicalPose().transformNormalInverse(look);
            eye = subLevel.logicalPose().transformPositionInverse(eye);
        }
        toBlock = toBlock.subtract(eye);

        double c = axis.choose(look.x, look.y, look.z);
        if (Math.abs(c) < 0.15) {
            // patrzymy prawie wzdłuż płótna – bierzemy stronę "od gracza"
            c = axis.choose(toBlock.x, toBlock.y, toBlock.z);
        }
        if (Math.abs(c) < 1.0e-4) return null;
        return Direction.get(c > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE, axis);
    }

    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final ItemStack stack = event.getItemStack();
        if (!isWrench(stack)) return;
        if (event.getEntity().isShiftKeyDown()) return;

        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        final BlockState state = level.getBlockState(pos);
        if (!SailHelper.isSymmetricSail(state)) return;

        final Direction face = event.getFace();
        if (face == null || face.getAxis() != state.getValue(BlockStateProperties.AXIS)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));

        if (level.isClientSide) return;

        final BlockState flipped = state.setValue(SailHelper.FRONT_POSITIVE, !state.getValue(SailHelper.FRONT_POSITIVE));
        level.setBlock(pos, flipped, 3);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0f, 0.8f);

        if (event.getEntity() instanceof final ServerPlayer sp) {
            sendFrontInfo(sp, level, pos, flipped, "aeronauticsvaloris.sail.flipped");
        }
    }

    private static boolean isWrench(final ItemStack stack) {
        if (stack.isEmpty()) return false;
        final var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "create".equals(id.getNamespace()) && "wrench".equals(id.getPath());
    }

    private static void sendFrontInfo(final ServerPlayer player, final Level level, final BlockPos pos,
                                      final BlockState state, final String key) {
        final Direction front = SailHelper.front(state);
        if (front == null) return;

        Vec3 frontWorld = Vec3.atLowerCornerOf(front.getNormal());
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) {
            frontWorld = subLevel.logicalPose().transformNormal(frontWorld);
        }

        player.displayClientMessage(Component.translatable(key,
                        WindMath.directionName(frontWorld).copy().withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.YELLOW), true);
    }
}
