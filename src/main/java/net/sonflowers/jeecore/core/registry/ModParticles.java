package net.sonflowers.jeecore.core.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sonflowers.jeecore.JeeCore;

/**
 * Declares particle types; client providers supply their visual implementations.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, JeeCore.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REFUEL_SPARK =
            PARTICLES.register("refuel_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REFINED_REFUEL_SPARK =
            PARTICLES.register("refined_refuel_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> POWERED_REFUEL_SPARK =
            PARTICLES.register("powered_refuel_spark", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
