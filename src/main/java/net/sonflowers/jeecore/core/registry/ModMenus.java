package net.sonflowers.jeecore.core.registry;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.menu.TerragolemRepairMenu;

/**
 * Declares menu types shared by server containers and client screens.
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, JeeCore.MOD_ID);
    public static final Supplier<MenuType<TerragolemRepairMenu>> TERRAGOLEM_REPAIR = MENUS.register(
            "terragolem_repair", () -> new MenuType<>(TerragolemRepairMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
