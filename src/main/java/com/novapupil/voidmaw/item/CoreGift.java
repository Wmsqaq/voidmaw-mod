package com.novapupil.voidmaw.item;

import com.novapupil.voidmaw.blackhole.Balance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.UUID;

/** Hands every player their one-time Singularity Core when they first enter the world. */
public final class CoreGift {
    private CoreGift() {
    }

    public static void onJoin(ServerPlayerEntity player) {
        if (!Balance.GIVE_CORE_ON_FIRST_JOIN) {
            return;
        }
        MinecraftServer server = ((ServerWorld) player.getEntityWorld()).getServer();
        GrantedGiftsState state = GrantedGiftsState.get(server);
        UUID playerId = player.getUuid();
        if (state.alreadyGranted(playerId)) {
            return;
        }
        CoreLock.ensure(player);
        state.markGranted(playerId);
        player.sendMessage(Text.translatable("commands.voidmaw.gift_received"), false);
    }
}
