package net.sonflowers.jeecore.gene.infernal.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Shares client flame geometry and flutter; individual effects supply their own palettes.
 */
final class InfernalFlameShape {
    private final float width, height, phase, frequency;

    InfernalFlameShape(RandomSource random) {
        width = 0.45f + random.nextFloat() * 1.15f;
        height = 0.65f + random.nextFloat() * 1.9f;
        phase = random.nextFloat() * (float) Math.PI * 2.0f;
        frequency = 0.35f + random.nextFloat() * 0.55f;
    }

    Vec3 turbulence(int age, double strength) {
        return new Vec3(Math.sin(phase + age * frequency) * strength,
                Math.sin(phase + age * frequency * 0.7) * strength * 0.3,
                Math.cos(phase + age * frequency * 0.8) * strength);
    }

    void draw(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
              Vector3f left, Vector3f up, float size, float age, int alpha, int r, int g, int b) {
        float flicker = 0.75f + 0.25f * (float) Math.sin(phase + age * frequency * 2.0f);
        float w = size * width * flicker;
        float h = size * height * flicker * (0.8f + 0.35f * (float) Math.sin(phase + age * frequency));
        int a = (int) (alpha * flicker);
        buffer.addVertex(matrix, x - left.x() * w - up.x() * h, y - left.y() * w - up.y() * h, z - left.z() * w - up.z() * h).setColor(r, g, b, a);
        buffer.addVertex(matrix, x + left.x() * w - up.x() * h, y + left.y() * w - up.y() * h, z + left.z() * w - up.z() * h).setColor(r, g, b, a);
        buffer.addVertex(matrix, x + left.x() * w + up.x() * h, y + left.y() * w + up.y() * h, z + left.z() * w + up.z() * h).setColor(r, g, b, a);
        buffer.addVertex(matrix, x - left.x() * w + up.x() * h, y - left.y() * w + up.y() * h, z - left.z() * w + up.z() * h).setColor(r, g, b, a);
    }
}
