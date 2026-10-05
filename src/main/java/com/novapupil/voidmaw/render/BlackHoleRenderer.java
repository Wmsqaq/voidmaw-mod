package com.novapupil.voidmaw.render;

import com.novapupil.voidmaw.net.MassSyncPayload;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client side of the maw: draws a FLAT black disc lying on the ground at the pit
 * mouth (Hole.io look) - the actual pit is carved by the server. Also hides the
 * host's model and keeps per-player hole state fresh from server syncs.
 */
public final class BlackHoleRenderer {
    /** Hole states stop being drawn if the server stays silent for this long. */
    private static final long STALE_AFTER_MS = 5000;
    private static final Map<UUID, Hole> HOLES = new HashMap<>();

    public record Hole(int level, double mass, double radius, double mouthY, long updatedAt) {
    }

    /** Smoothly eases the drawn mouth height/radius toward the synced targets. */
    private static final class Display {
        double mouthY;
        double radius;

        Display(double mouthY, double radius) {
            this.mouthY = mouthY;
            this.radius = radius;
        }

        void approach(double targetMouthY, double targetRadius) {
            if (Math.abs(targetMouthY - mouthY) > 4.0) {
                mouthY = targetMouthY;
            } else {
                mouthY += (targetMouthY - mouthY) * 0.25;
            }
            radius += (targetRadius - radius) * 0.2;
        }
    }

    private static final Map<UUID, Display> DISPLAYS = new HashMap<>();

    public static void updateState(MassSyncPayload payload) {
        if (payload.active()) {
            HOLES.put(payload.playerId(), new Hole(payload.level(), payload.mass(),
                    payload.radius(), payload.mouthY(), System.currentTimeMillis()));
        } else {
            HOLES.remove(payload.playerId());
            DISPLAYS.remove(payload.playerId());
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
        WorldRenderEvents.END_MAIN.register(BlackHoleRenderer::render);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(BlackHoleRenderer::tick);
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
                DISPLAYS.remove(entry.getKey());
                continue;
            }
            Entity entity = client.world.getEntity(entry.getKey());
            if (entity != null) {
                entity.setInvisible(true);
            }
        }
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || HOLES.isEmpty()) {
            return;
        }
        if (!(context.consumers() instanceof VertexConsumerProvider.Immediate consumers)) {
            return;
        }
        MatrixStack matrices = context.matrices();
        if (matrices == null) {
            return;
        }
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getDebugTriangleFan());
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Hole> entry : HOLES.entrySet()) {
            Entity entity = client.world.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            Hole hole = entry.getValue();
            Display display = DISPLAYS.computeIfAbsent(entry.getKey(),
                    k -> new Display(hole.mouthY(), hole.radius()));
            display.approach(hole.mouthY(), hole.radius());
            double radius = display.radius * (1.0 + 0.02 * Math.sin(now / 300.0));
            // Flat hole lying on the ground: a dark violet under-layer forms the rim,
            // the near-black core sits a hair above it. Sunk slightly below the mouth
            // plane so terrain edges do not z-fight.
            Vec3d rim = new Vec3d(entity.getX(), display.mouthY - 0.01, entity.getZ());
            Vec3d core = new Vec3d(entity.getX(), display.mouthY - 0.005, entity.getZ());
            emitDisc(consumer, matrix, rim, radius * 1.10, true, 58, 16, 92);
            emitDisc(consumer, matrix, rim, radius * 1.10, false, 58, 16, 92);
            emitDisc(consumer, matrix, core, radius, true, 4, 2, 8);
            emitDisc(consumer, matrix, core, radius, false, 4, 2, 8);
        }

        matrices.pop();
        consumers.drawCurrentLayer();
    }

    /** Emits one flat disc as a triangle fan (center + ring); pass both windings. */
    private static void emitDisc(VertexConsumer consumer, Matrix4f matrix, Vec3d center,
                                 double radius, boolean reverse, int r, int g, int b) {
        consumer.vertex(matrix, (float) center.x, (float) center.y, (float) center.z).color(r, g, b, 255);
        int segments = Math.max(12, (int) (radius * 6));
        for (int i = 0; i <= segments; i++) {
            int index = reverse ? segments - i : i;
            double phi = Math.PI * 2.0 * index / segments;
            consumer.vertex(matrix,
                    (float) (center.x + radius * Math.cos(phi)),
                    (float) center.y,
                    (float) (center.z + radius * Math.sin(phi))).color(r, g, b, 255);
        }
    }
}
