package com.novapupil.voidmaw.item;

import com.novapupil.voidmaw.blackhole.BlackHoleManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Right-click opens the maw; sneak + right-click snaps it shut (releasing the mass).
 */
public class SingularityCoreItem extends Item {

    public SingularityCoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (user instanceof ServerPlayerEntity player) {
            if (player.isSneaking()) {
                if (BlackHoleManager.isActive(player)) {
                    BlackHoleManager.stop(player, true);
                } else {
                    BlackHoleManager.sendStatus(player);
                }
            } else if (BlackHoleManager.isActive(player)) {
                player.sendMessage(BlackHoleManager.ALREADY_OPEN, false);
            } else {
                BlackHoleManager.start(player);
            }
        }
        user.getItemCooldownManager().set(stack, 10);
        return ActionResult.SUCCESS;
    }
}
