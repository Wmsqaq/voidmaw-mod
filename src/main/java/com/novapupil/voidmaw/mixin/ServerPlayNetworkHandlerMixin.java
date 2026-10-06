package com.novapupil.voidmaw.mixin;

import com.novapupil.voidmaw.item.CoreLock;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    // Packet handlers begin with NetworkThreadUtils.forceMainThread(...); injecting at HEAD
    // would run (and possibly cancel) on the Netty thread. Inject after the thread check so
    // all inventory access and resync happens on the server thread.
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;"
                    + "Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";

    @Inject(method = "onClickSlot",
            at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER),
            cancellable = true)
    private void voidmaw$lockClickPacket(ClickSlotC2SPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        if (CoreLock.blockedClick(handler.player.currentScreenHandler,
                packet.slot(), packet.button(), packet.actionType())) {
            handler.player.currentScreenHandler.syncState();
            ci.cancel();
        }
    }

    @Inject(method = "onCreativeInventoryAction",
            at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER),
            cancellable = true)
    private void voidmaw$lockCreativePacket(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        if (CoreLock.blockedCreativeAction(packet.slot(), packet.stack())) {
            handler.player.playerScreenHandler.syncState();
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction",
            at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER),
            cancellable = true)
    private void voidmaw$lockHandSwap(PlayerActionC2SPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && (CoreLock.isCore(handler.player.getMainHandStack())
                || CoreLock.isCore(handler.player.getOffHandStack()))) {
            // The client predicts the swap locally; push the authoritative stacks back.
            handler.player.playerScreenHandler.syncState();
            ci.cancel();
        }
    }
}
