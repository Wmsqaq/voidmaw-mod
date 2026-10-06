package com.novapupil.voidmaw.mixin;

import com.novapupil.voidmaw.item.CoreLock;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void voidmaw$lockClick(int slot, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (CoreLock.blockedClick(handler, slot, button, action)) {
            // syncState needs a server-side sync handler; client-side prediction is already
            // cancelled by returning early, which leaves the local handler state untouched.
            if (!player.getEntityWorld().isClient()) {
                handler.syncState();
            }
            ci.cancel();
        }
    }
}
