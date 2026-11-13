package net.sonflowers.jeecore.gene.terragolem.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModParticles;

/**
 * Implements the client refuel spark; fuel tiers share geometry while retaining distinct color and
 * brightness.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class TerragolemRefuelSparkParticle extends TextureSheetParticle {
    private final int tier;
    private final float initialAlpha;
    private TerragolemRefuelSparkParticle(ClientLevel level, double x, double y, double z,
                                          double dx, double dy, double dz, SpriteSet sprites, int tier) {
        super(level, x, y, z);
        this.tier = tier;
        initialAlpha = tier == 1 ? 0.65F : 1.0F;
        alpha = initialAlpha;
        xd = dx;
        yd = dy;
        zd = dz;
        gravity = -0.025F;
        friction = 0.97F;
        hasPhysics = false;
        lifetime = 12 + random.nextInt(13);
        quadSize = 0.06F + random.nextFloat() * 0.04F;
        if (tier == 1) {
            setColor(1.0F, 0.72F + random.nextFloat() * 0.08F, 0.48F);
        } else if (tier == 2) {
            setColor(1.0F, 0.65F + random.nextFloat() * 0.10F, 0.24F);
        } else {
            setColor(1.0F, 0.30F + random.nextFloat() * 0.15F, 0.015F);
        }
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        alpha = initialAlpha * Math.max(0.0F, 1.0F - (float) age / lifetime);
    }

    @Override
    public int getLightColor(float partialTick) {
        if (tier == 3) return 0xF000F0;
        int light = super.getLightColor(partialTick);
        int minimumBlockLight = tier == 1 ? 6 * 16 : 14 * 16;
        return (light & 0xFFFF0000) | Math.max(light & 0xFFFF, minimumBlockLight);
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @Override
    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf rotation,
                                     float x, float y, float z, float partialTick) {
        float size = getQuadSize(partialTick);
        int light = getLightColor(partialTick);
        // Every fuel shares the halo and white core; tint and brightness retain tier identity.
        renderStreak(buffer, rotation, x, y, z, size * 0.85F, size * 1.75F,
                rCol, gCol, bCol, alpha * 0.18F, light);
        renderStreak(buffer, rotation, x, y, z, size * 0.5F, size * 1.6F,
                rCol, gCol, bCol, alpha, light);
        renderStreak(buffer, rotation, x, y, z, size * 0.19F, size * 1.4F,
                1.0F, 1.0F, 1.0F, alpha, light);
    }

    private void renderStreak(VertexConsumer buffer, Quaternionf rotation,
                              float x, float y, float z, float halfWidth, float halfHeight,
                              float red, float green, float blue, float opacity, int light) {
        streakVertex(buffer, rotation, x, y, z, halfWidth, -halfHeight, getU1(), getV1(), red, green, blue, opacity, light);
        streakVertex(buffer, rotation, x, y, z, halfWidth, halfHeight, getU1(), getV0(), red, green, blue, opacity, light);
        streakVertex(buffer, rotation, x, y, z, -halfWidth, halfHeight, getU0(), getV0(), red, green, blue, opacity, light);
        streakVertex(buffer, rotation, x, y, z, -halfWidth, -halfHeight, getU0(), getV1(), red, green, blue, opacity, light);
    }

    private static void streakVertex(VertexConsumer buffer, Quaternionf rotation,
                                      float x, float y, float z, float dx, float dy, float u, float v,
                                      float red, float green, float blue, float opacity, int light) {
        Vector3f vertex = new Vector3f(dx, dy, 0).rotate(rotation).add(x, y, z);
        buffer.addVertex(vertex.x(), vertex.y(), vertex.z()).setUv(u, v)
                .setColor(red, green, blue, opacity).setLight(light);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(float partialTick) {
        return super.getRenderBoundingBox(partialTick).inflate(getQuadSize(partialTick));
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.REFUEL_SPARK.get(), sprites ->
                (type, level, x, y, z, dx, dy, dz) ->
                        new TerragolemRefuelSparkParticle(level, x, y, z, dx, dy, dz, sprites, 1));
        event.registerSpriteSet(ModParticles.REFINED_REFUEL_SPARK.get(), sprites ->
                (type, level, x, y, z, dx, dy, dz) ->
                        new TerragolemRefuelSparkParticle(level, x, y, z, dx, dy, dz, sprites, 2));
        event.registerSpriteSet(ModParticles.POWERED_REFUEL_SPARK.get(), sprites ->
                (type, level, x, y, z, dx, dy, dz) ->
                        new TerragolemRefuelSparkParticle(level, x, y, z, dx, dy, dz, sprites, 3));
    }
}
