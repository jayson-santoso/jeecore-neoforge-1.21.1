package net.sonflowers.jeecore.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.entity.DryadSporeEntity;

/**
 * Declares deferred entity registrations for gene gameplay and display effects.
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, JeeCore.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<DryadSporeEntity>> DRYAD_SPORE =
            ENTITIES.register("dryad_spore", () -> EntityType.Builder
                    .<DryadSporeEntity>of(DryadSporeEntity::new, MobCategory.MISC)
                    .sized(DryadSporeEntity.SIZE, DryadSporeEntity.SIZE).noSave().fireImmune()
                    .clientTrackingRange(2).updateInterval(2)
                    .setShouldReceiveVelocityUpdates(false)
                    .build(JeeCore.MOD_ID + ":dryad_spore"));

    private ModEntities() {}
}
