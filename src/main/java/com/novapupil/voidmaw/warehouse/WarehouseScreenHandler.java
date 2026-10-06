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


/**
 * Paginated 9x6 chest UI for the black hole warehouse. Storage slots represent ITEM
 * TYPES with unbounded counts: left click takes one, right click takes a stack,
 * shift-click takes a stack into the inventory, and ctrl+left / ctrl+right (client
 * mod only, via {@link WarehouseActionPayload}) take a stack / destroy the whole
 * type. Clicking a type with items on the cursor deposits them.
 */
public class WarehouseScreenHandler extends GenericContainerScreenHandler {
    public static final int RESERVED_START = BlackHoleWarehouse.SLOTS_PER_PAGE;
    public static final int PREV_SLOT = 45;
    public static final int SORT_SLOT = 47;
    public static final int NEXT_SLOT = 53;
    public static final int INFO_SLOT = 49;

    private final BlackHoleWarehouse warehouse;
    private final WarehouseInventory view;
    private int page;

    public WarehouseScreenHandler(int syncId, PlayerInventory playerInventory, BlackHoleWarehouse warehouse) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, new WarehouseInventory(warehouse), 6);
        this.warehouse = warehouse;
        this.view = (WarehouseInventory) this.getSlot(0).inventory;
        refresh();
    }

    /** The last row of every page is GUI navigation, never storage. */
    @Override
    protected Slot addSlot(Slot slot) {
        if (slot.inventory instanceof WarehouseInventory && slot.getIndex() >= RESERVED_START) {
            return super.addSlot(new ReservedSlot(slot.inventory, slot.getIndex(), slot.x, slot.y));
        }
        return super.addSlot(slot);
    }

    /** Payload entry point for ctrl-modified actions from the modded client. */
    public void handleAction(int slotIndex, int action) {
        if (slotIndex < 0 || slotIndex >= RESERVED_START) {
            return;
        }
        int global = page * RESERVED_START + slotIndex;
        if (action == WarehouseActionPayload.TAKE_STACK) {
            if (getCursorStack().isEmpty()) {
                setCursorStack(warehouse.takeStack(global));
            }
        } else if (action == WarehouseActionPayload.DESTROY_ALL) {
            warehouse.destroyAll(global);
        } else {
            return;
        }
        refresh();
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
        if (actionType == SlotActionType.PICKUP && slotIndex == SORT_SLOT) {
            warehouse.sortAll();
            refresh();
            return;
        }
        if (slotIndex >= RESERVED_START && slotIndex < BlackHoleWarehouse.SIZE) {
            return; // filler panes
        }
        if (slotIndex >= 0 && slotIndex < RESERVED_START) {
            handleStorageClick(slotIndex, button, actionType, player);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void handleStorageClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        int global = page * RESERVED_START + slotIndex;
        switch (actionType) {
            case PICKUP -> {
                ItemStack cursor = getCursorStack();
                if (cursor.isEmpty()) {
                    // Left = one, right = a stack (the right-click path also lets vanilla
                    // clients without the mod take full stacks).
                    setCursorStack(button == 0
                            ? warehouse.takeOne(global)
                            : warehouse.takeStack(global));
                } else {
                    setCursorStack(warehouse.insert(cursor.copy()));
                }
            }
            case QUICK_MOVE -> {
                // Shift-click: move a full stack into the player inventory.
                ItemStack taken = warehouse.takeStack(global);
                if (!taken.isEmpty()) {
                    player.getInventory().insertStack(taken);
                    if (!taken.isEmpty()) {
                        warehouse.insert(taken);
                    }
                }
            }
            default -> {
                // THROW / SWAP / CLONE / QUICK_CRAFT / PICKUP_ALL make no sense on
                // type entries; drop them on the floor.
            }
        }
        refresh();
    }

    /** Shift-clicking items in the PLAYER inventory deposits them into the warehouse. */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!(slot.inventory instanceof PlayerInventory)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack leftover = warehouse.insert(stack.copy());
        slot.setStack(leftover);
        refresh();
        return ItemStack.EMPTY;
    }

    private void switchPage(int delta) {
        int next = MathHelper.clamp(page + delta, 0, warehouse.pageCount() - 1);
        if (next == page) {
            return;
        }
        page = next;
        refresh();
    }

    /** Rebuilds the display stacks for the current page and resyncs the client. */
    private void refresh() {
        view.showPage(page, warehouse.entries());
        decorate();
        this.sendContentUpdates();
    }

    /** Refreshes the navigation row of the current page. */
    private void decorate() {
        Inventory inventory = view;
        inventory.setStack(PREV_SLOT, page > 0
                ? named(Items.ARROW, Text.translatable("container.voidmaw.prev"))
                : ItemStack.EMPTY);
        inventory.setStack(INFO_SLOT, named(Items.PAPER,
                Text.translatable("container.voidmaw.page", page + 1, warehouse.pageCount())));
        inventory.setStack(NEXT_SLOT, page < warehouse.pageCount() - 1
                ? named(Items.SPECTRAL_ARROW, Text.translatable("container.voidmaw.next"))
                : ItemStack.EMPTY);
        inventory.setStack(SORT_SLOT, named(Items.CHEST, Text.translatable("container.voidmaw.sort")));
        ItemStack filler = named(Items.GRAY_STAINED_GLASS_PANE, Text.literal(" "));
        for (int i = RESERVED_START; i < BlackHoleWarehouse.SIZE; i++) {
            if (i != PREV_SLOT && i != INFO_SLOT && i != NEXT_SLOT && i != SORT_SLOT) {
                inventory.setStack(i, filler.copy());
            }
        }
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
        public boolean canTakeItems(PlayerEntity player) {
            return false;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }
    }
}
