package com.novapupil.voidmaw;

import com.novapupil.voidmaw.net.MassSyncPayload;
import com.novapupil.voidmaw.render.BlackHoleHud;
import com.novapupil.voidmaw.render.BlackHoleRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;

public class VoidMawClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MassSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> BlackHoleRenderer.updateState(payload)));
        BlackHoleRenderer.init();
        HudElementRegistry.addLast(Identifier.of(VoidMaw.MOD_ID, "mass_hud"), new BlackHoleHud());
    }
}
