package com.novapupil.voidmaw.warehouse;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.collection.DefaultedList;


/**
 * Paginated 9x6 chest UI for the black hole warehouse. The bottom row of every page
 * holds navigation controls (prev / page indicator / next) instead of storage; those
 * slots reject item insertion and clicks act as page turns.
 */
public class WarehouseScreenHandler extends GenericContainerScreenHandler {
    public static final int RESERVED_START = BlackHoleWarehouse.RESERVED_START;
    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;
    public static final int INFO_SLOT = 49;

    private final BlackHoleWarehouse warehouse;
    private final WarehouseInventory pages;
    private int page;

    public WarehouseScreenHandler(int syncId, PlayerInventory playerInventory, BlackHoleWarehouse warehouse) {
        this(syncId, playerInventory, warehouse, warehouse.page(0));
    }

    private WarehouseScreenHandler(int syncId, PlayerInventory playerInventory,
                                   BlackHoleWarehouse warehouse, DefaultedList<ItemStack> initialPage) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory,
                new WarehouseInventory(warehouse, initialPage), 6);
        this.warehouse = warehouse;
        this.pages = (WarehouseInventory) this.getSlot(0).inventory;
        decorate();
    }

    /** The last row of every page is GUI navigation, never storage. */
    @Override
    protected Slot addSlot(Slot slot) {
        if (slot.inventory instanceof WarehouseInventory && slot.getIndex() >= RESERVED_START) {
            return super.addSlot(new ReservedSlot(slot.inventory, slot.getIndex(), slot.x, slot.y));
        }
        return super.addSlot(slot);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (actionType == SlotActionType.PICKUP && slotIndex == PREV_SLOT) {
            switchPage(-1);
            return;
        }
        if (actionType == SlotActionType.PICKUP && slotIndex == NEXT_SLOT) {
            switchPage(1);
            return;
        }
        if (slotIndex >= RESERVED_START) {
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void switchPage(int delta) {
        int next = MathHelper.clamp(page + delta, 0, warehouse.pageCount() - 1);
        if (next == page) {
            return;
        }
        page = next;
        pages.setView(warehouse.page(page));
        decorate();
        this.sendContentUpdates();
    }

    /** Refreshes the navigation row of the current page. */
    private void decorate() {
        DefaultedList<ItemStack> view = warehouse.page(page);
        view.set(PREV_SLOT, page > 0
                ? named(Items.ARROW, Text.translatable("container.voidmaw.prev"))
                : ItemStack.EMPTY);
        view.set(INFO_SLOT, named(Items.PAPER,
                Text.translatable("container.voidmaw.page", page + 1, warehouse.pageCount())));
        view.set(NEXT_SLOT, page < warehouse.pageCount() - 1
                ? named(Items.SPECTRAL_ARROW, Text.translatable("container.voidmaw.next"))
                : ItemStack.EMPTY);
        ItemStack filler = named(Items.GRAY_STAINED_GLASS_PANE, Text.literal(" "));
        for (int i = RESERVED_START; i < BlackHoleWarehouse.SIZE; i++) {
            if (i != PREV_SLOT && i != INFO_SLOT && i != NEXT_SLOT) {
                view.set(i, filler.copy());
            }
        }
        warehouse.markDirty();
    }

    private static ItemStack named(Item item, Text name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
        return stack;
    }

    /** A navigation slot: nothing can be placed in it. */
    private static class ReservedSlot extends Slot {
        ReservedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }
    }
}
