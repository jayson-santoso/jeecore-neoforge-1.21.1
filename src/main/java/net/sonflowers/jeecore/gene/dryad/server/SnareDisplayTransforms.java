package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import org.joml.Matrix4f;

/**
 * Builds display transformation NBT for server display entities whose mapped setters are private.
 */
final class SnareDisplayTransforms {
    private SnareDisplayTransforms() {}

    static CompoundTag animation(Matrix4f matrix, int duration, float width, float height) {
        CompoundTag tag = new CompoundTag();
        ListTag values = new ListTag();
        for (int i = 0; i < 16; i++) values.add(FloatTag.valueOf(matrix.getRowColumn(i / 4, i % 4)));
        tag.put("transformation", values);
        tag.putInt("interpolation_duration", duration);
        tag.putInt("start_interpolation", 0);
        tag.putInt("teleport_duration", 2);
        tag.putFloat("view_range", 1.5f);
        tag.putFloat("width", width);
        tag.putFloat("height", height);
        tag.putFloat("shadow_radius", 0);
        // No brightness override: vanilla display rendering samples world lighting.
        return tag;
    }

    static Matrix4f plant(float scale, int half, float growth, float tilt, float windDirection) {
        float sink = -0.16f * (1 - Math.min(1, growth));
        return new Matrix4f().translate(0, sink, 0)
                .rotate(tilt, (float) Math.cos(windDirection), 0, (float) Math.sin(windDirection))
                .scale(scale, scale * growth, scale)
                .translate(-0.5f, half, -0.5f);
    }
}
