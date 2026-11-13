package net.sonflowers.jeecore.gene.nimbus;

/**
 * Calculates horizontal air control without world access; speed is in blocks per tick and input is
 * world-relative.
 */
public final class WindrunnerAirControl {
    public record Motion(double x, double z) {}
    private WindrunnerAirControl() {}

    public static Motion move(double x, double z, double inputX, double inputZ,
                              double acceleration, double baseLimit, float stage) {
        double inputLength = Math.hypot(inputX, inputZ);
        if (inputLength < 1.0e-5) return new Motion(x, z);
        double dx = inputX / inputLength, dz = inputZ / inputLength;
        double strength = Math.min(1, inputLength);
        double oldSpeed = Math.hypot(x, z);
        double limit = baseLimit * (1 + .10 * stage);
        // Preserve external impulses. Windrunner neither amplifies nor clamps them.
        if (oldSpeed > limit * 1.35) {
            return new Motion(x + inputX * acceleration, z + inputZ * acceleration);
        }
        double along = x * dx + z * dz;
        double driftX = x - along * dx, driftZ = z - along * dz;
        double steering = .04 * stage * strength;
        double braking = along < 0 ? (stage >= 2 ? .30 + .20 * (stage - 2) : .15 * stage) : 0;
        double push = acceleration * strength * (1 + .15 * stage + braking);
        double nextX = x - driftX * steering + dx * push;
        double nextZ = z - driftZ * steering + dz * push;
        double speed = Math.hypot(nextX, nextZ);
        double allowed = Math.max(limit, oldSpeed);
        if (speed > allowed) { nextX *= allowed / speed; nextZ *= allowed / speed; }
        return new Motion(nextX, nextZ);
    }
}
