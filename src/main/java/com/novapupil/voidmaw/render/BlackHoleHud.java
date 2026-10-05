package com.novapupil.voidmaw.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Centered readout of level, mass and pit radius while the local player is the maw.
 */
public final class BlackHoleHud implements HudElement {

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        BlackHoleRenderer.Hole hole = BlackHoleRenderer.viewOf(client.player.getUuid());
        if (hole == null) {
            return;
        }
        TextRenderer textRenderer = client.textRenderer;
        Text line = Text.translatable("hud.voidmaw.mass",
                hole.level(),
                String.format(java.util.Locale.ROOT, "%.1f", hole.mass()),
                String.format(java.util.Locale.ROOT, "%.1f", hole.radius()));
        int x = (context.getScaledWindowWidth() - textRenderer.getWidth(line)) / 2;
        context.drawText(textRenderer, line, x, 8, 0xFFDDB0FF, true);
    }
}
