package net.sonflowers.jeecore.gene.terragolem.menu;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemFuelManager;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemRepairManager;

/**
 * Owns the server repair round, reserved fuel, and timed targets; menu data supplies the client
 * display state.
 */
public final class TerragolemRepairRound {
    public static final int TARGET_COUNT = 3;
    public static final int TARGET_TICKS = 25;
    public static final int PERFECT_TICK = 18;
    public static final int PERFECT_TOLERANCE = 3;
    public static final int HIT_RADIUS = 5;
    public static final int DATA_COUNT = 11;
    // All centers sit inside the body's opaque shapes. Each target uses a different body region.
    private static final int[][] REGIONS = {
            {23, 6, 29, 12}, {17, 27, 35, 43}, {4, 29, 4, 46},
            {47, 29, 47, 46}, {17, 59, 20, 72}, {32, 59, 35, 72}
    };
    private final TerragolemRepairMenu menu;
    private final Player owner;
    private final SimpleContainerData data = new SimpleContainerData(DATA_COUNT);
    private ItemStack reserved = ItemStack.EMPTY;
    private ResourceKey<Level> dimension;
    private long targetStarted;
    private int usedRegions;
    private int sequence;
    private boolean attempted;

    public TerragolemRepairRound(TerragolemRepairMenu menu, Player owner) {
        this.menu = menu;
        this.owner = owner;
        sequence = owner.getRandom().nextInt(30000) + 1;
    }

    public SimpleContainerData data() { return data; }
    public boolean active() { return data.get(0) != 0; }
    public int token() { return data.get(1) & 0xffff; }
    public int targetX() { return data.get(2); }
    public int targetY() { return data.get(3); }
    public int remainingTicks() { return data.get(4); }
    public int step() { return data.get(5); }
    public int score() { return data.get(6); }
    // Feedback: 0 pending, 1 miss, 2 hit, 3 perfect.
    public int feedback() { return data.get(7); }
    public int resultPercent() { return data.get(8); }
    public float healedHearts() { return data.get(9) / 100.0F; }
    public float baseHearts() { return data.get(10) / 2.0F; }

    public boolean start(int slot) {
        if (owner.level().isClientSide || active() || !reserved.isEmpty()) return false;
        reserved = menu.getSlot(slot).remove(1);
        if (reserved.isEmpty()) return false;
        menu.getSlot(slot).setChanged();
        data.set(0, 1);
        data.set(5, 0);
        data.set(6, 0);
        data.set(8, 0);
        data.set(9, 0);
        data.set(10, (int) (TerragolemFuelManager.repairHearts(reserved) * 2));
        dimension = owner.level().dimension();
        usedRegions = 0;
        nextTarget();
        menu.broadcastChanges();
        return true;
    }

    private void nextTarget() {
        int region;
        do { region = owner.getRandom().nextInt(REGIONS.length); } while ((usedRegions & (1 << region)) != 0);
        usedRegions |= 1 << region;
        int[] bounds = REGIONS[region];
        sequence = sequence % 65535 + 1;
        data.set(1, sequence);
        data.set(2, bounds[0] + owner.getRandom().nextInt(bounds[2] - bounds[0] + 1));
        data.set(3, bounds[1] + owner.getRandom().nextInt(bounds[3] - bounds[1] + 1));
        data.set(4, TARGET_TICKS);
        data.set(7, 0);
        attempted = false;
        targetStarted = owner.level().getGameTime();
    }

    // Container button 0 starts a round. Other buttons encode token and body-local coordinates. No
    // client score, heal amount, or timestamp is accepted.
    public static int clickButton(int token, int x, int y) { return (token << 13) | (y << 6) | x; }

    public boolean click(int button) {
        if (owner.level().isClientSide || !active() || attempted || !valid()) return false;
        int requestToken = button >>> 13;
        int x = button & 63;
        int y = (button >>> 6) & 127;
        if (requestToken != token() || x >= 52 || y >= 80) return false;
        long elapsed = owner.level().getGameTime() - targetStarted;
        if (elapsed < 0 || elapsed >= TARGET_TICKS) return false;
        attempted = true; // One attempt per target, including misses; packet spam cannot retry.
        int dx = x - targetX(), dy = y - targetY();
        int points = dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS
                ? (Math.abs(elapsed - PERFECT_TICK) <= PERFECT_TOLERANCE ? 2 : 1) : 0;
        data.set(6, score() + points);
        data.set(7, points + 1);
        if (points == 0) finish(true);
        menu.broadcastChanges();
        return true;
    }

    private boolean valid() {
        return owner.containerMenu == menu && menu.stillValid(owner)
                && owner.level().dimension().equals(dimension)
                && owner.getFoodData().getFoodLevel() > 0 && owner.getHealth() < owner.getMaxHealth();
    }

    public void tick() {
        if (owner.level().isClientSide || !active()) return;
        if (!valid()) { abort(); return; }
        long elapsed = owner.level().getGameTime() - targetStarted;
        data.set(4, (int) Math.clamp(TARGET_TICKS - elapsed, 0L, TARGET_TICKS));
        if (elapsed < TARGET_TICKS) return;
        if (!attempted) {
            data.set(7, 1);
            finish(true);
            return;
        }
        if (step() + 1 < TARGET_COUNT) {
            data.set(5, step() + 1);
            nextTarget();
            return;
        }
        finish(false);
    }

    private void finish(boolean missed) {
        float multiplier = missed ? 0.5F : 0.5F + score() / 6.0F;
        float healed = TerragolemRepairManager.completeRound(owner, menu, reserved, multiplier);
        if (healed <= 0) {
            abort();
            if (missed) cooldown(TerragolemRepairManager.MISSED_REPAIR_COOLDOWN_TICKS);
            menu.broadcastChanges();
            return;
        }
        reserved = ItemStack.EMPTY; // Commit the single reserved fuel only after successful healing.
        data.set(0, 0);
        data.set(8, Math.round(multiplier * 100));
        data.set(9, Math.round(healed * 50)); // Health points -> hearts with two decimal places.
        cooldown(missed ? TerragolemRepairManager.MISSED_REPAIR_COOLDOWN_TICKS
                : TerragolemRepairManager.REPAIR_CLICK_COOLDOWN_TICKS);
        menu.broadcastChanges();
    }

    private void cooldown(int ticks) {
        owner.getData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA).setNextRepairTick(
                owner.level().getGameTime() + ticks);
    }

    public void abort() {
        if (owner.level().isClientSide || (!active() && reserved.isEmpty())) return;
        ItemStack refund = takeReservedFuel();
        data.set(8, -1);
        cooldown(TerragolemRepairManager.REPAIR_CLICK_COOLDOWN_TICKS);
        menu.returnFuel(refund);
    }

    /**
     * Death transfers this item into the normal drops collection exactly once.
     */
    public ItemStack takeReservedFuel() {
        ItemStack stack = reserved;
        reserved = ItemStack.EMPTY;
        data.set(0, 0);
        return stack;
    }
}
