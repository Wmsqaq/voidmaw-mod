package com.novapupil.voidmaw.render;

import com.novapupil.voidmaw.blackhole.HoleLevel;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/**
 * Centered HUD while the local player is the maw: a level badge with a progress bar
 * toward the next absorption level, and a mass/radius stat line below it.
 */
public final class BlackHoleHud implements HudElement {
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 3;
    private static final int COLOR_BG = 0x66000000;
    private static final int COLOR_FILL = 0xFFB36BFF;
    private static final int COLOR_LEVEL = 0xFFE6C8FF;
    private static final int COLOR_STATS = 0xFFDDB0FF;

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
        int centerX = context.getScaledWindowWidth() / 2;

        // Level badge with progress toward the next level.
        boolean maxed = hole.level() >= HoleLevel.MAX;
        double next = HoleLevel.nextLevelMass(hole.level());
        Text levelLine = maxed
                ? Text.translatable("hud.voidmaw.level_max", hole.level())
                : Text.translatable("hud.voidmaw.level",
                        hole.level(),
                        String.format(Locale.ROOT, "%.0f", hole.mass()),
                        String.format(Locale.ROOT, "%.0f", next));
        context.drawText(textRenderer, levelLine,
                centerX - textRenderer.getWidth(levelLine) / 2, 4, COLOR_LEVEL, true);

        int statsY = 4 + textRenderer.fontHeight + 2;
        if (!maxed) {
            double base = HoleLevel.levelThreshold(hole.level());
            double progress = MathHelper.clamp((hole.mass() - base) / (next - base), 0.0, 1.0);
            int barX = centerX - BAR_WIDTH / 2;
            int barY = statsY + 1;
            context.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, COLOR_BG);
            context.fill(barX, barY, barX + (int) (BAR_WIDTH * progress), barY + BAR_HEIGHT, COLOR_FILL);
            statsY += BAR_HEIGHT + 4;
        }

        // Mass / radius stat line.
        Text stats = Text.translatable("hud.voidmaw.mass",
                String.format(Locale.ROOT, "%.1f", hole.mass()),
                String.format(Locale.ROOT, "%.1f", hole.radius()));
        context.drawText(textRenderer, stats,
                centerX - textRenderer.getWidth(stats) / 2, statsY, COLOR_STATS, true);
    }
}
