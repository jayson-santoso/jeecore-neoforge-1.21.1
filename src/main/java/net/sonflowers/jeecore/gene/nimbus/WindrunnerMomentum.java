package net.sonflowers.jeecore.gene.nimbus;

/**
 * Encapsulates server-owned momentum stages and dodge rules using game ticks, without Minecraft
 * world access.
 */
public final class WindrunnerMomentum {
    public enum Movement { SPRINT, WALK, IDLE, SNEAK, EXCLUDED }
    private float momentum;
    private int stage;
    private boolean decaying;
    private long lastAttack = -1000, disruptedUntil = -1000;
    private long nextDodge = -1000, cloudUntil = -1000;

    public float momentum() { return momentum; }
    public int stage() { return stage; }
    public boolean decaying() { return decaying && momentum > 0; }
    public float dodgeChance() { return switch (stage) { case 1 -> .30f; case 2 -> .50f; case 3 -> .60f; default -> 0; }; }
    public boolean dodging(long now) { return now < cloudUntil; }

    public void add(float amount) {
        momentum = Math.clamp(momentum + amount, 0, 100);
        decaying = amount < 0;
        updateStage();
    }

    public void tick(long now, Movement movement) {
        if (movement == Movement.SPRINT && now < disruptedUntil) movement = Movement.IDLE;
        add(switch (movement) {
            case SPRINT -> .30f;
            case WALK -> -.20f;
            case SNEAK -> -.60f;
            case IDLE, EXCLUDED -> -.40f;
        });
    }

    public void attack(long now) {
        if (now - lastAttack < 10) return;
        lastAttack = now;
        disruptedUntil = Math.max(disruptedUntil, now + 10);
        add(-10);
    }

    public boolean tryDodge(long now, float roll) {
        if (stage == 0 || now < nextDodge || roll >= dodgeChance()) return false;
        nextDodge = now + 20;
        cloudUntil = now + 10;
        return true;
    }

    private void updateStage() {
        while (stage < 3 && momentum >= (stage + 1) * 25) stage++;
        while (stage > 0 && momentum < stage * 25 - 5) stage--;
    }
}
