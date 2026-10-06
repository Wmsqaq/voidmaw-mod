package com.novapupil.voidmaw;

import com.novapupil.voidmaw.net.MassSyncPayload;
import com.novapupil.voidmaw.net.OpenWarehousePayload;
import com.novapupil.voidmaw.render.BlackHoleHud;
import com.novapupil.voidmaw.render.BlackHoleRenderer;
import com.novapupil.voidmaw.render.HudEditScreen;
import com.novapupil.voidmaw.render.VoidMawConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VoidMawClient implements ClientModInitializer {
    public static VoidMawConfig config;

    @Override
    public void onInitializeClient() {
        config = VoidMawConfig.load();
        BlackHoleHud.init();
        BlackHoleRenderer.init();

        ClientPlayNetworking.registerGlobalReceiver(MassSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> BlackHoleRenderer.updateState(payload)));

        // Categories are globally registered in 1.21.10 - create ONCE and share,
        // a second create() with the same id crashes the client entrypoint.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(VoidMaw.MOD_ID, "main"));
        KeyBinding hudEditKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voidmaw.hud_edit",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category));
        KeyBinding warehouseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voidmaw.warehouse",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (hudEditKey.wasPressed()) {
                client.setScreen(new HudEditScreen(client.currentScreen));
            }
            while (warehouseKey.wasPressed()) {
                ClientPlayNetworking.send(OpenWarehousePayload.INSTANCE);
            }
        });
    }
}
