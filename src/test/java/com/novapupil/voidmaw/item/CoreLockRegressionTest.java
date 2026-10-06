package com.novapupil.voidmaw.item;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.ShelfBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayNetworkHandler;

/**
 * Verifies the Singularity Core lock logic and force-loads every mixin target so a
 * wrong injection point fails here instead of crashing real servers mid-game.
 */
public final class CoreLockRegressionTest {
    /** Minimal handler exposing one slot over a SimpleInventory for click-rule tests. */
    private static final class TestHandler extends ScreenHandler {
        final SimpleInventory inventory = new SimpleInventory(9);

        TestHandler() {
            super(null, 1);
            addSlot(new Slot(inventory, 8, 0, 0));
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }
    }

    public static void main(String[] args) {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();

        // Mixin application happens at first class load; a bad injection target throws here.
        Class<?>[] mixinTargets = {
                ScreenHandler.class,
                PlayerInventory.class,
                ServerPlayNetworkHandler.class,
                LivingEntity.class,
                ShelfBlockEntity.class,
                ItemFrameEntity.class,
                DecoratedPotBlockEntity.class,
        };
        for (Class<?> target : mixinTargets) {
            assert target.getDeclaredMethods().length >= 0 : target.getName();
        }

        ItemStack core = new ItemStack(ModItems.SINGULARITY_CORE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        assert CoreLock.isCore(core) : "core item must be registered";
        assert !CoreLock.isCore(dirt) && !CoreLock.isCore(ItemStack.EMPTY);

        TestHandler handler = new TestHandler();
        // Slot 8 holds the core: every click on it must be refused.
        handler.inventory.setStack(8, core.copy());
        assert CoreLock.protectedSlot(handler.getSlot(0)) : "core slot must be protected";
        assert CoreLock.blockedClick(handler, 0, 0, SlotActionType.PICKUP);
        assert CoreLock.blockedClick(handler, 0, 0, SlotActionType.QUICK_MOVE) : "shift-click must be refused";
        assert CoreLock.blockedClick(handler, 0, 0, SlotActionType.THROW) : "Q must be refused";
        assert CoreLock.blockedClick(handler, 0, 0, SlotActionType.SWAP) : "number-key swap must be refused";

        // Slot 8 holds dirt again: normal clicks pass, but the slot-8 hotbar key stays locked.
        handler.inventory.setStack(8, dirt.copy());
        assert !CoreLock.protectedSlot(handler.getSlot(0));
        assert !CoreLock.blockedClick(handler, 0, 0, SlotActionType.PICKUP);
        assert CoreLock.blockedClick(handler, 0, 8, SlotActionType.SWAP) : "key 9 must not yank the core out";
        assert !CoreLock.blockedClick(handler, 0, 0, SlotActionType.SWAP) : "other hotbar keys stay usable";

        // A core on the cursor blocks every action until it is gone.
        handler.setCursorStack(core.copy());
        assert CoreLock.blockedClick(handler, 0, 0, SlotActionType.PICKUP);
        handler.setCursorStack(ItemStack.EMPTY);

        // Creative packets use PlayerScreenHandler ids: hotbar 0-8 map to 36-44.
        assert CoreLock.blockedCreativeAction((short) 44, dirt.copy()) : "creative writes to slot 8 must be refused";
        assert CoreLock.blockedCreativeAction((short) 44, core.copy());
        assert CoreLock.blockedCreativeAction((short) 43, core.copy()) : "cores must not travel via creative packets";
        assert !CoreLock.blockedCreativeAction((short) 43, dirt.copy()) : "normal creative inventory must stay usable";
        assert !CoreLock.blockedCreativeAction((short) -1, dirt.copy()) : "creative Q of other items stays usable";
        assert CoreLock.blockedCreativeAction((short) -1, core.copy()) : "creative drop of the core must be refused";

        System.out.println("Core lock click rules and mixin target regressions passed");
    }
}
