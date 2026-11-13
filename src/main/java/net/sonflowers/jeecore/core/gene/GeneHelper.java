package net.sonflowers.jeecore.core.gene;

import net.minecraft.world.entity.player.Player;

/**
 * Reads and changes player gene attachments, including selection and reset state.
 */
public class GeneHelper {
    public static Gene getPlayerGene(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        return data.getGene();
    }

    public static void setPlayerGene(Player player, Gene gene) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        data.resetAbilityState();
        data.setGene(gene);
    }

    public static boolean hasChosen(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        return data.hasChosen();
    }

    public static void resetGene(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        data.reset();
    }
}
