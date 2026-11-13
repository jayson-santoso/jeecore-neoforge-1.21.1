package net.sonflowers.jeecore.core.gene;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Stores serializable player gene and ability values; server updates are mirrored to the client by
 * payloads.
 */
public class PlayerGeneData implements INBTSerializable<CompoundTag> {
    // Gene selection and ability state.
    private Gene gene = Gene.NONE;
    private boolean hasChosen = false;
    private boolean glideToggled = false;
    private int glideResource = 0;
    private boolean smokeFormActive = false;
    private int smokeResource = MAX_SMOKE_RESOURCE;
    private int smokeCooldownTicks = 0;
    private boolean ignitionChargeEnabled = true;
    private int ignitionCharge = 0;
    private int ignitionTimer = 0;
    private int ignitionCooldownTicks = 0;
    private boolean ignitionQuickTimeActive = false;
    private int ignitionQuickTimeZoneStart = 42;
    private int ignitionQuickTimeZoneWidth = 12;
    private int rageBar = 0;
    private int overheatBar = 0;
    private boolean overheatPrimaryLocked = false;
    // Resource limits.
    private static final int MAX_GLIDE_RESOURCE = 300;
    private static final int MAX_SMOKE_RESOURCE = 70;
    private static final int MAX_IGNITION_CHARGE = 100;
    private static final int MAX_IGNITION_TIMER = 100;
    private static final int MAX_RAGE_BAR = 200;
    private static final int MAX_OVERHEAT_BAR = 500;

    // Selection and lifecycle.
    public Gene getGene() {
        return gene;
    }

    public void setGene(Gene gene) {
        this.gene = gene;
        this.hasChosen = true;
    }

    /**
     * Copies gene and ability values during player cloning; lifecycle handlers reset transient
     * abilities separately.
     */
    public void copyFrom(PlayerGeneData other) {
        this.gene = other.gene;
        this.hasChosen = other.hasChosen;
        this.glideToggled = other.glideToggled;
        this.glideResource = other.glideResource;
        this.smokeFormActive = other.smokeFormActive;
        this.smokeResource = other.smokeResource;
        this.smokeCooldownTicks = other.smokeCooldownTicks;
        this.ignitionChargeEnabled = other.ignitionChargeEnabled;
        this.ignitionCharge = other.ignitionCharge;
        this.ignitionTimer = other.ignitionTimer;
        this.ignitionCooldownTicks = other.ignitionCooldownTicks;
        this.ignitionQuickTimeActive = other.ignitionQuickTimeActive;
        this.ignitionQuickTimeZoneStart = other.ignitionQuickTimeZoneStart;
        this.ignitionQuickTimeZoneWidth = other.ignitionQuickTimeZoneWidth;
        this.rageBar = other.rageBar;
        this.overheatBar = other.overheatBar;
    }

    /**
     * Clears active ability resources and timers while retaining the selected gene and choice
     * flag.
     */
    public void resetAbilityState() {
        this.glideToggled = false;
        this.glideResource = 0;
        this.smokeFormActive = false;
        this.smokeResource = MAX_SMOKE_RESOURCE;
        this.smokeCooldownTicks = 0;
        this.ignitionChargeEnabled = true;
        this.ignitionCharge = 0;
        this.ignitionTimer = 0;
        this.ignitionCooldownTicks = 0;
        this.ignitionQuickTimeActive = false;
        this.ignitionQuickTimeZoneStart = 42;
        this.ignitionQuickTimeZoneWidth = 12;
        this.rageBar = 0;
        this.overheatBar = 0;
    }

    public boolean hasChosen() {
        return hasChosen;
    }

