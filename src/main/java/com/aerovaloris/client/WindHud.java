package com.aerovaloris.client;

import com.aerovaloris.wind.WindMath;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * HUD: tylko napis pod celownikiem, gdy patrzysz na żagiel (gdzie jest przód i ile daje teraz wiatr).
 */
public final class WindHud {
    private WindHud() {}

    public static void render(final GuiGraphics g, final DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (!SailFrontIndicator.isHovering()) return;

        final float pt = delta.getGameTimeDeltaPartialTick(false);
        final Vec3 front = SailFrontIndicator.hoveredFrontWorld(pt);
        if (front == null) return;

        final double mul = SailFrontIndicator.hoveredWindMultiplier(pt);
        final ChatFormatting color = mul >= 1.5 ? ChatFormatting.GREEN : mul >= 0.8 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        final Component line = Component.translatable("aeronauticsvaloris.hud.sail_front",
                WindMath.directionName(front).copy().withStyle(ChatFormatting.GOLD),
                Component.literal(String.format("x%.2f", mul)).withStyle(color));
        g.drawCenteredString(mc.font, line, g.guiWidth() / 2, g.guiHeight() / 2 + 12, 0xFFFFFF);
    }
}
