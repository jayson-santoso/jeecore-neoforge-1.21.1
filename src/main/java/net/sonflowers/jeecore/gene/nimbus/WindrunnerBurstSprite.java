package net.sonflowers.jeecore.gene.nimbus;

/** One-shot timing for the supplied 64x256 sheet: seven smoke poses and one blank row. */
public final class WindrunnerBurstSprite {
    public static final int LIFETIME = 44;
    public static final double DODGE_PLAYBACK_RATE = 1.25;
    public static final int DODGE_LIFETIME = (int)Math.ceil(LIFETIME / DODGE_PLAYBACK_RATE);
    private static final int[] FRAME_STARTS = {0, 2, 5, 10, 17, 25, 34};

    private WindrunnerBurstSprite() {}

    public static float scale(int stage) {
        return .65f + (Math.clamp(stage, 1, 3) - 1) * .175f;
    }

    public static int frameAt(double age) {
        for (int i = FRAME_STARTS.length - 1; i > 0; i--) {
            if (age >= FRAME_STARTS[i]) return i;
        }
        return 0;
    }

    public static float opacity(double age) {
        if (age < 0 || age >= LIFETIME) return 0;
        double fade = Math.clamp((age - 2) / (LIFETIME - 2), 0, 1);
        return (float)(Math.min(age / 2, 1) * (1 - fade * fade * (3 - 2 * fade)));
    }

    public static WindrunnerTrailSprite.Frame frame(int index) {
        int row = Math.clamp(index, 0, 6) * 32;
        // Fixed bounds preserve the expansion in the artwork without frame-to-frame stretching.
        return new WindrunnerTrailSprite.Frame(6.5f / 64, 45.5f / 64,
                (row + 6.5f) / 256, (row + 25.5f) / 256);
    }
}
