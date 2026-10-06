package com.novapupil.voidmaw.blackhole;

import com.novapupil.voidmaw.item.ModItems;
import com.novapupil.voidmaw.net.MassSyncPayload;
import com.novapupil.voidmaw.warehouse.BlackHoleWarehouse;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
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
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side absorption and the flat disc at the owner's feet. */
public final class BlackHoleManager {
    public static final Text ALREADY_OPEN = Text.translatable("commands.voidmaw.already");

    private static final Map<UUID, BlackHoleState> ACTIVE = new HashMap<>();
    /** Tags every visual display we spawn, so orphaned ones can be cleaned up on load. */
    private static final String DISPLAY_TAG = "voidmaw_visual";
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
        BlackHoleState state;
        try {
            state = new BlackHoleState(HoleProgress.get(world(player).getServer(), player.getUuid()));
        } catch (IllegalStateException failure) {
            player.sendMessage(Text.translatable("commands.voidmaw.progress_unavailable"), false);
            return;
        }
        state.setMouthY(player.getY());
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
        state.retainProgress();
        discardDiscVisual(state);
        ServerWorld world = world(player);
        double mass = state.mass();
        int level = HoleLevel.levelFor(mass);

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
                    level, String.format(java.util.Locale.ROOT, "%.2f", power)), false);
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
            try {
                double mass = HoleProgress.get(world(player).getServer(), player.getUuid()).mass();
                player.sendMessage(Text.translatable("commands.voidmaw.status_saved", HoleLevel.levelFor(mass),
                        String.format(java.util.Locale.ROOT, "%.1f", mass)), false);
            } catch (IllegalStateException failure) {
                player.sendMessage(Text.translatable("commands.voidmaw.progress_unavailable"), false);
            }
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
        state.retainProgress();
        discardDiscVisual(state);
        MassSyncPayload payload = new MassSyncPayload(player.getUuid(), false, 0, 0.0, 0.0, 0.0, 0);
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
            sendState(watcher, payload);
        }
    }

    /**
     * Discards tagged visuals left over from a server save mid-hole (restarts while a
     * hole was open) that loaded back with nobody managing them.
     */
    private static void sweepOrphanVisuals(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof DisplayEntity)) {
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
        return ACTIVE.values().stream().anyMatch(state -> state.discVisual() == entity);
    }

    public static void reset() {
        ACTIVE.values().forEach(state -> {
            state.retainProgress();
            discardDiscVisual(state);
        });
        ACTIVE.clear();
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 == 0) {
            BlackHoleWarehouse.flushAll(server);
            HoleProgress.flushAll(server);
        }
        if (server.getTicks() % 100 == 0) {
            sweepOrphanVisuals(server);
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

            double mouth = player.getY();
            state.setMouthY(mouth);

            int level = HoleLevel.levelFor(state.mass());
            if (level > state.level()) {
                state.setLevel(level);
                levelUp(world, player, level);
            }

            double radius = HoleLevel.radiusFor(level);

            ensureDiscVisual(world, player, state, mouth, radius);
            suckEntities(world, player, state, level, radius, mouth);
            List<FallingBlockEntity> falling = world.getEntitiesByClass(FallingBlockEntity.class,
                    new Box(player.getX() - radius, mouth - 0.05, player.getZ() - radius,
                            player.getX() + radius, mouth + radius * 1.5, player.getZ() + radius),
                    entity -> entity.isAlive() && HoleGeometry.contains(entity.getX() - player.getX(),
                            entity.getZ() - player.getZ(), entity.getY(), mouth, radius)
                            && !entity.getBlockState().isIn(MassTables.UNSWALLOWABLE)
                            && entity.getBlockState().getHardness(world, entity.getBlockPos()) >= 0);
            for (FallingBlockEntity fbe : falling) {
                BlockState blockState = fbe.getBlockState();
                state.addMass(MassTables.blockMass(blockState, world, fbe.getBlockPos()));
                for (ItemStack drop : LootHelper.blockLoot(world, blockState, fbe.getBlockPos())) {
                    insertOrSpill(player, state, drop);
                }
                fbe.discard();
            }
            devourBlocks(world, player, state, radius, mouth);
            if (world.getTime() % 2 == 0) {
                ambientVortex(world, player, state, radius, mouth);
            }
            if (player.age % 10 == 0) {
                sync(player);
            }
        }
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
        if (disc != null && disc.getEntityWorld() != world) {
            state.retainProgress();
            discardDiscVisual(state);
            disc = null;
        }
        if (disc == null || disc.isRemoved()) {
            disc = new HoleDiscEntity(world);
            disc.setPosition(player.getX(), mouth + 0.03, player.getZ());
            disc.setItemStack(new ItemStack(ModItems.HOLE_DISC));
            // Fullbright so the violet rim stays visible in caves and at night.
            disc.setBrightness(new Brightness(15, 15));
            disc.addCommandTag(DISPLAY_TAG);
            disc.setTeleportDuration(1);
            disc.setInterpolationDuration(2);
            float diameter = (float) (radius * 2.3);
            disc.setTransformation(scaleTransform(diameter));
            state.setDiscScale(diameter);
            if (!world.spawnEntity(disc)) {
                return;
            }
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
            disc.setStartInterpolation(0);
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
        return new AffineTransformation(null, null, new Vector3f(diameter, 1.0f, diameter), null);
    }

    private static void suckEntities(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                     int level, double radius, double mouth) {
        Box box = new Box(
                player.getX() - radius, mouth - 0.05, player.getZ() - radius,
                player.getX() + radius, mouth + radius * 1.5, player.getZ() + radius);
        List<Entity> victims = world.getOtherEntities(player, box, entity ->
                entity.isAlive() && !entity.isSpectator() && !(entity instanceof PlayerEntity)
                        && (entity instanceof LivingEntity || entity instanceof ItemEntity
                                || entity instanceof ExperienceOrbEntity)
                        && HoleGeometry.contains(entity.getX() - player.getX(), entity.getZ() - player.getZ(),
                                entity.getY(), mouth, radius)
                        && fitsInMaw(entity, level));
        Vec3d mouthCenter = new Vec3d(player.getX(), mouth + 0.2, player.getZ());
        double devourHorizSq = radius * radius * 0.45;

        for (Entity entity : victims) {
            double dx = entity.getX() - player.getX();
            double dz = entity.getZ() - player.getZ();
            if (dx * dx + dz * dz > devourHorizSq || entity.getY() > mouth + 0.8) {
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
                        HoleGeometry.suctionY(entity.getY(), mouth, entity.isOnGround()),
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
            if (living instanceof net.minecraft.entity.InventoryOwner owner) {
                Inventory inventory = owner.getInventory();
                for (int slot = 0; slot < inventory.size(); slot++) {
                    insertOrSpill(player, state, inventory.getStack(slot).copy());
                }
                inventory.clear();
            }
            if (living instanceof net.minecraft.entity.passive.AbstractHorseEntity horse) {
                for (int slot = 0; slot < horse.getInventorySize(); slot++) {
                    var reference = horse.getStackReference(500 + slot);
                    insertOrSpill(player, state, reference.get().copy());
                    reference.set(ItemStack.EMPTY);
                }
            }
        }

        world.spawnParticles(ParticleTypes.POOF,
                entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 0.5f, 0.6f);
        entity.discard();
    }

    /**
     * Devours blocks by walking chunk sections directly through the chunk palette:
     * empty sections cost a single isEmpty() call, rows outside the disc are cut by
     * geometry, and only real cells count against the scan budget - the same coverage
     * in a fraction of the lookups the old per-position world scan needed. Sections
     * run bottom-up so the disc eats the layer nearest to it first.
     */
    private static void devourBlocks(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                     double radius, double mouth) {
        double centerX = player.getX();
        double centerZ = player.getZ();
        int bottom = Math.max(world.getBottomY(), HoleGeometry.firstBlockY(mouth));
        int top = world.getTopYInclusive();
        int minCx = DiscSweep.minChunk(centerX, radius);
        int maxCx = DiscSweep.maxChunk(centerX, radius);
        int minCz = DiscSweep.minChunk(centerZ, radius);
        int maxCz = DiscSweep.maxChunk(centerZ, radius);
        int zSpan = maxCz - minCz + 1;
        long chunkVolume = DiscSweep.volume(minCx, maxCx, minCz, maxCz);
        if (chunkVolume == 0) {
            return;
        }
        int baseSection = world.getBottomY() >> 4;
        int bottomSection = Math.max(0, (bottom >> 4) - baseSection);
        int consumed = 0;
        int checked = 0;

        sweep:
        for (int visited = 0; visited < chunkVolume; visited++) {
            long index = state.nextSweepIndex(chunkVolume);
            int cx = DiscSweep.chunkX(index, minCx, zSpan);
            int cz = DiscSweep.chunkZ(index, minCz, zSpan);
            // create=false: never force a chunk into memory for the maw.
            Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }
            ChunkSection[] sections = chunk.getSectionArray();
            int highest = chunk.getHighestNonEmptySection();
            if (highest < bottomSection) {
                continue;
            }
            int topSection = Math.min(highest, Math.min(sections.length - 1, (top >> 4) - baseSection));
            DiscSweep.Row[] rows = new DiscSweep.Row[16];
            for (int lx = 0; lx < 16; lx++) {
                rows[lx] = DiscSweep.rowRange(cx, lx, centerX, centerZ, radius);
            }
            for (int s = bottomSection; s <= topSection; s++) {
                ChunkSection section = sections[s];
                if (section == null || section.isEmpty()) {
                    continue;
                }
                int sectionBaseY = (baseSection + s) << 4;
                for (int ly = 0; ly < 16; ly++) {
                    int y = sectionBaseY + ly;
                    if (y < bottom || y > top) {
                        continue;
                    }
                    for (int lx = 0; lx < 16; lx++) {
                        DiscSweep.Row row = rows[lx];
                        if (row.empty()) {
                            continue;
                        }
                        int x = (cx << 4) + lx;
                        for (int lz = row.start(); lz <= row.endInclusive(); lz++) {
                            if (++checked > Balance.BLOCK_SCAN_BUDGET || consumed >= Balance.MAX_BLOCKS_PER_TICK) {
                                break sweep;
                            }
                            BlockState blockState = section.getBlockState(lx, ly, lz);
                            if (blockState.isAir() || blockState.isIn(MassTables.UNSWALLOWABLE)) {
                                continue;
                            }
                            BlockPos pos = new BlockPos(x, y, (cz << 4) + lz);
                            if (blockState.getHardness(world, pos) < 0) {
                                continue;
                            }
                            if (absorbBlock(world, player, state, blockState, pos)) {
                                consumed++;
                            }
                        }
                    }
                }
            }
        }
    }

    /** Removes one block (or fluid column), banking its loot and mass; false if the world refused. */
    private static boolean absorbBlock(ServerWorld world, ServerPlayerEntity player, BlackHoleState state,
                                       BlockState blockState, BlockPos pos) {
        // Pure water/lava columns have no loot, no container and no break sound;
        // waterlogged solids keep their normal block treatment.
        boolean liquid = blockState.getBlock() instanceof net.minecraft.block.FluidBlock;
        BlockEntity blockEntity = liquid ? null : world.getBlockEntity(pos);
        List<ItemStack> drops = liquid
                ? List.of()
                : LootHelper.blockLoot(world, blockState, pos, blockEntity);
        List<ItemStack> contents = new java.util.ArrayList<>();
        if (!liquid && blockEntity instanceof Inventory inventory) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                contents.add(inventory.getStack(slot).copy());
            }
            inventory.clear();
        }
        double mass = liquid
                ? MassTables.liquidMass(blockState)
                : MassTables.blockMass(blockState, world, pos);
        if (!world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) {
            if (blockEntity instanceof Inventory inventory) {
                for (int slot = 0; slot < contents.size(); slot++) {
                    inventory.setStack(slot, contents.get(slot));
                }
            }
            return false;
        }
        state.addMass(mass);
        drops.forEach(drop -> insertOrSpill(player, state, drop));
        // Shulker-box loot already carries its contents as item components.
        if (!liquid && !(blockEntity instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity)) {
            contents.forEach(drop -> insertOrSpill(player, state, drop));
        }
        if (!liquid) {
            world.playSound(null, pos, blockState.getSoundGroup().getBreakSound(),
                    SoundCategory.BLOCKS, 0.25f, 0.7f);
        }
        return true;
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
                active ? state.mouthY() : 0.0,
                active ? state.ticksLeft() : 0);
        sendState(player, payload);
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(player)) {
            if (!watcher.getUuid().equals(player.getUuid())) {
                sendState(watcher, payload);
            }
        }
    }

    private static void sendState(ServerPlayerEntity player, MassSyncPayload payload) {
        if (ServerPlayNetworking.canSend(player, MassSyncPayload.ID)) {
            ServerPlayNetworking.send(player, payload);
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
