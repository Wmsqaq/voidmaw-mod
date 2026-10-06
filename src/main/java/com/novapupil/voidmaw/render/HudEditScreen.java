/*
 * HUD editor overlay, ported from killstreak-mod's client HudEditScreen:
 *   https://github.com/Wmsqaq/killstreak-mod - MIT License, Copyright (c) 2026 novapupil.
 * Adapted for Void Maw's single-panel HUD (drag to move, scroll to scale, R to reset).
 */
package com.novapupil.voidmaw.render;

import com.novapupil.voidmaw.VoidMawClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Transparent full-screen overlay used to drag the HUD panel. It deliberately skips
 * super.render() so the world and the HUD elements below stay visible without a dark
 * overlay. Ported from the killstreak-mod HUD editor.
 */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean dragging;

    public HudEditScreen(Screen parent) {
        super(Text.translatable("hud.voidmaw.edit_title"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        BlackHoleHud.drawOutline(context, mouseX, mouseY);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("hud.voidmaw.edit_hint"), width / 2, height - 16, 0xFFFFFF00);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int mx = (int) click.x();
            int my = (int) click.y();
            if (BlackHoleHud.hitTest(mx, my)) {
                BlackHoleHud.beginDrag(mx, my);
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging) {
            BlackHoleHud.dragTo((int) click.x(), (int) click.y());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            VoidMawConfig.save(VoidMawClient.config);
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && (dragging || BlackHoleHud.hitTest((int) mouseX, (int) mouseY))) {
            BlackHoleHud.adjustScale((float) verticalAmount);
            VoidMawConfig.save(VoidMawClient.config);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_R) {
            BlackHoleHud.resetPosition();
            VoidMawConfig.save(VoidMawClient.config);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        VoidMawConfig.save(VoidMawClient.config);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
