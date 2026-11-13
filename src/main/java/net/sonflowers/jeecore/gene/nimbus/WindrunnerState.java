package net.sonflowers.jeecore.gene.nimbus;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Stores transient entity attachment state; it is neither saved nor copied across death or logout.
 */
public final class WindrunnerState {
    public final WindrunnerMomentum rules = new WindrunnerMomentum();
    public Vec3 previous;
    public ResourceKey<Level> dimension;
    public boolean initialized;
    public net.minecraft.world.damagesource.DamageSource lastDodgedSource;
    public long lastDodgeTick = -1000;
    public float movementStage;
    public boolean clientDashing;
    // Synchronized owner-only visual state; changes do not trigger feedback on initial sync.
    public float clientMomentum;
    public int clientStage, transition;
    public boolean clientDecaying, clientInitialized;
    public long transitionTick = -1000;
    public long promotionTick = -1000;
    public int promotionStage;
}
