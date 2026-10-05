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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-black-hole item storage, keyed by the hole owner's UUID.
 *
 * Capacity is paginated: {@link #PAGES} pages of {@link #SIZE} slots each, where the
 * last row of every page (slots {@link #RESERVED_START}..) is reserved for GUI page
 * controls and never stores items.
 *
 * Persistence is plain JSON files under {@code <world>/data/voidmaw/warehouses/<uuid>.json}
 * (stacks encoded with ItemStack.CODEC) - deliberately NOT NBT.
 */
public final class BlackHoleWarehouse {
    public static final int SIZE = 54;
    public static final int PAGES = 9;
    /** Slots at/above this index in a page are GUI controls, not storage. */
    public static final int RESERVED_START = 45;

    private static final Map<UUID, BlackHoleWarehouse> LOADED = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final UUID owner;
    private final MinecraftServer server;
    private final Path file;
    private final List<net.minecraft.util.collection.DefaultedList<ItemStack>> pages = new ArrayList<>();
    private boolean dirty;

    private BlackHoleWarehouse(MinecraftServer server, UUID owner) {
        this.server = server;
        this.owner = owner;
        this.file = server.getSavePath(WorldSavePath.ROOT)
                .resolve("data/voidmaw/warehouses/" + owner + ".json");
        for (int p = 0; p < PAGES; p++) {
            pages.add(net.minecraft.util.collection.DefaultedList.ofSize(SIZE, ItemStack.EMPTY));
        }
    }

    public static BlackHoleWarehouse get(MinecraftServer server, UUID owner) {
        return LOADED.computeIfAbsent(owner, id -> {
            BlackHoleWarehouse warehouse = new BlackHoleWarehouse(server, id);
            warehouse.load();
            return warehouse;
        });
    }

    public int pageCount() {
        return PAGES;
    }

    public net.minecraft.util.collection.DefaultedList<ItemStack> page(int index) {
        return pages.get(Math.clamp(index, 0, PAGES - 1));
    }

    /** Fills matching partial stacks first, then empty slots. Returns whatever did not fit. */
    public ItemStack insert(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        for (int p = 0; p < PAGES && !remainder.isEmpty(); p++) {
            net.minecraft.util.collection.DefaultedList<ItemStack> page = pages.get(p);
            for (int i = 0; i < RESERVED_START && !remainder.isEmpty(); i++) {
                ItemStack slot = page.get(i);
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
            for (int i = 0; i < RESERVED_START && !remainder.isEmpty(); i++) {
                if (page.get(i).isEmpty()) {
                    page.set(i, remainder.split(remainder.getCount()));
                    markDirty();
                }
            }
        }
        return remainder;
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
            for (int p = 0; p < PAGES; p++) {
                net.minecraft.util.collection.DefaultedList<ItemStack> page = pages.get(p);
                for (int i = 0; i < SIZE; i++) {
                    ItemStack stack = page.get(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("page", p);
                    entry.addProperty("slot", i);
                    ItemStack.CODEC.encodeStart(ops, stack).result()
                            .ifPresent(encoded -> entry.add("item", encoded));
                    items.add(entry);
                }
            }
            JsonObject root = new JsonObject();
            root.addProperty("owner", owner.toString());
            root.addProperty("pages", PAGES);
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
                // Legacy saves had no "page" field; everything lands on page 0.
                int page = Math.clamp(entry.has("page") ? entry.get("page").getAsInt() : 0, 0, PAGES - 1);
                int slot = Math.clamp(entry.get("slot").getAsInt(), 0, SIZE - 1);
                ItemStack.CODEC.parse(ops, entry.get("item")).result()
                        .ifPresent(stack -> pages.get(page).set(slot, stack));
            }
        } catch (IOException | IllegalStateException | ClassCastException ex) {
            VoidMaw.LOGGER.warn("Failed to load voidmaw warehouse for {}: {}", owner, ex.toString());
        }
    }
}
