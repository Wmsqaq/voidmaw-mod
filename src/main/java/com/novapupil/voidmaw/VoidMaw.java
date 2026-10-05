package com.novapupil.voidmaw;

import com.novapupil.voidmaw.blackhole.BlackHoleManager;
import com.novapupil.voidmaw.command.VoidMawCommand;
import com.novapupil.voidmaw.item.CoreGift;
import com.novapupil.voidmaw.item.ModItems;
import com.novapupil.voidmaw.net.MassSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoidMaw implements ModInitializer {
    public static final String MOD_ID = "voidmaw";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.register();
        PayloadTypeRegistry.playS2C().register(MassSyncPayload.ID, MassSyncPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(BlackHoleManager::tick);
        // One Singularity Core per player, on their first join into this world.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CoreGift.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BlackHoleManager.onDisconnect(handler.getPlayer()));
        // Death forces the maw shut, releasing the stored mass.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                BlackHoleManager.onDeath(player);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VoidMawCommand.register(dispatcher));
        LOGGER.info("Void Maw initialized - the maw hungers.");
    }
}
