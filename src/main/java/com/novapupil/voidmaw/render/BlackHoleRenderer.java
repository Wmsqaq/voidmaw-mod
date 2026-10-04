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
 * Client side of the maw: draws the growing black sphere, hides the host's model,
 * and keeps per-player hole state fresh from server syncs.
 */
public final class BlackHoleRenderer {
    /** Hole states stop being drawn if the server stays silent for this long. */
    private static final long STALE_AFTER_MS = 5000;
    private static final float CORE_RGB_SCALE = 1.0f;
    private static final Map<UUID, Hole> HOLES = new HashMap<>();

    public record Hole(double mass, double radius, long updatedAt) {
    }

    private BlackHoleRenderer() {
    }

    public static void updateState(MassSyncPayload payload) {
        if (payload.active()) {
            HOLES.put(payload.playerId(),
                    new Hole(payload.mass(), payload.radius(), System.currentTimeMillis()));
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

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getDebugQuads());
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Hole> entry : HOLES.entrySet()) {
            Entity entity = client.world.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            double radius = entry.getValue().radius() * 0.45;
            // Subtle pulse so the horizon feels alive.
            radius *= 1.0 + 0.03 * Math.sin(now / 300.0);
            Vec3d center = new Vec3d(entity.getX(), entity.getY() + 1.0, entity.getZ());
            drawSphere(consumer, matrix, center, radius);
        }

        matrices.pop();
        consumers.drawCurrentLayer();
    }

    private static void drawSphere(VertexConsumer consumer, Matrix4f matrix, Vec3d center, double radius) {
        int rings = 7;
        int sectors = 14;
        for (int i = 0; i < rings; i++) {
            double theta1 = Math.PI * i / rings;
            double theta2 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < sectors; j++) {
                double phi1 = 2.0 * Math.PI * j / sectors;
                double phi2 = 2.0 * Math.PI * (j + 1) / sectors;
                vertex(consumer, matrix, center, radius, theta1, phi1);
                vertex(consumer, matrix, center, radius, theta2, phi1);
                vertex(consumer, matrix, center, radius, theta2, phi2);
                vertex(consumer, matrix, center, radius, theta1, phi2);
                // Same quad with reversed winding: the sphere stays visible regardless of cull state.
                vertex(consumer, matrix, center, radius, theta1, phi2);
                vertex(consumer, matrix, center, radius, theta2, phi2);
                vertex(consumer, matrix, center, radius, theta2, phi1);
                vertex(consumer, matrix, center, radius, theta1, phi1);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3d center,
                               double radius, double theta, double phi) {
        float x = (float) (center.x + radius * Math.sin(theta) * Math.cos(phi));
        float y = (float) (center.y + radius * Math.cos(theta));
        float z = (float) (center.z + radius * Math.sin(theta) * Math.sin(phi));
        consumer.vertex(matrix, x, y, z).color(
                (int) (5 * CORE_RGB_SCALE), (int) (2 * CORE_RGB_SCALE), (int) (10 * CORE_RGB_SCALE), 255);
    }
}
