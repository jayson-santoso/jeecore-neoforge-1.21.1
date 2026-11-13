package net.sonflowers.jeecore.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.effect.SporeGuardEffect;
import net.sonflowers.jeecore.gene.terragolem.effect.ConcussionEffect;
import net.sonflowers.jeecore.gene.terragolem.effect.OverheatedEffect;

/**
 * Declares deferred gene status effects and their registry identifiers.
 */
public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, JeeCore.MOD_ID);
    public static final DeferredHolder<MobEffect, SporeGuardEffect> SPORE_GUARD =
            EFFECTS.register("spore_guard", SporeGuardEffect::new);
    public static final DeferredHolder<MobEffect, ConcussionEffect> CONCUSSION =
            EFFECTS.register("concussion", ConcussionEffect::new);
    public static final DeferredHolder<MobEffect, OverheatedEffect> OVERHEATED =
            EFFECTS.register("overheated", OverheatedEffect::new);
    public static final DeferredHolder<MobEffect, net.sonflowers.jeecore.gene.nimbus.effect.NimbleEffect> NIMBLE =
            EFFECTS.register("nimble", net.sonflowers.jeecore.gene.nimbus.effect.NimbleEffect::new);

    private ModMobEffects() {}
}
