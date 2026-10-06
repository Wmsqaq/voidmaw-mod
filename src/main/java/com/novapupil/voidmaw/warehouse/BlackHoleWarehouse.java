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
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
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

    private static final Map<MinecraftServer, Map<UUID, BlackHoleWarehouse>> LOADED = new java.util.IdentityHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final UUID owner;
    private final RegistryWrapper.WrapperLookup registries;
    private final Path file;
    private final List<net.minecraft.util.collection.DefaultedList<ItemStack>> pages = new ArrayList<>();
    private boolean dirty;
    private boolean loadFailed;

    private BlackHoleWarehouse(MinecraftServer server, UUID owner) {
        this(server.getRegistryManager(), server.getSavePath(WorldSavePath.ROOT)
                .resolve("data/voidmaw/warehouses/" + owner + ".json"), owner);
    }

    BlackHoleWarehouse(RegistryWrapper.WrapperLookup registries, Path file, UUID owner) {
        this.registries = registries;
        this.owner = owner;
        this.file = file;
        for (int p = 0; p < PAGES; p++) {
            pages.add(net.minecraft.util.collection.DefaultedList.ofSize(SIZE, ItemStack.EMPTY));
        }
    }

    public static BlackHoleWarehouse get(MinecraftServer server, UUID owner) {
        return LOADED.computeIfAbsent(server, ignored -> new HashMap<>()).computeIfAbsent(owner, id -> {
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
        if (loadFailed) {
            return stack.copy();
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
                    page.set(i, remainder.split(Math.min(remainder.getCount(), remainder.getMaxCount())));
                    markDirty();
                }
            }
        }
        return remainder;
    }

    /** Consolidates and sorts every page: merge stacks, order by item id, compact. */
    public void sortAll() {
        List<ItemStack> all = new ArrayList<>();
        for (net.minecraft.util.collection.DefaultedList<ItemStack> page : pages) {
            for (int i = 0; i < RESERVED_START; i++) {
                if (!page.get(i).isEmpty()) {
                    all.add(page.get(i));
                }
                page.set(i, ItemStack.EMPTY);
            }
        }

        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : all) {
            for (ItemStack target : merged) {
                if (stack.isEmpty()) {
                    break;
                }
                if (ItemStack.areItemsAndComponentsEqual(target, stack)) {
                    int room = target.getMaxCount() - target.getCount();
                    int moved = Math.min(room, stack.getCount());
                    target.increment(moved);
                    stack.decrement(moved);
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
            }
        }
        merged.sort((a, b) -> {
            int c = Registries.ITEM.getId(a.getItem()).toString()
                    .compareTo(Registries.ITEM.getId(b.getItem()).toString());
            return c != 0 ? c : Integer.compare(b.getCount(), a.getCount());
        });

        int page = 0;
        int slot = 0;
        for (ItemStack stack : merged) {
            if (page >= PAGES) {
                break;
            }
            pages.get(page).set(slot, stack);
            if (++slot >= RESERVED_START) {
                slot = 0;
                page++;
            }
        }
        markDirty();
    }

    public void markDirty() {
        dirty = true;
    }

    public void flushIfDirty() {
        if (dirty && !loadFailed && save()) {
            dirty = false;
        }
    }

    public static void flushAll() {
        for (Map<UUID, BlackHoleWarehouse> warehouses : LOADED.values()) {
            for (BlackHoleWarehouse warehouse : warehouses.values()) {
                warehouse.flushIfDirty();
            }
        }
    }

    public static void flushAll(MinecraftServer server) {
        Map<UUID, BlackHoleWarehouse> warehouses = LOADED.get(server);
        if (warehouses != null) {
            warehouses.values().forEach(BlackHoleWarehouse::flushIfDirty);
        }
    }

    /** Release every world-bound reference when an integrated or dedicated server stops. */
    public static void unload(MinecraftServer server) {
        flushAll(server);
        LOADED.remove(server);
    }

    private boolean save() {
        Path temporary = null;
        try {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, registries);
            JsonArray items = new JsonArray();
            for (int p = 0; p < PAGES; p++) {
                net.minecraft.util.collection.DefaultedList<ItemStack> page = pages.get(p);
                for (int i = 0; i < RESERVED_START; i++) {
                    ItemStack stack = page.get(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("page", p);
                    entry.addProperty("slot", i);
                    entry.add("item", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
                    items.add(entry);
                }
            }
            JsonObject root = new JsonObject();
            root.addProperty("owner", owner.toString());
            root.addProperty("pages", PAGES);
            root.add("items", items);
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), owner + "-", ".tmp");
            Files.writeString(temporary, GSON.toJson(root));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            VoidMaw.LOGGER.warn("Failed to save voidmaw warehouse for {}: {}", owner, ex.toString());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ex) {
                    VoidMaw.LOGGER.warn("Failed to remove temporary warehouse file {}: {}", temporary, ex.toString());
                }
            }
        }
    }

    void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, registries);
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            List<net.minecraft.util.collection.DefaultedList<ItemStack>> loaded = new ArrayList<>();
            for (int p = 0; p < PAGES; p++) {
                loaded.add(net.minecraft.util.collection.DefaultedList.ofSize(SIZE, ItemStack.EMPTY));
            }
            for (JsonElement element : items) {
                JsonObject entry = element.getAsJsonObject();
                // Legacy saves had no "page" field; everything lands on page 0.
                int page = entry.has("page") ? entry.get("page").getAsInt() : 0;
                int slot = entry.get("slot").getAsInt();
                if (page < 0 || page >= PAGES || slot < 0 || slot >= SIZE) {
                    throw new IllegalArgumentException("Invalid warehouse position: " + page + "/" + slot);
                }
                // Older versions wrote decorative controls into their warehouse files.
                if (slot >= RESERVED_START) {
                    continue;
                }
                if (!loaded.get(page).get(slot).isEmpty()) {
                    throw new IllegalArgumentException("Duplicate warehouse position: " + page + "/" + slot);
                }
                loaded.get(page).set(slot, ItemStack.CODEC.parse(ops, entry.get("item")).getOrThrow());
            }
            for (int p = 0; p < PAGES; p++) {
                for (int i = 0; i < RESERVED_START; i++) {
                    pages.get(p).set(i, loaded.get(p).get(i));
                }
            }
            loadFailed = false;
        } catch (IOException | RuntimeException ex) {
            loadFailed = true;
            VoidMaw.LOGGER.warn("Failed to load voidmaw warehouse for {}; original file will not be overwritten: {}",
                    owner, ex.toString());
        }
    }
}
