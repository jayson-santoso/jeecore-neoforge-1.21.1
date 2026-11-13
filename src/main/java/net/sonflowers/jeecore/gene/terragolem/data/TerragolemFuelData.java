package net.sonflowers.jeecore.gene.terragolem.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.sonflowers.jeecore.gene.terragolem.TerragolemTags;

/**
 * Serializes player fuel storage and repair accounting; the power-core slot remains reserved and
 * empty.
 */
public final class TerragolemFuelData implements INBTSerializable<CompoundTag> {
    public static final int FUEL_SLOTS = 9;
    private long nextRepairTick;
    private int heldFuelTicks;
    private final ItemStackHandler fuelInventory = new ItemStackHandler(FUEL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < FUEL_SLOTS && stack.is(TerragolemTags.Items.TERRAGOLEM_FUEL);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (!stack.isEmpty() && !isItemValid(slot, stack)) {
                throw new IllegalArgumentException("Only tagged Terragolem fuel may be stored");
            }
            super.setStackInSlot(slot, stack);
        }

        @Override
        public void setSize(int size) {
            // Serialized data cannot change the menu's fixed layout.
            super.setSize(FUEL_SLOTS);
        }

        @Override
        protected void onContentsChanged(int slot) {
            // Entity attachments are serialized on every player save; unlike chunks they need no
            // dirty flag. Open menus detect stack changes through normal slot synchronization.
        }
    };

    public ItemStackHandler fuelInventory() { return fuelInventory; }
    /**
     * Returns the absolute level game tick at which repair becomes available.
     */
    public long nextRepairTick() { return nextRepairTick; }
    /**
     * Returns the carried partial held-beam usage toward the next hunger cost, in game ticks.
     */
    public int heldFuelTicks() { return heldFuelTicks; }
    public void setHeldFuelTicks(int value) { heldFuelTicks = Math.max(0, value); }
    public void setNextRepairTick(long value) { nextRepairTick = value; }
    public ItemStack powerCore() { return ItemStack.EMPTY; }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put("FuelInventory", fuelInventory.serializeNBT(provider));
        tag.put("PowerCore", new CompoundTag()); // Reserved, deliberately always empty.
        tag.putLong("NextRepairTick", nextRepairTick);
        tag.putInt("HeldFuelTicks", heldFuelTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        fuelInventory.deserializeNBT(provider, tag.getCompound("FuelInventory"));
        // ItemStackHandler's NBT reader bypasses isItemValid; validate command-edited data too.
        for (int slot = 0; slot < FUEL_SLOTS; slot++) {
            ItemStack stack = fuelInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && !fuelInventory.isItemValid(slot, stack)) {
                fuelInventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        nextRepairTick = tag.contains("NextRepairTick", Tag.TAG_LONG) ? tag.getLong("NextRepairTick") : 0L;
        heldFuelTicks = Math.clamp(tag.getInt("HeldFuelTicks"), 0,
                net.sonflowers.jeecore.gene.terragolem.server.TerragolemAbilityFuelManager.HOLD_TICKS_PER_HUNGER - 1);
    }
}
