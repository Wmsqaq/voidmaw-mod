package com.novapupil.voidmaw.render;

import com.novapupil.voidmaw.net.MassSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client side bookkeeping of the maw: keeps per-player hole state fresh from server
 * syncs (the HUD reads it) and hides the host's model while they are the black hole.
 * The hole disc itself is a plain ItemDisplay entity spawned by the server, so no
 * custom world rendering is involved at all.
 */
public final class BlackHoleRenderer {
    /** Hole states stop being tracked if the server stays silent for this long. */
    private static final long STALE_AFTER_MS = 5000;
    private static final Map<UUID, Hole> HOLES = new HashMap<>();

    public record Hole(int level, double mass, double radius, double mouthY, int ticksLeft, long updatedAt) {
    }

    private BlackHoleRenderer() {
    }

    public static void updateState(MassSyncPayload payload) {
        if (payload.active()) {
            HOLES.put(payload.playerId(), new Hole(payload.level(), payload.mass(),
                    payload.radius(), payload.mouthY(), payload.ticksLeft(), System.currentTimeMillis()));
        } else {
            HOLES.remove(payload.playerId());
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                Entity entity = client.world.getEntity(payload.playerId());
                if (entity != null) {
                    entity.setInvisible(false);
                }
            }
        }
    }

    public static Hole viewOf(UUID playerId) {
        return HOLES.get(playerId);
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(BlackHoleRenderer::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            HOLES.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Hole>> iterator = HOLES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Hole> entry = iterator.next();
            if (now - entry.getValue().updatedAt() > STALE_AFTER_MS) {
                iterator.remove();
                continue;
            }
            Entity entity = client.world.getEntity(entry.getKey());
            if (entity != null) {
                entity.setInvisible(true);
            }
        }
    }
}
