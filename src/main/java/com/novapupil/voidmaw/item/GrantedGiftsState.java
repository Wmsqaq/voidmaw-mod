package com.novapupil.voidmaw.item;

import com.novapupil.voidmaw.VoidMaw;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers which players already received their one-time Singularity Core gift.
 * Stored in the overworld so it survives restarts and prevents relog farming.
 */
public class GrantedGiftsState extends PersistentState {
    private static final PersistentStateType<GrantedGiftsState> TYPE = new PersistentStateType<>(
            "voidmaw_gifts",
            GrantedGiftsState::new,
            NbtCompound.CODEC.xmap(GrantedGiftsState::fromNbt, GrantedGiftsState::toNbt),
            null
    );

    private final Set<UUID> granted = new HashSet<>();

    public GrantedGiftsState() {
    }

    private GrantedGiftsState(Set<UUID> granted) {
        this.granted.addAll(granted);
    }

    public static GrantedGiftsState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public boolean alreadyGranted(UUID playerId) {
        return granted.contains(playerId);
    }

    public void markGranted(UUID playerId) {
        if (granted.add(playerId)) {
            markDirty();
        }
    }

    private static GrantedGiftsState fromNbt(NbtCompound nbt) {
        Set<UUID> loaded = new HashSet<>();
        for (String key : nbt.getKeys()) {
            try {
                if (nbt.getBoolean(key, false)) {
                    loaded.add(UUID.fromString(key));
                }
            } catch (IllegalArgumentException e) {
                VoidMaw.LOGGER.warn("Skipping malformed key '{}' in voidmaw gift state", key);
            }
        }
        return new GrantedGiftsState(loaded);
    }

    private NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        for (UUID uuid : granted) {
            nbt.putBoolean(uuid.toString(), true);
        }
        return nbt;
    }
}
