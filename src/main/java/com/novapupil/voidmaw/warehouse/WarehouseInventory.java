package com.novapupil.voidmaw.warehouse;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * Inventory adapter exposing one PAGE of a black hole warehouse to a chest UI.
 * The handler swaps the viewed page via {@link #setView(DefaultedList)}.
 */
public class WarehouseInventory implements Inventory {
    private final BlackHoleWarehouse warehouse;
    private DefaultedList<ItemStack> view;
    private final DefaultedList<ItemStack> controls = DefaultedList.ofSize(
            BlackHoleWarehouse.SIZE - BlackHoleWarehouse.RESERVED_START, ItemStack.EMPTY);

    public WarehouseInventory(BlackHoleWarehouse warehouse, DefaultedList<ItemStack> view) {
        this.warehouse = warehouse;
        this.view = view;
    }

    public void setView(DefaultedList<ItemStack> view) {
        this.view = view;
    }

    @Override
    public int size() {
        return view.size();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < BlackHoleWarehouse.RESERVED_START; i++) {
            if (!view.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot < BlackHoleWarehouse.RESERVED_START ? view.get(slot)
                : controls.get(slot - BlackHoleWarehouse.RESERVED_START);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot >= BlackHoleWarehouse.RESERVED_START) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = Inventories.splitStack(view, slot, amount);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot >= BlackHoleWarehouse.RESERVED_START) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = Inventories.removeStack(view, slot);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= BlackHoleWarehouse.RESERVED_START) {
            controls.set(slot - BlackHoleWarehouse.RESERVED_START, stack);
            return;
        }
        view.set(slot, stack);
        markDirty();
    }

    @Override
    public void markDirty() {
        warehouse.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void clear() {
        for (int i = 0; i < BlackHoleWarehouse.RESERVED_START; i++) {
            view.set(i, ItemStack.EMPTY);
        }
        markDirty();
    }
}
