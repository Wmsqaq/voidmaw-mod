package com.novapupil.voidmaw.warehouse;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;

import java.nio.file.Files;
import java.util.UUID;

/**
 * Verifies the type-entry warehouse: merging, take one/stack, destroy-all,
 * pagination capacity, legacy save migration and the failure guarantees.
 */
public final class WarehouseRegressionTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        var root = Files.createTempDirectory("voidmaw-regression-");
        var file = root.resolve("warehouse.json");
        var id = UUID.randomUUID();
        var registries = DynamicRegistryManager.of(Registries.REGISTRIES);
        var warehouse = new BlackHoleWarehouse(registries, file, id);

        // Type entries: identical stacks merge, distinct types stay separate.
        assert warehouse.insert(new ItemStack(Items.DIAMOND, 64)).isEmpty();
        assert warehouse.insert(new ItemStack(Items.DIAMOND, 64)).isEmpty();
        assert warehouse.insert(new ItemStack(Items.DIRT)).isEmpty();
        assert warehouse.entries().size() == 2;
        assert warehouse.entries().get(0).count() == 128;
        assert warehouse.entries().get(1).count() == 1;

        // Take one, take a stack, run the type dry.
        assert warehouse.takeOne(0).getCount() == 1;
        assert warehouse.entries().get(0).count() == 127;
        assert warehouse.takeStack(0).getCount() == 64;
        assert warehouse.entries().get(0).count() == 63;
        assert warehouse.takeStack(0).getItem() == Items.DIAMOND;
        assert warehouse.entries().size() == 1 : "a drained entry must disappear";

        // Destroy the whole type.
        assert warehouse.destroyAll(0);
        assert warehouse.entries().isEmpty();
        assert !warehouse.destroyAll(0) : "destroying an empty slot changes nothing";

        // Counts far beyond 99 survive persistence.
        warehouse.insert(new ItemStack(Items.IRON_INGOT, 64));
        warehouse.insert(new ItemStack(Items.IRON_INGOT, 64));
        warehouse.insert(new ItemStack(Items.IRON_INGOT, 64));
        warehouse.flushIfDirty();
        var loaded = new BlackHoleWarehouse(registries, file, id);
        loaded.load();
        assert loaded.entries().size() == 1;
        assert loaded.entries().get(0).count() == 192 : "counts are unbounded longs, not stack caps";

        // Sorting orders entries by item id.
        var shuffled = new BlackHoleWarehouse(registries, root.resolve("sort.json"), UUID.randomUUID());
        shuffled.insert(new ItemStack(Items.DIRT));
        shuffled.insert(new ItemStack(Items.DIAMOND));
        shuffled.sortAll();
        assert shuffled.entries().get(0).template().getItem() == Items.DIAMOND;

        // Capacity: each type is one entry; the 406th distinct type overflows back.
        var big = new BlackHoleWarehouse(registries, root.resolve("big.json"), UUID.randomUUID());
        int distinct = 0;
        ItemStack overflow = ItemStack.EMPTY;
        for (var item : Registries.ITEM) {
            if (item == net.minecraft.item.Items.AIR) {
                continue;
            }
            if (overflow.isEmpty()) {
                overflow = big.insert(new ItemStack(item));
                if (overflow.isEmpty()) {
                    distinct++;
                }
            }
        }
        assert distinct == BlackHoleWarehouse.CAPACITY : "capacity must fill before overflow";
        assert !overflow.isEmpty() : "the first overflowing type returns to the caller";

        // Legacy slot-based saves migrate into merged type entries.
        var legacyFile = root.resolve("legacy.json");
        String legacy = "{\"owner\":\"legacy\",\"pages\":9,\"items\":["
                + "{\"page\":0,\"slot\":0,\"item\":{\"id\":\"minecraft:diamond\",\"count\":32}},"
                + "{\"page\":1,\"slot\":5,\"item\":{\"id\":\"minecraft:diamond\",\"count\":64}},"
                + "{\"page\":2,\"slot\":50,\"item\":{\"id\":\"minecraft:stone\",\"count\":7}}"
                + "]}";
        Files.writeString(legacyFile, legacy);
        var migrated = new BlackHoleWarehouse(registries, legacyFile, UUID.randomUUID());
        migrated.load();
        assert migrated.entries().size() == 1 : "legacy slots of one type merge";
        assert migrated.entries().get(0).count() == 96 : "decorative control slots are skipped";

        // Corrupt saves are never overwritten; failed saves stay dirty for retry.
        var corrupt = root.resolve("corrupt.json");
        Files.writeString(corrupt, "{broken");
        var damaged = new BlackHoleWarehouse(registries, corrupt, id);
        damaged.load();
        damaged.insert(new ItemStack(Items.DIRT));
        damaged.flushIfDirty();
        assert Files.readString(corrupt).equals("{broken") : "damaged saves must not be overwritten";
        var blocked = root.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        var retry = new BlackHoleWarehouse(registries, blocked.resolve("retry.json"), id);
        retry.insert(new ItemStack(Items.DIAMOND));
        retry.flushIfDirty();
        Files.delete(blocked);
        retry.flushIfDirty();
        assert Files.exists(blocked.resolve("retry.json")) : "failed saves must stay dirty for retry";

        System.out.println("Warehouse type entries, controls, migration and failure regressions passed");
    }
}