    /**
     * Clears gene selection and its mode state; lifecycle cleanup handles external manager state.
     */
    public void reset() {
        this.gene = Gene.NONE;
        this.hasChosen = false;
        this.glideToggled = false;
        this.glideResource = 0;
        this.smokeFormActive = false;
        this.smokeResource = MAX_SMOKE_RESOURCE;
        this.smokeCooldownTicks = 0;
        this.ignitionChargeEnabled = true;
        this.ignitionCharge = 0;
        this.ignitionTimer = 0;
        this.ignitionCooldownTicks = 0;
        this.ignitionQuickTimeActive = false;
        this.ignitionQuickTimeZoneStart = 42;
        this.ignitionQuickTimeZoneWidth = 12;
    }

    // Nimbus glide state.
    public boolean isGlideToggled() {
        return glideToggled;
    }

    public void setGlideToggled(boolean toggled) {
        this.glideToggled = toggled;
    }

    public int getGlideResource() {
        return glideResource;
    }

    public void setGlideResource(int resource) {
        this.glideResource = Math.max(0, Math.min(resource, MAX_GLIDE_RESOURCE));
    }

    public void addGlideResource(int amount) {
        setGlideResource(glideResource + amount);
    }

    public int getMaxGlideResource() {
        return MAX_GLIDE_RESOURCE;
    }

    // Infernal smoke state.
    public boolean isSmokeFormActive() {
        return smokeFormActive;
    }

    public void setSmokeFormActive(boolean active) {
        this.smokeFormActive = active;
    }

    public int getSmokeResource() {
        return smokeResource;
    }

    public void setSmokeResource(int resource) {
        this.smokeResource = Math.max(0, Math.min(resource, MAX_SMOKE_RESOURCE));
    }

    public void addSmokeResource(int amount) {
        setSmokeResource(smokeResource + amount);
    }

    public int getMaxSmokeResource() {
        return MAX_SMOKE_RESOURCE;
    }

    public int getSmokeCooldownTicks() {
        return smokeCooldownTicks;
    }

    public void setSmokeCooldownTicks(int ticks) {
        this.smokeCooldownTicks = Math.max(0, ticks);
    }

    // Infernal ignition and quick-time state.
    public boolean isIgnitionChargeEnabled() {
        return ignitionChargeEnabled;
    }

    public void setIgnitionChargeEnabled(boolean enabled) {
        this.ignitionChargeEnabled = enabled;
    }

    public int getIgnitionCharge() {
        return ignitionCharge;
    }

    public void setIgnitionCharge(int charge) {
        this.ignitionCharge = Math.max(0, Math.min(charge, MAX_IGNITION_CHARGE));
    }

    public void addIgnitionCharge(int amount) {
        setIgnitionCharge(ignitionCharge + amount);
    }

    public int getMaxIgnitionCharge() {
        return MAX_IGNITION_CHARGE;
    }

    public int getIgnitionTimer() {
        return ignitionTimer;
    }

    public void setIgnitionTimer(int timer) {
        this.ignitionTimer = Math.max(0, Math.min(timer, MAX_IGNITION_TIMER));
    }

    public void addIgnitionTimer(int amount) {
        setIgnitionTimer(ignitionTimer + amount);
    }

    public int getMaxIgnitionTimer() {
        return MAX_IGNITION_TIMER;
    }

    public int getIgnitionCooldownTicks() {
        return ignitionCooldownTicks;
    }

    public void setIgnitionCooldownTicks(int ticks) {
        this.ignitionCooldownTicks = Math.max(0, ticks);
    }

    public boolean isIgnitionQuickTimeActive() {
        return ignitionQuickTimeActive;
    }

    public void setIgnitionQuickTimeActive(boolean active) {
        this.ignitionQuickTimeActive = active;
    }

    public int getIgnitionQuickTimeZoneStart() {
        return ignitionQuickTimeZoneStart;
    }

    public void setIgnitionQuickTimeZoneStart(int zoneStart) {
        this.ignitionQuickTimeZoneStart = Math.max(0, Math.min(zoneStart, 99));
    }

    public int getIgnitionQuickTimeZoneWidth() {
        return ignitionQuickTimeZoneWidth;
    }

