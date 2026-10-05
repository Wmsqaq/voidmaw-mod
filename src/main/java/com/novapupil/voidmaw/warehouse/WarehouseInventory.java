package com.novapupil.voidmaw.warehouse;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * Inventory adapter that exposes one player's black hole warehouse to a chest UI.
 */
public class WarehouseInventory implements Inventory {
    private final BlackHoleWarehouse warehouse;
    private final DefaultedList<ItemStack> stacks;

    public WarehouseInventory(BlackHoleWarehouse warehouse) {
        this.warehouse = warehouse;
        this.stacks = warehouse.stacks();
    }

    @Override
    public int size() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return stacks.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
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
        stacks.clear();
        markDirty();
    }
}
