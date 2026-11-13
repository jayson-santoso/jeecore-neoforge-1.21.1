package net.sonflowers.jeecore.gene.terragolem;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.sonflowers.jeecore.JeeCore;

/**
 * Defines resource tag keys used to identify Terragolem fuel items.
 */
public final class TerragolemTags {
    public static final class Items {
        public static final TagKey<Item> TERRAGOLEM_FUEL = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_fuel"));
    }
}
