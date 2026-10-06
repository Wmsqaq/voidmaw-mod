package com.novapupil.voidmaw.mixin;

import com.novapupil.voidmaw.item.CoreLock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    // Armor stands, allays and item-picking mobs equip held items without a screen handler.
    // Players are exempt: their hand stack lives in PlayerInventory and CoreLock.ensure owns it.
    @Inject(method = "equipStack", at = @At("HEAD"), cancellable = true)
    private void voidmaw$refuseCore(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (CoreLock.isCore(stack) && !((Object) this instanceof PlayerEntity)) {
            ci.cancel();
        }
    }
}
