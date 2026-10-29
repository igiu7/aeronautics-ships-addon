package com.aerovaloris;

import com.aerovaloris.client.AnchorRenderer;
import com.aerovaloris.client.RudderRenderer;
import com.aerovaloris.client.SailFrontIndicator;
import com.aerovaloris.client.WindHud;
import com.aerovaloris.client.WindTrailRenderer;
import com.aerovaloris.wind.ClientWindState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// Ta klasa nie ładuje się na serwerze dedykowanym.
@Mod(value = aeronauticsvaloris.MODID, dist = Dist.CLIENT)
public class aeronauticsvalorisClient {

    public aeronauticsvalorisClient(final IEventBus modEventBus, final ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(aeronauticsvalorisClient::registerGuiLayers);
        modEventBus.addListener(aeronauticsvalorisClient::registerRenderers);
        RudderRenderer.init(); // rejestracja PartialModel płetwy przed ładowaniem modeli
        AnchorRenderer.init(); // i modelu kotwicy

        NeoForge.EVENT_BUS.addListener(aeronauticsvalorisClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(aeronauticsvalorisClient::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(aeronauticsvalorisClient::onLogout);
    }

    private static void registerGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(aeronauticsvaloris.MODID, "wind_hud"), WindHud::render);
    }

    private static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.RUDDER_BE.get(), RudderRenderer::new);
        event.registerEntityRenderer(ModBlocks.ANCHOR_ENTITY.get(), AnchorRenderer::new);
    }

    private static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!mc.isPaused()) ClientWindState.tick();
        WindTrailRenderer.tick();
        SailFrontIndicator.tick();
    }

    private static void onRenderLevel(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        WindTrailRenderer.render(event);
        SailFrontIndicator.render(event);
    }

    private static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWindState.reset();
        com.aerovaloris.wind.ClientSailPoints.reset();
        WindTrailRenderer.clear();
    }
}
