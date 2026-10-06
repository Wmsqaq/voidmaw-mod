package com.novapupil.voidmaw.render;

import com.novapupil.voidmaw.VoidMaw;
import com.novapupil.voidmaw.VoidMawClient;
import com.novapupil.voidmaw.blackhole.HoleLevel;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

import java.util.Locale;

/**
 * Draggable HUD panel (killstreak-mod style): level badge, progress bar toward the
 * next absorption level, and a mass/radius stat line. Position/scale persist in
 * config/voidmaw.json; press the HUD-edit key to drag it around.
 */
public final class BlackHoleHud implements HudElement {
    private static final int PADDING = 4;
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 3;

    private static final int COLOR_PANEL = 0x5A000000;
    private static final int COLOR_PANEL_INNER = 0x96000000;
    private static final int COLOR_HIGHLIGHT = 0x12FFFFFF;
    private static final int COLOR_BAR_BG = 0x66222233;
    private static final int COLOR_BAR_FILL = 0xFFB36BFF;
    private static final int COLOR_LEVEL = 0xFFE6C8FF;
    private static final int COLOR_STATS = 0xFFDDB0FF;

    /** Last rendered rect {x, y, w, h}; -1 marks "not rendered yet". */
    private static final int[] RECT = {-1, -1, 0, 0};
    private static int dragOffsetX;
    private static int dragOffsetY;

    private BlackHoleHud() {
    }

    public static void init() {
        HudElementRegistry.addLast(Identifier.of(VoidMaw.MOD_ID, "mass_hud"), new BlackHoleHud());
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        clearBounds();
        MinecraftClient client = MinecraftClient.getInstance();
        VoidMawConfig config = VoidMawClient.config;
        if (client.player == null || config == null) {
            return;
        }
        boolean editMode = client.currentScreen instanceof HudEditScreen;
        if ((!config.hudVisible || client.options.hudHidden) && !editMode) {
            return;
        }
        BlackHoleRenderer.Hole hole = BlackHoleRenderer.viewOf(client.player.getUuid());
        if (hole == null && !editMode) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        boolean active = hole != null;
        double next = active ? HoleLevel.nextLevelMass(hole.level()) : 0;

        Text levelLine = !active
                ? Text.translatable("hud.voidmaw.inactive")
                : Text.translatable("hud.voidmaw.level",
                                hole.level(),
                                String.format(Locale.ROOT, "%.0f", hole.mass()),
                                String.format(Locale.ROOT, "%.0f", next));
        Text stats = Text.translatable("hud.voidmaw.stats",
                String.format(Locale.ROOT, "%.1f", active ? hole.radius() : 0.0));

        int lineHeight = textRenderer.fontHeight;
        int contentWidth = Math.max(Math.max(textRenderer.getWidth(levelLine), textRenderer.getWidth(stats)), BAR_WIDTH);
        int unscaledWidth = contentWidth + PADDING * 2;
        int unscaledHeight = PADDING * 2 + lineHeight * 3 + BAR_HEIGHT + 4;

        float scale = MathHelper.clamp(config.hudScale, 0.5f, 4f);
        int width = Math.round(unscaledWidth * scale);
        int height = Math.round(unscaledHeight * scale);
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = config.hudX >= 0 ? config.hudX : 10;
        int y = config.hudY >= 0 ? config.hudY : 10;
        x = MathHelper.clamp(x, 0, Math.max(0, screenWidth - width));
        y = MathHelper.clamp(y, 0, Math.max(0, screenHeight - height));

        // Translucent panel with border and top highlight (killstreak/voice-remorphed style).
        // 1.21.6+ respects the alpha channel of text colors: 0xFFFFFF would be fully transparent.
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        context.fill(0, 0, unscaledWidth, unscaledHeight, COLOR_PANEL);
        context.fill(1, 1, unscaledWidth - 1, unscaledHeight - 1, COLOR_PANEL_INNER);
        context.fill(1, 1, unscaledWidth - 1, 2, COLOR_HIGHLIGHT);
        context.drawText(textRenderer, levelLine, PADDING, PADDING, COLOR_LEVEL, true);

        int barY = PADDING + lineHeight + 2;
        if (active) {
            double base = HoleLevel.levelThreshold(hole.level());
            double progress = MathHelper.clamp((hole.mass() - base) / (next - base), 0.0, 1.0);
            // The bar spans the full panel interior so it always matches the HUD width.
            context.fill(PADDING, barY, PADDING + contentWidth, barY + BAR_HEIGHT, COLOR_BAR_BG);
            context.fill(PADDING, barY, PADDING + (int) (contentWidth * progress), barY + BAR_HEIGHT, COLOR_BAR_FILL);
        }

        context.drawText(textRenderer, stats, PADDING, barY + BAR_HEIGHT + 3, COLOR_STATS, true);
        matrices.popMatrix();

        RECT[0] = x;
        RECT[1] = y;
        RECT[2] = width;
        RECT[3] = height;
    }

    static void adjustScale(float scroll) {
        VoidMawConfig config = VoidMawClient.config;
        if (config != null) {
            config.hudScale = MathHelper.clamp(config.hudScale + scroll * 0.25f, 0.5f, 4f);
        }
    }

    static boolean hitTest(int mouseX, int mouseY) {
        return RECT[0] >= 0
                && mouseX >= RECT[0] && mouseX <= RECT[0] + RECT[2]
                && mouseY >= RECT[1] && mouseY <= RECT[1] + RECT[3];
    }

    static void beginDrag(int mouseX, int mouseY) {
        dragOffsetX = mouseX - RECT[0];
        dragOffsetY = mouseY - RECT[1];
    }

    static void dragTo(int mouseX, int mouseY) {
        VoidMawConfig config = VoidMawClient.config;
        if (config != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            config.hudX = MathHelper.clamp(mouseX - dragOffsetX, 0,
                    Math.max(0, client.getWindow().getScaledWidth() - RECT[2]));
            config.hudY = MathHelper.clamp(mouseY - dragOffsetY, 0,
                    Math.max(0, client.getWindow().getScaledHeight() - RECT[3]));
        }
    }

    static void clearBounds() {
        RECT[0] = -1;
        RECT[1] = -1;
        RECT[2] = 0;
        RECT[3] = 0;
    }

    static void resetPosition() {
        VoidMawConfig config = VoidMawClient.config;
        if (config != null) {
            config.hudX = -1;
            config.hudY = -1;
            config.hudScale = 1.0f;
        }
    }

    /** Outline drawn by the edit screen; turns yellow while hovering the panel. */
    static void drawOutline(DrawContext context, int mouseX, int mouseY) {
        if (RECT[0] < 0) {
            return;
        }
        int x = RECT[0];
        int y = RECT[1];
        int w = RECT[2];
        int h = RECT[3];
        int color = hitTest(mouseX, mouseY) ? 0xFFFFFF00 : 0xFFFFFFFF;
        context.fill(x - 1, y - 1, x + w + 1, y, color);
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        context.fill(x - 1, y, x, y + h, color);
        context.fill(x + w, y, x + w + 1, y + h, color);
    }
}
