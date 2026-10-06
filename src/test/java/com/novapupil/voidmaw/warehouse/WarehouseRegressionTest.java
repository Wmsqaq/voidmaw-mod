package com.novapupil.voidmaw.warehouse;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import java.nio.file.Files;
import java.util.UUID;

public final class WarehouseRegressionTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        var root = Files.createTempDirectory("voidmaw-regression-");
        var file = root.resolve("warehouse.json");
        var id = UUID.randomUUID();
        var registries = DynamicRegistryManager.of(net.minecraft.registry.Registries.REGISTRIES);
        var warehouse = new BlackHoleWarehouse(registries, file, id);
        assert warehouse.insert(new ItemStack(Items.DIAMOND, 64)).isEmpty();
        var first = new WarehouseInventory(warehouse, warehouse.page(0));
        var second = new WarehouseInventory(warehouse, warehouse.page(0));
        first.setStack(49, new ItemStack(Items.PAPER));
        assert second.getStack(49).isEmpty() : "controls must be per viewer";
        assert warehouse.page(0).get(49).isEmpty();
        assert first.removeStack(49).isEmpty() : "controls cannot be extracted";
        warehouse.page(1).set(0, new ItemStack(Items.DIAMOND, 32));
        warehouse.sortAll();
        assert warehouse.page(0).get(0).getCount() == 64;
        assert warehouse.page(0).get(1).getCount() == 32;
        warehouse.flushIfDirty();
        assert Files.exists(file);
        assert !Files.readString(file).contains("\"slot\": 49");
        var loaded = new BlackHoleWarehouse(registries, file, id);
        loaded.load();
        assert loaded.page(0).get(0).getCount() == 64;
        assert loaded.page(0).get(1).getCount() == 32;
        first.removeStack(0, 10);
        warehouse.flushIfDirty();
        loaded = new BlackHoleWarehouse(registries, file, id);
        loaded.load();
        assert loaded.page(0).get(0).getCount() == 54;
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
        System.out.println("Warehouse controls, sorting, persistence and failure regressions passed");
    }
}
