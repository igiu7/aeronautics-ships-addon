package com.aerovaloris.client;

import com.aerovaloris.aeronauticsvaloris;
import com.aerovaloris.rudder.RudderBlock;
import com.aerovaloris.rudder.RudderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Renderuje ruchome części steru:
 * - pół-wał od góry (obraca się jak każdy wał Create),
 * - trzon z płetwą, obrócony o wychylenie steru.
 * Obudowa (kołnierz, wspornik) jest zwykłym statycznym modelem bloku.
 * <p>
 * Celowo nie korzysta z Flywheel visuals – zwykły BER działa zawsze, także na statkach Sable.
 */
public class RudderRenderer implements BlockEntityRenderer<RudderBlockEntity> {

    /** Model płetwy (skierowany na północ, oś obrotu w środku bloku). */
    public static final PartialModel BLADE =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(aeronauticsvaloris.MODID, "block/rudder_blade"));

    /** Wywołane z konstruktora klienta, żeby PartialModel zarejestrował się przed ładowaniem modeli. */
    public static void init() {
    }

    public RudderRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final RudderBlockEntity be, final float partialTick, final PoseStack ms,
                       final MultiBufferSource buffers, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof RudderBlock)) return;
        final VertexConsumer vb = buffers.getBuffer(RenderType.cutoutMipped());

        // pół-wał wchodzący od góry
        final SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.UP);
        KineticBlockEntityRenderer.standardKineticRotationTransform(shaft, be, light).renderInto(ms, vb);

        // płetwa: obrót do kierunku FACING + wychylenie (oba prawoskrętnie wokół +Y)
        final Direction facing = state.getValue(RudderBlock.FACING);
        final float facingYaw = -facing.toYRot() + 180f; // NORTH -> 0, EAST -> -90, SOUTH -> 180, WEST -> 90
        final float yaw = facingYaw + be.getDeflection(partialTick);

        CachedBuffers.partial(BLADE, state)
                .center()
                .rotateYDegrees(yaw)
                .uncenter()
                .light(light)
                .renderInto(ms, vb);
    }

    @Override
    public boolean shouldRenderOffScreen(final RudderBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(final RudderBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
    }
}
