package com.novapupil.voidmaw.command;

import com.mojang.brigadier.CommandDispatcher;
import com.novapupil.voidmaw.blackhole.BlackHoleManager;
import com.novapupil.voidmaw.warehouse.WarehouseUi;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VoidMawCommand {
    private VoidMawCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("voidmaw")
                .then(CommandManager.literal("start").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BlackHoleManager.start(player);
                    return 1;
                }))
                .then(CommandManager.literal("stop").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    BlackHoleManager.stop(player, false);
                    return 1;
                }))
                .then(CommandManager.literal("detonate").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
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
        WarehouseUi.open(player);
    }
}
