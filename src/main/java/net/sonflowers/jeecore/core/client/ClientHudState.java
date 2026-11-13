package net.sonflowers.jeecore.core.client;

/**
 * Stores the local HUD cooldown mirror; reset clears initialization and transient display state.
 */
public class ClientHudState {
    private static CooldownState primaryCooldown = CooldownState.ready();
    private static CooldownState secondaryCooldown = CooldownState.ready();
    private static boolean initialized = false;
    private static int pounceCharges;

    public static int getPounceCharges() {
        return pounceCharges;
    }

    public static void setPounceCharges(int charges) {
        pounceCharges = Math.max(0, Math.min(3, charges));
    }

    public static void setCooldown(boolean primary, int remainingTicks, int maxTicks, double gameTime) {
        CooldownState state = new CooldownState(Math.max(0, remainingTicks), Math.max(0, maxTicks), gameTime);
        if (primary) {
            primaryCooldown = state;
        } else {
            secondaryCooldown = state;
        }
        initialized = true;
    }

    public static CooldownState getPrimaryCooldown() {
        return primaryCooldown;
    }

    public static CooldownState getSecondaryCooldown() {
        return secondaryCooldown;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void reset() {
        primaryCooldown = CooldownState.ready();
        secondaryCooldown = CooldownState.ready();
        initialized = false;
        pounceCharges = 0;
    }

    public record CooldownState(int startingRemainingTicks, int maxTicks, double startGameTime) {
        public static CooldownState ready() {
            return new CooldownState(0, 0, 0.0);
        }

        public float remainingTicks(double gameTime) {
            return Math.max(0.0f, (float) (startingRemainingTicks - (gameTime - startGameTime)));
        }

        public float progress(double gameTime) {
            if (maxTicks <= 0) return 1.0f;
            return 1.0f - remainingTicks(gameTime) / maxTicks;
        }

        public boolean ready(double gameTime) {
            return remainingTicks(gameTime) <= 0.0f;
        }
    }
}
