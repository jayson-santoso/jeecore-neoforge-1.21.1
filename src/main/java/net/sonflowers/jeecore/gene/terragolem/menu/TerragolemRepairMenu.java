package net.sonflowers.jeecore.gene.terragolem.menu;

import net.minecraft.world.entity.player.Inventory;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemFuelManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.registry.ModMenus;
import net.sonflowers.jeecore.gene.terragolem.TerragolemTags;
import net.sonflowers.jeecore.gene.terragolem.data.TerragolemFuelData;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemRepairManager;

/**
 * Owns server fuel storage and repair interaction; the client uses an inert storage mirror and
 * synchronized menu data.
 */
public final class TerragolemRepairMenu extends AbstractContainerMenu {
    public static final int REPAIR_BUTTON = 0;
    public static final int FIX_SLOT_X = 82;
    public static final int FIX_SLOT_Y = 58;
    private static final int POWER_SLOT = TerragolemFuelData.FUEL_SLOTS;
    public static final int FIX_SLOT = POWER_SLOT + 1;
    private static final int PLAYER_START = FIX_SLOT + 1;
    private final ItemStackHandler storage;
    private final ItemStackHandler fixFuel = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return TerragolemFuelManager.repairHearts(stack) > 0;
        }
        @Override public void setStackInSlot(int slot, ItemStack stack) {
            if (!stack.isEmpty() && !isItemValid(slot, stack)) {
                throw new IllegalArgumentException("Invalid repair fuel");
            }
            super.setStackInSlot(slot, stack);
        }
    };
    private final Player owner;
    private final ContainerData state;
    private final TerragolemRepairRound round;

    // Client-only mirror; never uses a client player attachment as authoritative storage.
    public TerragolemRepairMenu(int id, Inventory inventory) {
        this(id, inventory, new TerragolemFuelData().fuelInventory(), new SimpleContainerData(8));
    }

    public static TerragolemRepairMenu server(int id, Inventory inventory) {
        Player player = inventory.player;
        return new TerragolemRepairMenu(id, inventory,
                player.getData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA).fuelInventory(), new ContainerData() {
            @Override public int get(int index) {
                if (index == 6) return player.getFoodData().getFoodLevel();
                if (index == 7) return TerragolemRepairManager.cooldownRemaining(player);
                float value = switch (index / 2) {
                    case 0 -> player.getHealth();
                    case 1 -> player.getMaxHealth();
                    default -> player.getFoodData().getSaturationLevel();
                };
                // Container property packets carry signed shorts: split float bits losslessly.
                return (Float.floatToIntBits(value) >>> ((index % 2) * 16)) & 0xffff;
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 8; }
        });
    }

    private TerragolemRepairMenu(int id, Inventory inventory, ItemStackHandler fuel, ContainerData state) {
        super(ModMenus.TERRAGOLEM_REPAIR.get(), id);
        this.owner = inventory.player;
        this.state = state;
        this.storage = fuel;
        this.round = new TerragolemRepairRound(this, owner);
        addDataSlots(state);
        addDataSlots(round.data());
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new SlotItemHandler(fuel, row * 3 + column, 14 + column * 18, 30 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) {
                        return stack.is(TerragolemTags.Items.TERRAGOLEM_FUEL) && super.mayPlace(stack);
                    }
                });
            }
        }
        // A separate inert handler prevents every insertion/extraction path, including quick craft.
        ItemStackHandler placeholder = new ItemStackHandler(1) {
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
            @Override public void setStackInSlot(int slot, ItemStack stack) {}
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        };
        addSlot(new SlotItemHandler(placeholder, 0, 32, 100) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return false; }
        });
        addSlot(new SlotItemHandler(fixFuel, 0, FIX_SLOT_X, FIX_SLOT_Y) {
            @Override public boolean mayPlace(ItemStack stack) {
                return TerragolemFuelManager.repairHearts(stack) > 0 && super.mayPlace(stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 14 + column * 18, 152 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 14 + column * 18, 210));
    }

    /**
     * Explicit slot wins; otherwise use the cheapest supported fuel, breaking ties by slot order.
     */
    public int selectedFuelSlot() {
        if (!fixFuel.getStackInSlot(0).isEmpty()) {
            return TerragolemFuelManager.repairHearts(fixFuel.getStackInSlot(0)) > 0 ? FIX_SLOT : -1;
        }
        int selected = -1;
        int cheapest = Integer.MAX_VALUE;
        for (int i = 0; i < TerragolemFuelData.FUEL_SLOTS; i++) {
            int tier = TerragolemFuelManager.repairTier(storage.getStackInSlot(i));
            if (tier > 0 && tier < cheapest) {
                selected = i;
                cheapest = tier;
            }
        }
        return selected;
    }

    public float selectedRepairHearts() {
        int slot = selectedFuelSlot();
        return slot < 0 ? 0 : TerragolemFuelManager.repairHearts(getSlot(slot).getItem());
    }

    public ItemStack takeFixFuel() {
        ItemStack stack = fixFuel.getStackInSlot(0);
        fixFuel.setStackInSlot(0, ItemStack.EMPTY);
        return stack;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) return;
        round.abort();
        returnFuel(takeFixFuel());
    }

    public TerragolemRepairRound round() { return round; }

    public void returnFuel(ItemStack remaining) {
        if (owner.level().isClientSide) return;
        Player player = owner;
        if (remaining.isEmpty()) return;
        if (player.isAlive()) {
            for (int i = 0; i < storage.getSlots() && !remaining.isEmpty(); i++) {
                remaining = storage.insertItem(i, remaining, false);
            }
            if (!remaining.isEmpty()) player.getInventory().add(remaining);
        }
        if (!remaining.isEmpty()) player.drop(remaining, false);
    }

    private float readFloat(int start) {
        return Float.intBitsToFloat((state.get(start) & 0xffff) | ((state.get(start + 1) & 0xffff) << 16));
    }
    public float health() { return readFloat(0); }
    public float maxHealth() { return readFloat(2); }
    public float saturation() { return readFloat(4); }
    public int hunger() { return state.get(6); }
    public int cooldown() { return state.get(7); }

    @Override public boolean stillValid(Player player) {
        return player == owner && player.isAlive() && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide || player.containerMenu != this || !stillValid(player)) return false;
        return button == REPAIR_BUTTON ? TerragolemRepairManager.repair(player, this) : round.click(button);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size() || index == POWER_SLOT) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < POWER_SLOT || index == FIX_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!stack.is(TerragolemTags.Items.TERRAGOLEM_FUEL)
                    || !moveItemStackTo(stack, 0, POWER_SLOT, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
