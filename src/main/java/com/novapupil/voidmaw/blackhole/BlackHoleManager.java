package com.novapupil.voidmaw.blackhole;

import com.novapupil.voidmaw.item.ModItems;
import com.novapupil.voidmaw.net.MassSyncPayload;
import com.novapupil.voidmaw.warehouse.BlackHoleWarehouse;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
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
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
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
 * blocks shrink away as ghost displays and entities are dragged over the rim and
 * devoured at the bottom. Everything swallowed ends up in the owner's warehouse.
 *
 * All visuals are plain vanilla display entities (ItemDisplay disc, BlockDisplay
 * ghosts) so every client - vanilla, Sodium or Iris - renders them through the
 * standard entity pipeline.
 */
public final class BlackHoleManager {
    public static final Text ALREADY_OPEN = Text.translatable("commands.voidmaw.already");

    private static final Map<UUID, BlackHoleState> ACTIVE = new HashMap<>();
    /** Tags every visual display we spawn, so orphaned ones can be cleaned up on load. */
    private static final String DISPLAY_TAG = "voidmaw_visual";
    /** Ghost blocks shrink in visible steps so the animation never depends on client interpolation. */
    private static final int SHRINK_STEPS = 5;
    private static final int SHRINK_STEP_TICKS = 2;
    private static final List<Shrinking> SHRINKING = new ArrayList<>();

    /** A block ghost stepping down from full size to nothing, then discarded. */
    private static final class Shrinking {
        final BlockDisplayEntity display;
        int step;
        int ticksToNextStep = SHRINK_STEP_TICKS;

