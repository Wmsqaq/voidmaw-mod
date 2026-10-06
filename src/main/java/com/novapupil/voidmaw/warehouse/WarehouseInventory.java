package com.novapupil.voidmaw.warehouse;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * Inventory adapter exposing one PAGE of type entries (as display stacks) plus the
 * navigation control row to a chest UI. Storage slots are display-only: the screen
 * handler intercepts every interaction and mutates {@link BlackHoleWarehouse} directly,
 * so this adapter never touches the real storage.
 */
public class WarehouseInventory implements Inventory {
    private final BlackHoleWarehouse warehouse;
    private final DefaultedList<ItemStack> display = DefaultedList.ofSize(
            BlackHoleWarehouse.SLOTS_PER_PAGE, ItemStack.EMPTY);
    private final DefaultedList<ItemStack> controls = DefaultedList.ofSize(
            BlackHoleWarehouse.SIZE - BlackHoleWarehouse.SLOTS_PER_PAGE, ItemStack.EMPTY);

    public WarehouseInventory(BlackHoleWarehouse warehouse) {
        this.warehouse = warehouse;
    }

    /** Rebuilds the display stacks of one page from the warehouse entries. */
    public void showPage(int page, java.util.List<BlackHoleWarehouse.Entry> entries) {
        for (int slot = 0; slot < BlackHoleWarehouse.SLOTS_PER_PAGE; slot++) {
            int global = page * BlackHoleWarehouse.SLOTS_PER_PAGE + slot;
            display.set(slot, global < entries.size()
                    ? displayStack(entries.get(global))
                    : ItemStack.EMPTY);
        }
    }

    /** Item icon with a truthful count in lore; the visual count is capped by max stack. */
    private static ItemStack displayStack(BlackHoleWarehouse.Entry entry) {
        ItemStack stack = entry.template().copyWithCount(
                (int) Math.min(entry.count(), entry.template().getMaxCount()));
        stack.set(net.minecraft.component.DataComponentTypes.LORE,
                new net.minecraft.component.type.LoreComponent(java.util.List.of(
                        net.minecraft.text.Text.translatable("container.voidmaw.count",
                                java.text.NumberFormat.getIntegerInstance().format(entry.count())))));
        return stack;
    }

    @Override
    public int size() {
        return BlackHoleWarehouse.SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < BlackHoleWarehouse.SIZE; i++) {
            if (!getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot < BlackHoleWarehouse.SLOTS_PER_PAGE
                ? display.get(slot)
                : controls.get(slot - BlackHoleWarehouse.SLOTS_PER_PAGE);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        // Display-only: real mutations go through the screen handler.
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= BlackHoleWarehouse.SLOTS_PER_PAGE) {
            controls.set(slot - BlackHoleWarehouse.SLOTS_PER_PAGE, stack);
        }
        // Storage slots are display-only and ignore writes.
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
        for (int i = 0; i < BlackHoleWarehouse.SIZE; i++) {
            setStack(i, ItemStack.EMPTY);
        }
    }
}
