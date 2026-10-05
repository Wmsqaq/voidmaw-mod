package com.novapupil.voidmaw.command;

import com.mojang.brigadier.CommandDispatcher;
import com.novapupil.voidmaw.blackhole.BlackHoleManager;
import com.novapupil.voidmaw.warehouse.BlackHoleWarehouse;
import com.novapupil.voidmaw.warehouse.WarehouseInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class VoidMawCommand {
    private VoidMawCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("voidmaw")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("start").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BlackHoleManager.start(player);
                    return 1;
                }))
                .then(CommandManager.literal("stop").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BlackHoleManager.stop(player, false);
                    return 1;
                }))
                .then(CommandManager.literal("detonate").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BlackHoleManager.stop(player, true);
                    return 1;
                }))
                .then(CommandManager.literal("status").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BlackHoleManager.sendStatus(player);
                    return 1;
                }))
                .then(CommandManager.literal("warehouse").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    openWarehouse(player);
                    return 1;
                })));
    }

    private static void openWarehouse(ServerPlayerEntity player) {
        BlackHoleWarehouse warehouse = BlackHoleWarehouse.get(
                ((ServerWorld) player.getEntityWorld()).getServer(), player.getUuid());
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, p) -> GenericContainerScreenHandler.createGeneric9x6(
                        syncId, inventory, new WarehouseInventory(warehouse)),
                Text.translatable("container.voidmaw.warehouse")));
    }
}
