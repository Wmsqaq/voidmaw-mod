package com.novapupil.voidmaw.render;

import com.novapupil.voidmaw.VoidMaw;
import com.novapupil.voidmaw.net.MassSyncPayload;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client side of the maw: draws a FLAT hole decal lying on the ground at the pit
 * mouth. The decal is a textured entity-translucent quad - entity layers render fine
 * under Iris/Sodium, while vanilla debug render layers (the previous approach) get
 * dropped by shader pipelines. The host's model is hidden and per-player hole state
 * is kept fresh from server syncs.
 */
public final class BlackHoleRenderer {
    /** Hole states stop being drawn if the server stays silent for this long. */
    private static final long STALE_AFTER_MS = 5000;
    private static final Identifier DISC_TEXTURE = Identifier.of(VoidMaw.MOD_ID, "textures/hole_disc.png");
    private static final Map<UUID, Hole> HOLES = new HashMap<>();
    private static final Map<UUID, Display> DISPLAYS = new HashMap<>();

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

    private BlackHoleRenderer() {
    }

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
            DISPLAYS.clear();
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

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getEntityTranslucent(DISC_TEXTURE));
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

            // The texture's radial gradient makes the visible hole; the quad is a bit
            // larger than the pull radius and gently breathes.
            double radius = display.radius * 1.15 * (1.0 + 0.02 * Math.sin(now / 300.0));
            float y = (float) (display.mouthY + 0.03);
            float x1 = (float) (entity.getX() - radius);
            float x2 = (float) (entity.getX() + radius);
            float z1 = (float) (entity.getZ() - radius);
            float z2 = (float) (entity.getZ() + radius);
            emitQuad(consumer, matrix, x1, z1, x2, z2, y, true);
            emitQuad(consumer, matrix, x1, z1, x2, z2, y, false);
        }

        matrices.pop();
        consumers.drawCurrentLayer();
    }

    /** One horizontal quad; the second (reversed) pass keeps it visible from below. */
    private static void emitQuad(VertexConsumer consumer, Matrix4f matrix,
                                 float x1, float z1, float x2, float z2, float y, boolean top) {
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        if (top) {
            vertex(consumer, matrix, x1, y, z1, 0f, 0f, light, 0f, 1f, 0f);
            vertex(consumer, matrix, x1, y, z2, 0f, 1f, light, 0f, 1f, 0f);
            vertex(consumer, matrix, x2, y, z2, 1f, 1f, light, 0f, 1f, 0f);
            vertex(consumer, matrix, x2, y, z1, 1f, 0f, light, 0f, 1f, 0f);
        } else {
            vertex(consumer, matrix, x1, y, z1, 0f, 0f, light, 0f, -1f, 0f);
            vertex(consumer, matrix, x2, y, z1, 1f, 0f, light, 0f, -1f, 0f);
            vertex(consumer, matrix, x2, y, z2, 1f, 1f, light, 0f, -1f, 0f);
            vertex(consumer, matrix, x1, y, z2, 0f, 1f, light, 0f, -1f, 0f);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               float x, float y, float z, float u, float v, int light,
                               float nx, float ny, float nz) {
        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(nx, ny, nz);
    }
}
