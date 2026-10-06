package com.novapupil.voidmaw.blackhole;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.world.World;

final class HoleDiscEntity extends ItemDisplayEntity {
    HoleDiscEntity(World world) {
        super(EntityType.ITEM_DISPLAY, world);
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
