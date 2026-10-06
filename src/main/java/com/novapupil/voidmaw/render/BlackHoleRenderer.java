package com.novapupil.voidmaw.render;

import com.novapupil.voidmaw.net.MassSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** HUD state tracking; the server-spawned disc does not hide the player. */
public final class BlackHoleRenderer {
    private static final long STALE_AFTER_TICKS = 100;
    private static final Map<UUID, Hole> HOLES = new HashMap<>();
    private static ClientWorld world;
    private static long clientTicks;

    public record Hole(int level, double mass, double radius, double mouthY, int ticksLeft, long updatedAt) {
        /** A legacy server has no timer; -1 means unknown, rather than zero. */
        public int remainingTicks() {
            return ticksLeft < 0 ? -1 : (int) Math.max(0, ticksLeft - (clientTicks - updatedAt));
        }
    }

    private BlackHoleRenderer() {
    }

    public static void updateState(MassSyncPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        useWorld(client.world);
        if (world == null || client.getNetworkHandler() == null) {
            return;
        }
        if (payload.active()) {
            HOLES.put(payload.playerId(), new Hole(payload.level(), payload.mass(),
                    payload.radius(), payload.mouthY(), payload.ticksLeft(), clientTicks));
        } else {
            HOLES.remove(payload.playerId());
        }
    }

    public static Hole viewOf(UUID playerId) {
        useWorld(MinecraftClient.getInstance().world);
        return HOLES.get(playerId);
    }

    public static void init() {
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, newWorld) -> useWorld(newWorld));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(BlackHoleRenderer::tick);

    }

    private static void useWorld(ClientWorld newWorld) {
        if (world != newWorld) {
            reset();
            world = newWorld;
        }
    }

    private static void reset() {
        HOLES.clear();
        clientTicks = 0;
        world = null;
        BlackHoleHud.clearBounds();
    }

    private static void tick(MinecraftClient client) {
        useWorld(client.world);
        if (world == null || client.isPaused()) {
            return;
        }
        clientTicks++;
        HOLES.entrySet().removeIf(entry -> clientTicks - entry.getValue().updatedAt() > STALE_AFTER_TICKS);
    }
}
