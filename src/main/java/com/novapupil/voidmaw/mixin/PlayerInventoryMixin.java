package com.novapupil.voidmaw.mixin;

import com.novapupil.voidmaw.item.CoreLock;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Redirect(method = "dropAll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;"))
    private net.minecraft.entity.ItemEntity voidmaw$skipCoreDrop(net.minecraft.entity.player.PlayerEntity player,
                                                                  ItemStack stack, boolean retain, boolean delayed) {
        return CoreLock.isCore(stack) ? null : player.dropItem(stack, retain, delayed);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void voidmaw$protectSelected(boolean entireStack, CallbackInfoReturnable<ItemStack> cir) {
        PlayerInventory inventory = (PlayerInventory) (Object) this;
        if (CoreLock.isCore(inventory.getSelectedStack())) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
