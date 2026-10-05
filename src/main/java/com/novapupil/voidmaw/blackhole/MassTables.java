package com.novapupil.voidmaw.blackhole;

import com.novapupil.voidmaw.VoidMaw;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/**
 * Mass values for devoured things plus the tag of blocks the maw refuses to chew.
 */
public final class MassTables {
    public static final TagKey<Block> UNSWALLOWABLE =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of(VoidMaw.MOD_ID, "unswallowable"));

    private MassTables() {
    }

    public static double entityMass(Entity entity) {
        if (entity instanceof ItemEntity) {
            return 0.2;
        }
        if (entity instanceof ExperienceOrbEntity) {
            return 0.1;
        }
        // Rough size proxy: bosses and two-block-tall heavies feed the maw more.
        double avg = (entity.getBoundingBox().getLengthX()
                + entity.getBoundingBox().getLengthY()
                + entity.getBoundingBox().getLengthZ()) / 3.0;
        if (avg >= 2.5) {
            return 10.0;
        }
        if (avg >= 1.6) {
            return 3.0;
        }
        if (avg >= 0.9) {
            return 1.5;
        }
        return 1.0;
    }

    public static double blockMass(BlockState state, WorldView world, BlockPos pos) {
        float hardness = state.getHardness(world, pos);
        if (hardness <= 0.0f) {
            return 0.2;
        }
        return 0.3 + hardness * 0.15;
    }
}
