package net.sonflowers.jeecore.core.gene;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Defines gene identifiers, display metadata, and default ability cooldowns in game ticks.
 */
public enum Gene implements StringRepresentable {
    NONE("none", "No Gene", 0x808080),
    NIMBUS("nimbus", "Nimbus", 0x87CEEB),
    INFERNAL("infernal", "Infernal", 0xFF4500),
    DRYAD("dryad", "Dryad", 0x228B22),
    TERRAGOLEM("terragolem", "Terragolem", 0x8B4513),
    FLICKER("flicker", "Flicker", 0xFFD700);

    private final String id;
    private final String displayName;
    private final int color;

    Gene(String id, String displayName, int color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Component getDisplayComponent() {
        return Component.literal(displayName);
    }

    public int getColor() {
        return color;
    }

    public static Gene fromString(String id) {
        for (Gene gene : values()) {
            if (gene.id.equals(id)) return gene;
        }
        return NONE;
    }

    public int getPrimaryCooldown() {
        return switch (this) {
            case NIMBUS -> 200; // 10-second dash cooldown.
            case DRYAD -> 600; // 30 seconds for elixir toss.
            case TERRAGOLEM -> 100; // 5 seconds, matching the ready-up window.
            default -> 0;
        };
    }

    public int getSecondaryCooldown() {
        return switch (this) {
            case NIMBUS -> 110;
            case INFERNAL -> 1; // Rage activation has no cooldown - gated by full rage bar.
            case DRYAD -> 300; // 15 seconds for root snare.
            default -> 0;
        };
    }
}
