package com.novapupil.voidmaw.mixin;

import com.novapupil.voidmaw.item.CoreLock;
import net.minecraft.block.entity.ShelfBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShelfBlockEntity.class)
public abstract class ShelfBlockEntityMixin {
    // Shelves can swap an item straight out of any hotbar/inventory slot (including slot 8)
    // without going through a screen handler, so the shelf itself must refuse the core.
    @Inject(method = "swapStackNoMarkDirty", at = @At("HEAD"), cancellable = true)
    private void voidmaw$refuseCore(int slot, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (CoreLock.isCore(stack)) {
            cir.setReturnValue(stack);
        }
    }
}
