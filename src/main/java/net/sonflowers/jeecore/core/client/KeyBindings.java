package net.sonflowers.jeecore.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Defines the client key mappings for primary and secondary abilities and mode toggles.
 */
public class KeyBindings {
    public static final String CATEGORY = "key.categories.jeecore";

    public static final KeyMapping PRIMARY_ABILITY = new KeyMapping(
            "key.jeecore.primary_ability",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );
    public static final KeyMapping SECONDARY_ABILITY = new KeyMapping(
            "key.jeecore.secondary_ability",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );
    public static final KeyMapping TOGGLE_ABILITY = new KeyMapping(
            "key.jeecore.toggle_ability",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );
}
