package com.novapupil.voidmaw.mixin;

import com.novapupil.voidmaw.item.CoreLock;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DecoratedPotBlockEntity.class)
public abstract class DecoratedPotBlockEntityMixin {
    @Inject(method = "setStack(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void voidmaw$refuseCore(ItemStack stack, CallbackInfo ci) {
        if (CoreLock.isCore(stack)) {
            ci.cancel();
        }
    }
}