    public void setIgnitionQuickTimeZoneWidth(int zoneWidth) {
        this.ignitionQuickTimeZoneWidth = Math.max(1, Math.min(zoneWidth, 100));
    }

    // Infernal rage state.
    public int getRageBar() { return rageBar; }
    public void setRageBar(int value) { this.rageBar = Math.max(0, Math.min(value, MAX_RAGE_BAR)); }
    public void addRageBar(int amount) { setRageBar(rageBar + amount); }
    public int getMaxRageBar() { return MAX_RAGE_BAR; }

    // Terragolem heat state.
    public int getOverheatBar() { return overheatBar; }
    public void setOverheatBar(int value) { this.overheatBar = Math.max(0, Math.min(value, MAX_OVERHEAT_BAR)); }
    public void addOverheatBar(int amount) { setOverheatBar(overheatBar + amount); }
    public int getMaxOverheatBar() { return MAX_OVERHEAT_BAR; }

    public boolean isOverheatPrimaryLocked() { return overheatPrimaryLocked; }
    public void setOverheatPrimaryLocked(boolean locked) { this.overheatPrimaryLocked = locked; }

    // Persistence.
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("gene", gene.getSerializedName());
        tag.putBoolean("hasChosen", hasChosen);
        tag.putBoolean("glideToggled", glideToggled);
        tag.putInt("glideResource", glideResource);
        tag.putBoolean("smokeFormActive", smokeFormActive);
        tag.putInt("smokeResource", smokeResource);
        tag.putInt("smokeCooldownTicks", smokeCooldownTicks);
        tag.putBoolean("ignitionChargeEnabled", ignitionChargeEnabled);
        tag.putInt("ignitionCharge", ignitionCharge);
        tag.putInt("ignitionTimer", ignitionTimer);
        tag.putInt("ignitionCooldownTicks", ignitionCooldownTicks);
        tag.putBoolean("ignitionQuickTimeActive", ignitionQuickTimeActive);
        tag.putInt("ignitionQuickTimeZoneStart", ignitionQuickTimeZoneStart);
        tag.putInt("ignitionQuickTimeZoneWidth", ignitionQuickTimeZoneWidth);
        tag.putInt("rageBar", rageBar);
        tag.putInt("overheatBar", overheatBar);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.gene = Gene.fromString(tag.getString("gene"));
        this.hasChosen = tag.getBoolean("hasChosen");
        this.glideToggled = tag.getBoolean("glideToggled");
        setGlideResource(tag.getInt("glideResource"));
        this.smokeFormActive = tag.getBoolean("smokeFormActive");
        setSmokeResource(tag.contains("smokeResource") ? tag.getInt("smokeResource") : MAX_SMOKE_RESOURCE);
        setSmokeCooldownTicks(tag.getInt("smokeCooldownTicks"));
        this.ignitionChargeEnabled = !tag.contains("ignitionChargeEnabled") || tag.getBoolean("ignitionChargeEnabled");
        setIgnitionCharge(tag.getInt("ignitionCharge"));
        setIgnitionTimer(tag.getInt("ignitionTimer"));
        setIgnitionCooldownTicks(tag.getInt("ignitionCooldownTicks"));
        setIgnitionQuickTimeActive(tag.getBoolean("ignitionQuickTimeActive"));
        setIgnitionQuickTimeZoneStart(tag.contains("ignitionQuickTimeZoneStart") ? tag.getInt("ignitionQuickTimeZoneStart") : 42);
        setIgnitionQuickTimeZoneWidth(tag.contains("ignitionQuickTimeZoneWidth") ? tag.getInt("ignitionQuickTimeZoneWidth") : 12);
        setRageBar(tag.contains("rageBar") ? tag.getInt("rageBar") : 0);
        setOverheatBar(tag.contains("overheatBar") ? tag.getInt("overheatBar") : 0);
    }
}