        Shrinking(BlockDisplayEntity display) {
            this.display = display;
        }
    }

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
        discardDiscVisual(state);
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
        BlackHoleState state = ACTIVE.remove(player.getUuid());
        if (state == null) {
            return;
        }
        discardDiscVisual(state);
        MassSyncPayload payload = new MassSyncPayload(player.getUuid(), false, 0, 0.0, 0.0, 0.0);
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(watcher, payload);
        }
    }

    /**
     * Discards tagged visuals left over from a server save mid-hole (restarts while a
     * hole was open) that loaded back with nobody managing them.
     */
    private static void sweepOrphanVisuals(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof ItemDisplayEntity) && !(entity instanceof BlockDisplayEntity)) {
                    continue;
                }
                if (!entity.getCommandTags().contains(DISPLAY_TAG) || isManagedVisual(entity)) {
                    continue;
                }
                entity.discard();
            }
        }
    }

    private static boolean isManagedVisual(Entity entity) {
        for (BlackHoleState state : ACTIVE.values()) {
            if (state.discVisual() == entity) {
                return true;
            }
        }
        for (Shrinking shrinking : SHRINKING) {
            if (shrinking.display == entity) {
                return true;
            }
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (!ACTIVE.isEmpty() && server.getTicks() % 40 == 0) {
            BlackHoleWarehouse.flushAll();
        }
        if (server.getTicks() % 100 == 0) {
            sweepOrphanVisuals(server);
        }
        tickShrinking();
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

            // The mouth plane always re-anchors to the surface underfoot, so walking
            // into tunnels or caves drags it down with you instead of leaving it
            // floating at head height. The scan starts below the eyes and runs
            // downward, so low ceilings are never mistaken for the surface.
            double mouth = surfaceUnder(world, player, state.mouthY());
            state.setMouthY(mouth);

            int level = HoleLevel.levelFor(state.mass());
            if (level > state.level()) {
                state.setLevel(level);
                levelUp(world, player, level);
            }

            double radius = HoleLevel.radiusFor(level);
            Vec3d mouthCenter = new Vec3d(player.getX(), mouth + 0.2, player.getZ());

            ensureDiscVisual(world, player, state, mouth, radius);
            suckEntities(world, player, state, level, radius, mouth);
            // Claim naturally-falling blocks (sand, gravel...) inside the disc: they
            // become shrinking ghosts, so they can never land and place themselves back.
            List<FallingBlockEntity> falling = world.getEntitiesByClass(FallingBlockEntity.class,
                    new Box(player.getX() - radius, mouth - 0.5, player.getZ() - radius,
                            player.getX() + radius, mouth + radius * 1.5, player.getZ() + radius),
                    Entity::isAlive);
            for (FallingBlockEntity fbe : falling) {
                BlockState blockState = fbe.getBlockState();
                state.addMass(MassTables.blockMass(blockState, world, fbe.getBlockPos()));
                for (ItemStack drop : LootHelper.blockLoot(world, blockState, fbe.getBlockPos())) {
                    insertOrSpill(player, state, drop);
                }
                spawnShrinkingBlock(world, fbe.getX() - 0.5, fbe.getY(), fbe.getZ() - 0.5, blockState);
                fbe.discard();
            }
            if (world.getTime() % 3 == 0) {
                devourBlocks(world, player, state, level, radius, mouth);
            }
            if (world.getTime() % 2 == 0) {
                ambientVortex(world, player, state, radius, mouth);
            }
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
        int bottom = Math.max(world.getBottomY(), MathHelper.floor(player.getY()) - 16);
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

    /**
     * The flat hole visual: a fullbright hole-disc item display gliding at the mouth.
     * The scale is stepped server-side toward the level radius so the size change is
     * always visible, interpolation or not.
     */
    private static void ensureDiscVisual(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                         double mouth, double radius) {
        ItemDisplayEntity disc = state.discVisual();
        if (disc == null || disc.isRemoved()) {
            disc = new ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
            disc.setPosition(player.getX(), mouth + 0.03, player.getZ());
            disc.setItemStack(new ItemStack(ModItems.HOLE_DISC));
            // Fullbright so the violet rim stays visible in caves and at night.
            disc.setBrightness(new Brightness(15, 15));
            disc.addCommandTag(DISPLAY_TAG);
            disc.setTeleportDuration(3);
            disc.setInterpolationDuration(2);
            float diameter = (float) (radius * 2.3);
            disc.setTransformation(scaleTransform(diameter));
            state.setDiscScale(diameter);
            world.spawnEntity(disc);
            state.setDiscVisual(disc);
            return;
        }
        disc.setPosition(player.getX(), mouth + 0.03, player.getZ());
        float target = (float) (radius * 2.3);
        float current = state.discScale();
        if (Math.abs(target - current) > 0.05f) {
            float stepped = current + (target - current) * 0.3f;
            if (Math.abs(target - stepped) < 0.05f) {
                stepped = target;
            }
            disc.setTransformation(scaleTransform(stepped));
            state.setDiscScale(stepped);
        }
    }

    private static void discardDiscVisual(BlackHoleState state) {
        ItemDisplayEntity disc = state.discVisual();
        if (disc != null) {
            disc.discard();
            state.setDiscVisual(null);
        }
    }

    private static AffineTransformation scaleTransform(float diameter) {
        return new AffineTransformation(null, null, new Vector3f(diameter, diameter, diameter), null);
    }

    /** A ghost of the eaten block that steps down to nothing over a few ticks. */
    private static void spawnShrinkingBlock(ServerWorld world, double x, double y, double z, BlockState blockState) {
        BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        display.setPosition(x, y, z);
        display.setBlockState(blockState);
        display.addCommandTag(DISPLAY_TAG);
        display.setInterpolationDuration(SHRINK_STEP_TICKS);
        display.setTransformation(AffineTransformation.identity());
        world.spawnEntity(display);
        SHRINKING.add(new Shrinking(display));
    }

    private static void tickShrinking() {
        Iterator<Shrinking> iterator = SHRINKING.iterator();
        while (iterator.hasNext()) {
            Shrinking shrinking = iterator.next();
            if (shrinking.display.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (--shrinking.ticksToNextStep > 0) {
                continue;
            }
            shrinking.ticksToNextStep = SHRINK_STEP_TICKS;
            shrinking.step++;
            if (shrinking.step >= SHRINK_STEPS) {
                shrinking.display.discard();
                iterator.remove();
                continue;
            }
            float scale = Math.max(1.0f - shrinking.step / (float) SHRINK_STEPS, 0.02f);
            shrinking.display.setTransformation(shrinkTransform(scale));
        }
    }

    /** Collapse toward the block's centre: p -> s*p + (1-s)*0.5. */
    private static AffineTransformation shrinkTransform(float scale) {
        float t = (1.0f - scale) * 0.5f;
        return new AffineTransformation(new Vector3f(t, t, t), null, new Vector3f(scale, scale, scale), null);
    }

    private static void suckEntities(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                     int level, double radius, double mouth) {
        Box box = new Box(
                player.getX() - radius, mouth - 0.5, player.getZ() - radius,
                player.getX() + radius, mouth + radius * 1.5, player.getZ() + radius);
        List<Entity> victims = world.getOtherEntities(player, box, entity ->
                entity.isAlive() && !entity.isSpectator() && !(entity instanceof PlayerEntity)
                        // Falling blocks digest through the block-loot path instead.
                        && !(entity instanceof FallingBlockEntity)
                        && !(entity instanceof ItemDisplayEntity)
                        && !(entity instanceof BlockDisplayEntity)
                        && fitsInMaw(entity, level));
        Vec3d mouthCenter = new Vec3d(player.getX(), mouth + 0.2, player.getZ());
        double devourHorizSq = radius * radius * 0.45;

        for (Entity entity : victims) {
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            if (dx * dx + dz * dz > devourHorizSq) {
                // Inside the disc but not at the core yet: dragged toward the center.
                // Velocity is set directly every tick so friction can't fight the pull.
                Vec3d delta = mouthCenter.subtract(coreOf(entity));
                double dist = delta.length();
                if (dist < 1.0e-4) {
                    continue;
                }
                Vec3d dir = delta.multiply(1.0 / dist);
                double speed = Balance.pullSpeedFor(level);
                entity.setVelocity(dir.x * speed,
                        entity.getVelocity().y + (entity.isOnGround() ? 0.08 : 0.0),
                        dir.z * speed);
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

                // The block is digested immediately; a ghost display shrinks into
                // nothing where it stood, so nothing ever falls back or tumbles.
                world.breakBlock(pos, false, player, 512);
                state.addMass(MassTables.blockMass(blockState, world, pos));
                for (ItemStack drop : LootHelper.blockLoot(world, blockState, pos)) {
                    insertOrSpill(player, state, drop);
                }
                spawnShrinkingBlock(world, pos.getX(), pos.getY(), pos.getZ(), blockState);
                world.playSound(null, pos, blockState.getSoundGroup().getBreakSound(),
                        SoundCategory.BLOCKS, 0.4f, 0.7f);
                break;
            }
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
