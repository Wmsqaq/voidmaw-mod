package com.novapupil.voidmaw.warehouse;

import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Opens the paginated warehouse UI for a player (command + hotkey share this). */
public final class WarehouseUi {
    private WarehouseUi() {
    }

    public static void open(ServerPlayerEntity player) {
        BlackHoleWarehouse warehouse = BlackHoleWarehouse.get(
                ((ServerWorld) player.getEntityWorld()).getServer(), player.getUuid());
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, p) -> new WarehouseScreenHandler(syncId, inventory, warehouse),
                Text.translatable("container.voidmaw.warehouse")));
    }
}
