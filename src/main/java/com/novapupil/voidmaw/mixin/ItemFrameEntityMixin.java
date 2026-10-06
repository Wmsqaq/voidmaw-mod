package com.novapupil.voidmaw.mixin;

import com.novapupil.voidmaw.item.CoreLock;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameEntityMixin {
    @Inject(method = "setHeldItemStack(Lnet/minecraft/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void voidmaw$refuseCore(ItemStack stack, boolean update, CallbackInfo ci) {
        if (CoreLock.isCore(stack)) {
            ci.cancel();
        }
    }
}
