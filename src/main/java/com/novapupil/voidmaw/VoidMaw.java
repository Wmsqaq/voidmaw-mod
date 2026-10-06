package com.novapupil.voidmaw;

import com.novapupil.voidmaw.blackhole.BlackHoleManager;
import com.novapupil.voidmaw.command.VoidMawCommand;
import com.novapupil.voidmaw.item.CoreGift;
import com.novapupil.voidmaw.item.CoreLock;
import com.novapupil.voidmaw.item.ModItems;
import com.novapupil.voidmaw.net.MassSyncPayload;
import com.novapupil.voidmaw.net.OpenWarehousePayload;
import com.novapupil.voidmaw.warehouse.WarehouseUi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.novapupil.voidmaw.warehouse.BlackHoleWarehouse;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        PayloadTypeRegistry.playS2C().register(MassSyncPayload.V2_ID, MassSyncPayload.LEGACY_CODEC);
        PayloadTypeRegistry.playS2C().register(MassSyncPayload.LEGACY_ID, MassSyncPayload.LEGACY_CODEC);
        PayloadTypeRegistry.playC2S().register(OpenWarehousePayload.ID, OpenWarehousePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
                com.novapupil.voidmaw.warehouse.WarehouseActionPayload.ID,
                com.novapupil.voidmaw.warehouse.WarehouseActionPayload.CODEC);
        // The typed receiver already runs on the server thread. Handle it before
        // a disconnect can leave a queued request holding an obsolete player.
        ServerPlayNetworking.registerGlobalReceiver(OpenWarehousePayload.ID, (payload, context) ->
                WarehouseUi.open(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(
                com.novapupil.voidmaw.warehouse.WarehouseActionPayload.ID, (payload, context) -> {
                    if (context.player().currentScreenHandler
                            instanceof com.novapupil.voidmaw.warehouse.WarehouseScreenHandler handler) {
                        handler.handleAction(payload.slot(), payload.action());
                    }
                });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BlackHoleWarehouse.flushAll(server);
            com.novapupil.voidmaw.blackhole.HoleProgress.flushAll(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BlackHoleManager.reset();
            BlackHoleWarehouse.unload(server);
            com.novapupil.voidmaw.blackhole.HoleProgress.unload(server);
        });
        // One Singularity Core per player, on their first join into this world;
        // plus the hardcoded join copyright broadcast for every join.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joined = handler.getPlayer();
            CoreGift.onJoin(joined);
            CoreLock.ensure(joined);
            VoidMawBroadcast.sendJoinNotice(joined);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CoreLock.ensure(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BlackHoleManager.tick(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                CoreLock.ensure(player);
            }
        });
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
