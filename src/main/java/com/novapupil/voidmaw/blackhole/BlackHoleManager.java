package com.novapupil.voidmaw.blackhole;

import com.novapupil.voidmaw.net.MassSyncPayload;
import com.novapupil.voidmaw.warehouse.BlackHoleWarehouse;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side brain of the maw, Hole.io style.
 *
 * Geometry: the hole is a flat pit lying ON the ground at the player's feet. Its mouth
 * is anchored to the terrain surface underfoot (and drags along as the player walks);
 * the pit floor is {@code mouth - 1 - level}. ONLY things above the pit floor are eaten:
 * blocks are peeled from the floor upward and tumble into the pit as falling blocks,
 * entities are dragged over the rim and devoured at the bottom. Everything swallowed
 * ends up in the owner's warehouse.
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
        BlackHoleState state = new BlackHoleState();
        state.setMouthY(surfaceUnder(world(player), player, player.getY()));
        ACTIVE.put(player.getUuid(), state);
        world(player).playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                SoundCategory.PLAYERS, 0.8f, 0.6f);
        player.sendMessage(Text.translatable("commands.voidmaw.started"), false);
        sync(player);
    }

    /**
     * Closes the maw. Only {@code detonate} releases the stored mass as a
     * level-scaled explosion; quiet closes (right-click, timeout, death) do not.
     */
    public static void stop(ServerPlayerEntity player, boolean detonate) {
        BlackHoleState state = ACTIVE.remove(player.getUuid());
        if (state == null) {
            return;
        }
        ServerWorld world = world(player);
        double mass = state.mass();
        int level = HoleLevel.levelFor(mass);

        player.removeStatusEffect(StatusEffects.SLOW_FALLING);
        player.removeStatusEffect(StatusEffects.SLOWNESS);

        if (detonate) {
            // Short immunity so the blast does not instantly kill its former host.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, true, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 60, 0, true, false));
            float power = HoleLevel.explosionPowerFor(level);
            world.createExplosion(player, player.getX(), player.getY(), player.getZ(),
                    power, World.ExplosionSourceType.MOB);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                    SoundCategory.PLAYERS, 2.0f, 0.8f);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    player.getX(), player.getY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            player.sendMessage(Text.translatable("commands.voidmaw.detonated",
                    level, String.format(java.util.Locale.ROOT, "%.0f", power)), false);
        } else {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                    SoundCategory.PLAYERS, 0.8f, 0.5f);
            player.sendMessage(Text.translatable("commands.voidmaw.stopped",
                    String.format(java.util.Locale.ROOT, "%.1f", mass)), false);
        }
        sync(player);
    }

    /** The maw quietly snaps shut when its host dies - only shift+use detonates. */
    public static void onDeath(ServerPlayerEntity player) {
        if (ACTIVE.containsKey(player.getUuid())) {
            stop(player, false);
        }
    }

    public static void sendStatus(ServerPlayerEntity player) {
        BlackHoleState state = ACTIVE.get(player.getUuid());
        if (state == null) {
            player.sendMessage(Text.translatable("commands.voidmaw.status_idle"), false);
        } else {
            int level = HoleLevel.levelFor(state.mass());
            player.sendMessage(Text.translatable("commands.voidmaw.status_active",
                    level,
                    String.format(java.util.Locale.ROOT, "%.1f", state.mass()),
                    String.format(java.util.Locale.ROOT, "%.1f", HoleLevel.radiusFor(level)),
                    (int) Math.ceil(state.ticksLeft() / 20.0)), false);
        }
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        if (ACTIVE.remove(player.getUuid()) == null) {
            return;
        }
        MassSyncPayload payload = new MassSyncPayload(player.getUuid(), false, 0, 0.0, 0.0, 0.0);
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(watcher, payload);
        }
    }

    public static void tick(MinecraftServer server) {
        if (!ACTIVE.isEmpty() && server.getTicks() % 40 == 0) {
            BlackHoleWarehouse.flushAll();
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BlackHoleState state = ACTIVE.get(player.getUuid());
            if (state == null) {
                continue;
            }
            state.tick();
            if (state.expired()) {
                stop(player, false);
                continue;
            }

            ServerWorld world = (ServerWorld) player.getEntityWorld();

            // The mouth plane sits exactly at the player's feet. NOTHING below it is
            // ever touched - the hole is a flat disc of annihilation lying on the
            // ground, devouring whatever rises above the plane inside its radius.
            double mouth = state.mouthY();
            if (player.getY() >= mouth - 0.5) {
                mouth = surfaceUnder(world, player, mouth);
                state.setMouthY(mouth);
            }

            int level = HoleLevel.levelFor(state.mass());
            if (level > state.level()) {
                state.setLevel(level);
                levelUp(world, player, level);
            }

            double radius = HoleLevel.radiusFor(level);
            Vec3d mouthCenter = new Vec3d(player.getX(), mouth + 0.2, player.getZ());

            suckEntities(world, player, state, level, radius, mouth);
            if (world.getTime() % 3 == 0) {
                devourBlocks(world, player, state, level, radius, mouth);
            }
            if (world.getTime() % 2 == 0) {
                ambientVortex(world, player, state, radius, mouth);
            }
            digestPending(world, player, state);
            if (player.age % 10 == 0) {
                sync(player);
            }
        }
    }

    /**
     * Ground height under the player: first non-air, non-fluid block below the feet.
     * Falls back to {@code fallback} when nothing solid is found (mid-air over a void).
     */
    private static double surfaceUnder(ServerWorld world, ServerPlayerEntity player, double fallback) {
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int top = MathHelper.floor(player.getY()) + 1;
        int bottom = Math.max(world.getBottomY(), MathHelper.floor(player.getY()) - 12);
        for (int y = top; y >= bottom; y--) {
            BlockState state = world.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.isReplaceable()) {
                return y + 1;
            }
        }
        return fallback;
    }

    private static void levelUp(ServerWorld world, ServerPlayerEntity player, int level) {
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 1.0f, 0.8f);
        double r = HoleLevel.radiusFor(level);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(), 30, r * 0.5, 1.0, r * 0.5, 0.4);
        player.sendMessage(Text.translatable("commands.voidmaw.levelup",
                level, String.format(java.util.Locale.ROOT, "%.1f", r)), false);
    }

    private static void suckEntities(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                     int level, double radius, double mouth) {
        Box box = new Box(
                player.getX() - radius, mouth - 0.5, player.getZ() - radius,
                player.getX() + radius, mouth + radius * 1.5, player.getZ() + radius);
        List<Entity> victims = world.getOtherEntities(player, box, entity ->
                entity.isAlive() && !entity.isSpectator() && !(entity instanceof PlayerEntity)
                        && fitsInMaw(entity, level));
        double strength = Balance.pullStrengthFor(state.mass());
        Vec3d mouthCenter = new Vec3d(player.getX(), mouth + 0.2, player.getZ());
        double devourHorizSq = radius * radius * 0.45;

        for (Entity entity : victims) {
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            if (dx * dx + dz * dz > devourHorizSq) {
                // Inside the disc but not at the core yet: dragged toward the center.
                Vec3d delta = mouthCenter.subtract(coreOf(entity));
                double dist = delta.length();
                if (dist < 1.0e-4) {
                    continue;
                }
                Vec3d dir = delta.multiply(1.0 / dist);
                entity.addVelocity(dir.x * strength, dir.y * strength * 0.5 + 0.01, dir.z * strength);
                entity.velocityModified = true;
            } else {
                // At the core of the disc: swallowed.
                devour(world, player, state, entity);
            }
        }
    }

    /** Hole.io rule: the maw only swallows what fits under its current level. */
    private static boolean fitsInMaw(Entity entity, int level) {
        if (entity instanceof ItemEntity || entity instanceof net.minecraft.entity.ExperienceOrbEntity) {
            return true;
        }
        Box box = entity.getBoundingBox();
        double avg = (box.getLengthX() + box.getLengthY() + box.getLengthZ()) / 3.0;
        return avg <= HoleLevel.maxEntitySizeFor(level);
    }

    private static void devour(ServerWorld world, ServerPlayerEntity player, BlackHoleState state, Entity entity) {
        state.addMass(MassTables.entityMass(entity));

        // Whatever the maw swallows ends up in its owner's warehouse.
        if (entity instanceof ItemEntity item) {
            insertOrSpill(player, state, item.getStack().copy());
        } else if (entity instanceof LivingEntity living) {
            for (ItemStack drop : LootHelper.entityLoot(world, living)) {
                insertOrSpill(player, state, drop);
            }
        }

        world.spawnParticles(ParticleTypes.POOF,
                entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 0.5f, 0.6f);
        entity.discard();
    }

    private static void devourBlocks(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                     int level, double radius, double mouth) {
        int attempts = Balance.blockAttemptsFor(state.mass());
        // ONLY blocks at or above the mouth plane are devoured - the terrain below
        // the disc (including right under the player's feet) is never damaged.
        // Taller structures are chewed bottom-up, level permitting.
        int bottom = MathHelper.floor(mouth);
        int top = MathHelper.floor(mouth) + 3 + level * 2;
        Vec3d mouthCenter = new Vec3d(player.getX(), mouth + 0.2, player.getZ());

        // Samples sweep the disc evenly (golden angle) instead of clumping.
        for (int i = 0; i < attempts; i++) {
            double angle = state.nextSweepAngle();
            double dist = Math.sqrt(world.random.nextDouble()) * radius;
            int x = MathHelper.floor(player.getX() + Math.cos(angle) * dist);
            int z = MathHelper.floor(player.getZ() + Math.sin(angle) * dist);

            // Column scan from the pit floor up to just above the mouth: the block
            // nearest the floor tears loose first. Below the floor the maw never digs.
            for (int y = bottom; y <= top; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState blockState = world.getBlockState(pos);
                if (blockState.isAir() || blockState.isIn(MassTables.UNSWALLOWABLE)) {
                    continue;
                }
                if (!blockState.getFluidState().isEmpty()) {
                    continue;
                }
                float hardness = blockState.getHardness(world, pos);
                if (hardness < 0.0f) {
                    continue;
                }
                if (hardness > HoleLevel.maxBlockHardnessFor(level)) {
                    continue;
                }

                // The block is torn loose and tumbles across the disc toward the core,
                // then gets digested where it lands.
                world.breakBlock(pos, false, player, 512);
                state.addMass(MassTables.blockMass(blockState, world, pos));

                FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos, blockState);
                falling.dropItem = false;
                double dx = mouthCenter.x - falling.getX();
                double dz = mouthCenter.z - falling.getZ();
                double horiz = Math.max(Math.hypot(dx, dz), 0.25);
                double push = Math.min(horiz * 0.08, 0.35);
                falling.setVelocity(dx / horiz * push, 0.14, dz / horiz * push);
                world.spawnEntity(falling);
                state.pendingBlocks().add(falling);
                world.playSound(null, pos, blockState.getSoundGroup().getBreakSound(),
                        SoundCategory.BLOCKS, 0.4f, 0.7f);
                break;
            }
        }
    }

    /** Torn-off blocks are digested as soon as they land: loot goes to the warehouse. */
    private static void digestPending(ServerWorld world, ServerPlayerEntity player, BlackHoleState state) {
        Iterator<FallingBlockEntity> iterator = state.pendingBlocks().iterator();
        while (iterator.hasNext()) {
            FallingBlockEntity falling = iterator.next();
            if (falling.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (!falling.isOnGround() && falling.timeFalling <= 20) {
                continue;
            }
            iterator.remove();
            BlockState blockState = falling.getBlockState();
            for (ItemStack drop : LootHelper.blockLoot(world, blockState, falling.getBlockPos())) {
                insertOrSpill(player, state, drop);
            }
            world.spawnParticles(ParticleTypes.CLOUD,
                    falling.getX(), falling.getY() + 0.3, falling.getZ(), 3, 0.3, 0.1, 0.3, 0.01);
            falling.discard();
        }
    }

    /** Puts a stack into the owner's warehouse; overflow is converted into mass. */
    private static void insertOrSpill(ServerPlayerEntity player, BlackHoleState state, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack leftover = BlackHoleWarehouse.get(world(player).getServer(), player.getUuid()).insert(stack);
        if (!leftover.isEmpty()) {
            state.addMass(0.2);
        }
    }

    /** Accretion shimmer above the mouth, for every viewer. */
    private static void ambientVortex(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                      double radius, double mouth) {
        Vec3d center = new Vec3d(player.getX(), mouth + 0.3, player.getZ());
        int count = (int) Math.min(2 + state.mass() / 8.0, 12);

        for (int i = 0; i < count; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            double dist = radius * (0.45 + world.random.nextDouble() * 0.75);
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;
            double y = center.y + world.random.nextDouble() * radius;
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
        int level = active ? HoleLevel.levelFor(state.mass()) : 0;
        MassSyncPayload payload = new MassSyncPayload(player.getUuid(), active, level,
                active ? state.mass() : 0.0,
                active ? HoleLevel.radiusFor(Math.max(level, 1)) : 0.0,
                active ? state.mouthY() : 0.0);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
            if (!watcher.getUuid().equals(player.getUuid())) {
                ServerPlayNetworking.send(watcher, payload);
            }
        }
    }

    /** The maw's mouth sits at the player's feet. */
    private static Vec3d coreOf(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private static ServerWorld world(ServerPlayerEntity player) {
        return (ServerWorld) player.getEntityWorld();
    }
}
