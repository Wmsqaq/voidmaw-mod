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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-black-hole item storage, keyed by the hole owner's UUID.
 *
 * Storage records ITEM TYPES, not stacks: every distinct item (item + components)
 * occupies one entry with an unbounded count, so the nine 45-slot pages hold up to
 * {@link #CAPACITY} different types instead of a few hundred stacks.
 *
 * Persistence is plain JSON files under {@code <world>/data/voidmaw/warehouses/<uuid>.json}
 * (items encoded with ItemStack.CODEC) - deliberately NOT NBT. Legacy slot-based saves
 * are migrated on load.
 */
public final class BlackHoleWarehouse {
    public static final int SLOTS_PER_PAGE = 45;
    public static final int PAGES = 9;
    /** GUI page size including the 9-slot navigation row. */
    public static final int SIZE = SLOTS_PER_PAGE + 9;
    public static final int CAPACITY = PAGES * SLOTS_PER_PAGE;

    private static final Map<MinecraftServer, Map<UUID, BlackHoleWarehouse>> LOADED = new java.util.IdentityHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One warehouse row: the item identity plus how many of it the maw swallowed. */
    public record Entry(ItemStack template, long count) {}

    private final UUID owner;
    private final RegistryWrapper.WrapperLookup registries;
    private final Path file;
    /** Insertion-ordered entries; index == global display slot (page * 45 + slot). */
    private final List<Entry> entries = new ArrayList<>();
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

    /** Read-only view in display order; index = page * {@link #SLOTS_PER_PAGE} + slot. */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Adds a whole stack, merging into its type entry. Returns whatever did not fit. */
    public ItemStack insert(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (loadFailed) {
            return stack.copy();
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (ItemStack.areItemsAndComponentsEqual(entry.template(), stack)) {
                entries.set(i, new Entry(entry.template(), entry.count() + stack.getCount()));
                markDirty();
                return ItemStack.EMPTY;
            }
        }
        if (entries.size() >= CAPACITY) {
            return stack.copy();
        }
        entries.add(new Entry(stack.copyWithCount(1), stack.getCount()));
        markDirty();
        return ItemStack.EMPTY;
    }

    /** Takes one item of the entry; removes the entry when it runs dry. */
    public ItemStack takeOne(int globalIndex) {
        return take(globalIndex, 1);
    }

    /** Takes up to one full stack of the entry; removes the entry when it runs dry. */
    public ItemStack takeStack(int globalIndex) {
        Entry entry = entryAt(globalIndex);
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        return take(globalIndex, (int) Math.min(entry.count(), entry.template().getMaxCount()));
    }

    /** Wipes every item of the entry's type. True when something was destroyed. */
    public boolean destroyAll(int globalIndex) {
        if (entryAt(globalIndex) == null) {
            return false;
        }
        entries.remove(globalIndex);
        markDirty();
        return true;
    }

    private ItemStack take(int globalIndex, int amount) {
        Entry entry = entryAt(globalIndex);
        if (entry == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = (int) Math.min(amount, entry.count());
        long remaining = entry.count() - taken;
        if (remaining <= 0) {
            entries.remove(globalIndex);
        } else {
            entries.set(globalIndex, new Entry(entry.template(), remaining));
        }
        markDirty();
        return entry.template().copyWithCount(taken);
    }

    private Entry entryAt(int globalIndex) {
        return globalIndex >= 0 && globalIndex < entries.size() ? entries.get(globalIndex) : null;
    }

    /** Orders the type entries by item id so page slices stay predictable. */
    public void sortAll() {
        entries.sort(Comparator.comparing(entry ->
                Registries.ITEM.getId(entry.template().getItem()).toString()));
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
            JsonArray saved = new JsonArray();
            for (Entry entry : entries) {
                JsonObject json = new JsonObject();
                json.add("item", ItemStack.CODEC.encodeStart(ops, entry.template()).getOrThrow());
                json.addProperty("count", entry.count());
                saved.add(json);
            }
            JsonObject root = new JsonObject();
            root.addProperty("owner", owner.toString());
            root.addProperty("version", 2);
            root.add("entries", saved);
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
            List<Entry> loaded = new ArrayList<>();
            if (root.has("entries")) {
                for (JsonElement element : root.getAsJsonArray("entries")) {
                    JsonObject json = element.getAsJsonObject();
                    ItemStack template = ItemStack.CODEC.parse(ops, json.get("item")).getOrThrow();
                    merge(loaded, template, json.get("count").getAsLong());
                }
            } else {
                // Legacy slot-based saves: every stored stack becomes a type entry.
                for (JsonElement element : root.getAsJsonArray("items")) {
                    JsonObject json = element.getAsJsonObject();
                    int slot = json.get("slot").getAsInt();
                    if (slot >= SLOTS_PER_PAGE) {
                        continue; // older versions wrote decorative controls into saves
                    }
                    ItemStack stack = ItemStack.CODEC.parse(ops, json.get("item")).getOrThrow();
                    merge(loaded, stack, stack.getCount());
                }
            }
            if (loaded.size() > CAPACITY) {
                throw new IllegalArgumentException("Warehouse holds more types than capacity");
            }
            entries.clear();
            entries.addAll(loaded);
            loadFailed = false;
        } catch (IOException | RuntimeException ex) {
            loadFailed = true;
            VoidMaw.LOGGER.warn("Failed to load voidmaw warehouse for {}; original file will not be overwritten: {}",
                    owner, ex.toString());
        }
    }

    private static void merge(List<Entry> target, ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) {
            return;
        }
        for (int i = 0; i < target.size(); i++) {
            Entry entry = target.get(i);
            if (ItemStack.areItemsAndComponentsEqual(entry.template(), stack)) {
                target.set(i, new Entry(entry.template(), entry.count() + count));
                return;
            }
        }
        target.add(new Entry(stack.copyWithCount(1), count));
    }
}
