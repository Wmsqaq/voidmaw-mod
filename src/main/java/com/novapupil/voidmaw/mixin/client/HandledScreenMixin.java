package com.novapupil.voidmaw.mixin.client;

import com.novapupil.voidmaw.warehouse.WarehouseActionPayload;
import com.novapupil.voidmaw.warehouse.WarehouseInventory;
import com.novapupil.voidmaw.warehouse.WarehouseScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla click packets carry no ctrl state, so ctrl-modified warehouse actions are
 * sent as a dedicated payload instead: ctrl+left takes a full stack, ctrl+right
 * destroys the whole item type. Unmodified clicks keep vanilla semantics.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow @Final protected ScreenHandler handler;
    @Shadow protected Slot focusedSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void voidmaw$warehouseCtrlClick(net.minecraft.client.gui.Click click, boolean doubled,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!(this.handler instanceof WarehouseScreenHandler warehouseHandler)
                || this.focusedSlot == null
                || !(this.focusedSlot.inventory instanceof WarehouseInventory)
                || this.focusedSlot.getIndex() >= WarehouseScreenHandler.RESERVED_START) {
            return;
        }
        boolean ctrl = (click.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (!ctrl || !this.handler.getCursorStack().isEmpty()) {
            return; // plain clicks and deposit clicks keep vanilla semantics
        }
        int button = click.button();
        if (button == 0) {
            ClientPlayNetworking.send(new WarehouseActionPayload(
                    this.focusedSlot.getIndex(), WarehouseActionPayload.TAKE_STACK));
            cir.setReturnValue(true);
        } else if (button == 1) {
            ClientPlayNetworking.send(new WarehouseActionPayload(
                    this.focusedSlot.getIndex(), WarehouseActionPayload.DESTROY_ALL));
            cir.setReturnValue(true);
        }
    }
}
