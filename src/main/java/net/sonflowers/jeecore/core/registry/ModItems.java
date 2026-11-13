package net.sonflowers.jeecore.core.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sonflowers.jeecore.JeeCore;

/**
 * Declares mod items and attaches their deferred register to the mod event bus.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JeeCore.MOD_ID);
    public static final DeferredItem<Item> REFINED_COAL = ITEMS.registerSimpleItem("refined_coal");
    public static final DeferredItem<Item> POWERED_COAL = ITEMS.registerSimpleItem("powered_coal");
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JeeCore.MOD_ID);

    static {
        TABS.register("jeecore", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.jeecore"))
                .icon(() -> POWERED_COAL.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(REFINED_COAL.get());
                    output.accept(POWERED_COAL.get());
                }).build());
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
