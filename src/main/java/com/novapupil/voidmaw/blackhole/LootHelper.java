package com.novapupil.voidmaw.blackhole;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.context.ContextType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Rolls the loot tables of devoured things; results go into the black hole warehouse.
 * A diamond pickaxe is injected as TOOL so stone-family blocks keep dropping.
 */
public final class LootHelper {
    private static final ItemStack DEFAULT_TOOL = new ItemStack(Items.DIAMOND_PICKAXE);

    private LootHelper() {
    }

    public static List<ItemStack> entityLoot(ServerWorld world, LivingEntity entity) {
        return entity.getLootTableKey()
                .map(key -> generate(world, key, LootContextTypes.ENTITY, builder -> {
                    builder.add(LootContextParameters.THIS_ENTITY, entity);
                    builder.add(LootContextParameters.ORIGIN, new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
                    builder.add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().generic());
                }))
                .orElse(List.of());
    }

    public static List<ItemStack> blockLoot(ServerWorld world, BlockState state, BlockPos pos) {
        return state.getBlock().getLootTableKey()
                .map(key -> generate(world, key, LootContextTypes.BLOCK, builder -> {
                    builder.add(LootContextParameters.BLOCK_STATE, state);
                    builder.add(LootContextParameters.ORIGIN, new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    builder.add(LootContextParameters.TOOL, DEFAULT_TOOL);
                }))
                .orElse(List.of());
    }

    private static List<ItemStack> generate(ServerWorld world, RegistryKey<LootTable> key,
                                            ContextType type, Consumer<LootWorldContext.Builder> enrich) {
        LootTable table = world.getServer().getReloadableRegistries().getLootTable(key);
        List<ItemStack> out = new ArrayList<>();
        LootWorldContext.Builder builder = new LootWorldContext.Builder(world);
        enrich.accept(builder);
        table.generateLoot(builder.build(type), out::add);
        return out;
    }
}
