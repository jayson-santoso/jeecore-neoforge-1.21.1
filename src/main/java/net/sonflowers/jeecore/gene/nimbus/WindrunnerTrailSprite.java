package net.sonflowers.jeecore.gene.nimbus;

import java.util.Random;
import java.util.UUID;

/** Frame layout and animation contract for the original, transparent 64x112 player-supplied PNG. */
public final class WindrunnerTrailSprite {
    public static final int TEXTURE_WIDTH = 64, TEXTURE_HEIGHT = 112, FRAME_HEIGHT = 16, FRAME_COUNT = 7;
    private static final int[] LOOP = {0, 1, 2, 3, 4, 5, 6};
    public record Size(float length, float width) {}
    public record Frame(float minU, float maxU, float minV, float maxV) {}
    private WindrunnerTrailSprite() {}

    public static Size size(float stage) {
        float s = Math.clamp(stage, 1, 3);
        if (s <= 2) return new Size(1.5f + (s - 1) * 1.2f, .27f + (s - 1) * .10f);
        return new Size(2.7f + (s - 2) * 1.3f, .37f + (s - 2) * .12f);
    }

    /** Smooth spatial fade, independent of the trail sample's age-based fade. */
    public static float tailOpacity(double progress) {
        double u = Math.clamp(progress, 0, 1);
        return (float)(1 - u * u * (3 - 2 * u));
    }

    public static float speedFactor(double blocksPerTick) {
        return (float)Math.clamp((blocksPerTick - .08) / .30, 0, 1);
    }

    public static float stopOpacity(double idleTicks) {
        float remaining = (float)Math.clamp(1 - idleTicks / 6, 0, 1);
        return remaining * remaining;
    }

    /** Stepped highlights travel from the feet toward the tail; they never sweep forward. */
    public static float speedHighlight(double progress, double ticks, double phase) {
        int cell = Math.floorMod((long)Math.floor(progress * 20 - ticks * .75 + phase), 6);
        return cell == 0 ? 1 : cell == 1 ? .82f : .65f;
    }

    /** Brief collision/slowdown gaps are harmless; real pauses and teleports break the path. */
    public static boolean connectsRun(long now, long lastEmission, double distance) {
        long elapsed = now - lastEmission;
        return elapsed >= 1 && elapsed <= 4 && Double.isFinite(distance) && distance >= 0 && distance <= 8;
    }

    public static int frameAt(double ticks, double phase, double rate) {
        return LOOP[Math.floorMod((long)Math.floor((ticks * rate + phase) / 2), LOOP.length)];
    }

    public static Frame frame(int index) {
        int row = Math.clamp(index, 0, FRAME_COUNT - 1) * FRAME_HEIGHT;
        // Crop the unused transparent padding through UVs; preserve the source PNG byte-for-byte.
        return new Frame(4.5f / TEXTURE_WIDTH, 46.5f / TEXTURE_WIDTH,
                (row + 3.5f) / TEXTURE_HEIGHT, (row + 12.5f) / TEXTURE_HEIGHT);
    }

    private static long seed(UUID owner) {
        return owner.getMostSignificantBits() ^ Long.rotateLeft(owner.getLeastSignificantBits(), 19);
    }

    public static double phase(UUID owner, int layer) {
        return (new Random(seed(owner)).nextDouble() * 14 + layer * 3.25) % 14;
    }

    public static double rate(UUID owner, int layer) {
        return .94 + new Random(seed(owner) + 0x9E3779B97F4A7C15L * (layer + 1)).nextDouble() * .12;
    }
}
