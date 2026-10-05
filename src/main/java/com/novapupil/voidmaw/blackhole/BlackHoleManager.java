package com.novapupil.voidmaw.blackhole;

import com.novapupil.voidmaw.net.MassSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side brain of the black hole: suction, devouring, growth and the closing burst.
 */
public final class BlackHoleManager {
    public static final Text ALREADY_OPEN = Text.translatable("commands.voidmaw.already");

    private static final Map<UUID, BlackHoleState> ACTIVE = new HashMap<>();

    private BlackHoleManager() {
    }

    public static boolean isActive(ServerPlayerEntity player) {
        return ACTIVE.containsKey(player.getUuid());
    }

    public static void start(ServerPlayerEntity player) {
        if (ACTIVE.containsKey(player.getUuid())) {
            player.sendMessage(ALREADY_OPEN, false);
            return;
        }
        ACTIVE.put(player.getUuid(), new BlackHoleState());
        world(player).playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                SoundCategory.PLAYERS, 0.8f, 0.6f);
        player.sendMessage(Text.translatable("commands.voidmaw.started"), false);
        sync(player);
    }

    public static void stop(ServerPlayerEntity player, boolean withBurst) {
        BlackHoleState state = ACTIVE.remove(player.getUuid());
        if (state == null) {
            return;
        }
        ServerWorld world = world(player);
        double mass = state.mass();

        player.removeStatusEffect(StatusEffects.SLOW_FALLING);
        player.removeStatusEffect(StatusEffects.SLOWNESS);

        if (withBurst && mass > 0.0) {
            // Short immunity so the closing maw does not instantly kill its former host.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, true, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, true, false));
            world.createExplosion(player, player.getX(), player.getY() + 1.0, player.getZ(),
                    Balance.explosionPowerFor(mass), World.ExplosionSourceType.MOB);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                    SoundCategory.PLAYERS, 2.0f, 0.8f);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        } else {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                    SoundCategory.PLAYERS, 0.8f, 0.5f);
        }
        player.sendMessage(Text.translatable("commands.voidmaw.stopped",
                String.format(java.util.Locale.ROOT, "%.1f", mass)), false);
        sync(player);
    }

    /** The maw collapses violently when its host dies. */
    public static void onDeath(ServerPlayerEntity player) {
        if (ACTIVE.containsKey(player.getUuid())) {
            stop(player, true);
        }
    }

    public static void sendStatus(ServerPlayerEntity player) {
        BlackHoleState state = ACTIVE.get(player.getUuid());
        if (state == null) {
            player.sendMessage(Text.translatable("commands.voidmaw.status_idle"), false);
        } else {
            player.sendMessage(Text.translatable("commands.voidmaw.status_active",
                    String.format(java.util.Locale.ROOT, "%.1f", state.mass()),
                    String.format(java.util.Locale.ROOT, "%.1f", state.radius()),
                    (int) Math.ceil(state.ticksLeft() / 20.0)), false);
        }
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        if (ACTIVE.remove(player.getUuid()) == null) {
            return;
        }
        // Quietly close the maw: no burst at the logout spot, but viewers must be told.
        MassSyncPayload payload = new MassSyncPayload(player.getUuid(), false, 0.0, 0.0);
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(watcher, payload);
        }
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BlackHoleState state = ACTIVE.get(player.getUuid());
            if (state == null) {
                continue;
            }
            state.tick();
            if (state.expired()) {
                stop(player, true);
                continue;
            }

            // Float mode: no fall damage and a heavier step while the maw is open.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0, true, false));

            ServerWorld world = (ServerWorld) player.getEntityWorld();
            suckEntities(world, player, state);
            if (world.getTime() % 2 == 0) {
                devourBlocks(world, player, state);
            }
            if (world.getTime() % 2 == 0) {
                ambientVortex(world, player, state);
            }
            if (player.age % 10 == 0) {
                sync(player);
            }
        }
    }

    /** The maw's core sits one block above the host's feet. */
    private static Vec3d coreOf(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY() + 1.0, entity.getZ());
    }

    private static void suckEntities(ServerWorld world, ServerPlayerEntity player, BlackHoleState state) {
        Vec3d center = coreOf(player);
        double radius = state.radius();
        Box box = Box.of(center, radius * 2.0, radius * 2.0, radius * 2.0);
        List<Entity> victims = world.getOtherEntities(player, box,
                entity -> entity.isAlive() && !entity.isSpectator() && !(entity instanceof PlayerEntity));
        double strength = Balance.pullStrengthFor(state.mass());

        for (Entity entity : victims) {
            Vec3d delta = center.subtract(coreOf(entity));
            double dist = delta.length();
            if (dist > radius || dist < 1.0e-4) {
                continue;
            }
            // The maw feeds upward: things far below the core are left alone.
            if (entity.getY() < center.y - 2.0) {
                continue;
            }
            Vec3d dir = delta.multiply(1.0 / dist);
            // Close things get sucked in faster: factor ranges 0.6 (edge) .. 1.6 (core).
            double proximity = 1.6 - Math.min(dist / radius, 1.0);
            double pull = strength * proximity;
            entity.addVelocity(dir.x * pull, dir.y * pull + 0.02 * pull, dir.z * pull);
            entity.velocityModified = true;

            if (entity.squaredDistanceTo(center) <= Balance.DEVOUR_DISTANCE_SQ) {
                devour(world, state, entity);
            }
        }
    }

    private static void devour(ServerWorld world, BlackHoleState state, Entity entity) {
        state.addMass(MassTables.entityMass(entity));
        world.spawnParticles(ParticleTypes.POOF,
                entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 0.5f, 0.6f);
        entity.discard();
    }

    private static void devourBlocks(ServerWorld world, ServerPlayerEntity player, BlackHoleState state) {
        Vec3d center = coreOf(player);
        double radius = state.radius();
        int attempts = Balance.blockAttemptsFor(state.mass());
        int bottom = MathHelper.floor(center.y - 2.0);
        int top = MathHelper.floor(center.y + radius * 1.2);

        for (int i = 0; i < attempts; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(world.random.nextDouble()) * radius;
            int x = MathHelper.floor(center.x + Math.cos(angle) * dist);
            int z = MathHelper.floor(center.z + Math.sin(angle) * dist);

            // Column scan from the core outward: whatever sits closest to the maw
            // gets torn off first, so open terrain opens into a growing crater
            // instead of the old random sampling that mostly hit air.
            for (int y = bottom; y <= top; y++) {
                if (devourBlock(world, player, state, new BlockPos(x, y, z))) {
                    break;
                }
            }
        }
    }

    private static boolean devourBlock(ServerWorld world, ServerPlayerEntity player,
                                       BlackHoleState state, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        if (blockState.isAir() || blockState.isIn(MassTables.UNSWALLOWABLE)) {
            return false;
        }
        if (!blockState.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = blockState.getHardness(world, pos);
        if (hardness < 0.0f) {
            return false;
        }
        if (hardness > Balance.HARD_BLOCK_HARDNESS && state.mass() < Balance.HARD_BLOCK_MASS_GATE) {
            return false;
        }

        double gained = MassTables.blockMass(blockState, world, pos);
        world.breakBlock(pos, false, player, 512);
        state.addMass(gained);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
        world.playSound(null, pos, blockState.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 0.4f, 0.7f);
        return true;
    }

    /** Accretion shimmer: server-side particles swirl around the maw for every viewer. */
    private static void ambientVortex(ServerWorld world, ServerPlayerEntity player, BlackHoleState state) {
        Vec3d center = coreOf(player);
        double radius = state.radius();
        int count = (int) Math.min(2 + state.mass() / 8.0, 12);

        for (int i = 0; i < count; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            double dist = radius * (0.45 + world.random.nextDouble() * 0.75);
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;
            double y = center.y + (world.random.nextDouble() - 0.3) * radius * 0.8;
            world.spawnParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.08, -0.04, 0.08, 0.15);
        }
        if (world.random.nextInt(3) == 0) {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    center.x, center.y + radius * 0.5, center.z, 1, radius * 0.3, 0.2, radius * 0.3, 0.01);
        }
    }

    /** Push the hole state to the host player and everyone watching them. */
    private static void sync(ServerPlayerEntity player) {
        BlackHoleState state = ACTIVE.get(player.getUuid());
        boolean active = state != null;
        MassSyncPayload payload = new MassSyncPayload(player.getUuid(), active,
                active ? state.mass() : 0.0, active ? state.radius() : 0.0);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
            if (!watcher.getUuid().equals(player.getUuid())) {
                ServerPlayNetworking.send(watcher, payload);
            }
        }
    }

    private static ServerWorld world(ServerPlayerEntity player) {
        return (ServerWorld) player.getEntityWorld();
    }
}
