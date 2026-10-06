package com.novapupil.voidmaw.item;

import com.novapupil.voidmaw.VoidMaw;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {
    }

    public static final Item SINGULARITY_CORE = register("singularity_core",
            new SingularityCoreItem(new Item.Settings()
                    // 1.21.10 requires the registry key on the settings, otherwise "Item id not set"
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VoidMaw.MOD_ID, "singularity_core")))
                    .maxCount(1)
                    .rarity(Rarity.EPIC)
                    .fireproof()));

    /** Visual-only item rendered by the hole's ItemDisplay disc; never obtainable. */
    public static final Item HOLE_DISC = Registry.register(Registries.ITEM,
            Identifier.of(VoidMaw.MOD_ID, "hole_disc"),
            new Item(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(VoidMaw.MOD_ID, "hole_disc")))
                    .maxCount(1)));

    private static Item register(String name, Item item) {
        Item registered = Registry.register(Registries.ITEM, Identifier.of(VoidMaw.MOD_ID, name), item);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(registered));
        return registered;
    }

    public static void register() {
        // class loading performs the registrations
    }
}
