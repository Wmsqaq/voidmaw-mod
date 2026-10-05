package com.novapupil.voidmaw.warehouse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.novapupil.voidmaw.VoidMaw;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-black-hole item storage, keyed by the hole owner's UUID.
 *
 * Persistence is plain JSON files under {@code <world>/data/voidmaw/warehouses/<uuid>.json}
 * (stacks encoded with ItemStack.CODEC) - deliberately NOT NBT.
 */
public final class BlackHoleWarehouse {
    public static final int SIZE = 54;

    private static final Map<UUID, BlackHoleWarehouse> LOADED = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final UUID owner;
    private final MinecraftServer server;
    private final Path file;
    private final net.minecraft.util.collection.DefaultedList<ItemStack> stacks =
            net.minecraft.util.collection.DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
    private boolean dirty;

    private BlackHoleWarehouse(MinecraftServer server, UUID owner) {
        this.server = server;
        this.owner = owner;
        this.file = server.getSavePath(WorldSavePath.ROOT)
                .resolve("data/voidmaw/warehouses/" + owner + ".json");
    }

    public static BlackHoleWarehouse get(MinecraftServer server, UUID owner) {
        return LOADED.computeIfAbsent(owner, id -> {
            BlackHoleWarehouse warehouse = new BlackHoleWarehouse(server, id);
            warehouse.load();
            return warehouse;
        });
    }

    /** Fills matching partial stacks first, then empty slots. Returns whatever did not fit. */
    public ItemStack insert(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        for (int i = 0; i < SIZE && !remainder.isEmpty(); i++) {
            ItemStack slot = stacks.get(i);
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, remainder)) {
                int room = slot.getMaxCount() - slot.getCount();
                if (room > 0) {
                    int moved = Math.min(room, remainder.getCount());
                    slot.increment(moved);
                    remainder.decrement(moved);
                    markDirty();
                }
            }
        }
        for (int i = 0; i < SIZE && !remainder.isEmpty(); i++) {
            if (stacks.get(i).isEmpty()) {
                stacks.set(i, remainder.split(remainder.getCount()));
                markDirty();
            }
        }
        return remainder;
    }

    public net.minecraft.util.collection.DefaultedList<ItemStack> stacks() {
        return stacks;
    }

    public void markDirty() {
        dirty = true;
    }

    public void flushIfDirty() {
        if (dirty) {
            save();
            dirty = false;
        }
    }

    public static void flushAll() {
        for (BlackHoleWarehouse warehouse : LOADED.values()) {
            warehouse.flushIfDirty();
        }
    }

    private void save() {
        try {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
            JsonArray items = new JsonArray();
            for (int i = 0; i < SIZE; i++) {
                ItemStack stack = stacks.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", i);
                ItemStack.CODEC.encodeStart(ops, stack).result()
                        .ifPresent(encoded -> entry.add("item", encoded));
                items.add(entry);
            }
            JsonObject root = new JsonObject();
            root.addProperty("owner", owner.toString());
            root.add("items", items);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ex) {
            VoidMaw.LOGGER.warn("Failed to save voidmaw warehouse for {}: {}", owner, ex.toString());
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            for (JsonElement element : items) {
                JsonObject entry = element.getAsJsonObject();
                int slot = Math.clamp(entry.get("slot").getAsInt(), 0, SIZE - 1);
                ItemStack.CODEC.parse(ops, entry.get("item")).result()
                        .ifPresent(stack -> stacks.set(slot, stack));
            }
        } catch (IOException | IllegalStateException | ClassCastException ex) {
            VoidMaw.LOGGER.warn("Failed to load voidmaw warehouse for {}: {}", owner, ex.toString());
        }
    }
}
