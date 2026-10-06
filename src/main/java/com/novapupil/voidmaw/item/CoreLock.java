package com.novapupil.voidmaw.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CoreLock {
    public static final int SLOT = 8;
    private static final ThreadLocal<Boolean> MUTATING = ThreadLocal.withInitial(() -> false);

    private CoreLock() {
    }

    public static boolean isCore(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(ModItems.SINGULARITY_CORE);
    }

    public static boolean bypass() {
        return MUTATING.get();
    }

    public static boolean protectedSlot(Slot slot) {
        return slot != null && ((slot.inventory instanceof PlayerInventory && slot.getIndex() == SLOT)
                || isCore(slot.getStack()));
    }

    public static boolean blockedClick(ScreenHandler handler, int slotId, int button, SlotActionType action) {
        Slot slot = slotId >= 0 && slotId < handler.slots.size() ? handler.getSlot(slotId) : null;
        // protectedSlot covers clicks on slot 8 / any core stack; the cursor check covers
        // placing or dragging a core that somehow got picked up; the SWAP check covers the
        // number key for hotbar slot 8 pressed over any other slot.
        return protectedSlot(slot)
                || isCore(handler.getCursorStack())
                || (action == SlotActionType.SWAP && button == SLOT);
    }

    public static boolean blockedCreativeAction(short slot, ItemStack stack) {
        // Creative packets use PlayerScreenHandler slot ids: hotbar 0-8 map to 36-44.
        return isCore(stack) || slot == 36 + SLOT;
    }

    public static void ensure(ServerPlayerEntity player) {
        if (!player.isAlive()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ScreenHandler screenHandler = player.currentScreenHandler;
        ItemStack retained = inventory.getStack(SLOT);
        ItemStack core = isCore(retained) ? retained : ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack candidate = inventory.getStack(slot);
            if (isCore(candidate) && core.isEmpty()) {
                core = candidate.copyWithCount(1);
            }
        }
        ItemStack cursor = screenHandler.getCursorStack();
        if (core.isEmpty() && isCore(cursor)) {
            core = cursor.copyWithCount(1);
        }
        if (core.isEmpty()) {
            core = new ItemStack(ModItems.SINGULARITY_CORE);
        }
        boolean changed = !isCore(retained) || retained.getCount() != 1;
        MUTATING.set(true);
        try {
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (slot != SLOT && isCore(inventory.getStack(slot))) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                    changed = true;
                }
            }
            if (isCore(cursor)) {
                screenHandler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            }
            core.setCount(1);
            inventory.setStack(SLOT, core);
            if (!retained.isEmpty() && !isCore(retained)) {
                ItemStack displaced = retained.copy();
                inventory.insertStack(displaced);
                if (!displaced.isEmpty()) {
                    player.dropItem(displaced, false);
                }
            }
        } finally {
            MUTATING.set(false);
        }
        if (changed) {
            inventory.markDirty();
            screenHandler.syncState();
        }
    }
}
