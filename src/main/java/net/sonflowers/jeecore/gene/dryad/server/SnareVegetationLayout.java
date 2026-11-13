package net.sonflowers.jeecore.gene.dryad.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Defines stable woodland patch placement independently of the server terrain reveal schedule.
 */
final class SnareVegetationLayout {
    enum Species { TALL_GRASS, FERN, SHORT_GRASS, TULIP, DAISY, DANDELION, ROSE_BUSH, PEONY, LILAC }

    record Plant(Species species, float scale, float yaw, double offsetX, double offsetZ,
                 float phase, float overshoot, float windDirection) {}

    private record Patch(double x, double z, double radius, float fernBias, float heightBias, float phase) {}

    private final long seed;
    private final float radius;
    private final List<Patch> patches;
    private final float windDirection;

    SnareVegetationLayout(long seed, float radius) {
        this.seed = seed;
        this.radius = Math.max(1, radius);
        Random random = new Random(seed);
        windDirection = random.nextFloat() * (float) Math.PI * 2;
        List<Patch> groups = new ArrayList<>(7);
        groups.add(patch(random, 0, 0, 1.6));
        double rotation = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 6; i++) {
            double angle = rotation + (i + (random.nextDouble() - 0.5) * 0.3) * Math.PI / 3;
            double distance = 2.3 + random.nextDouble() * 0.7;
            groups.add(patch(random, Math.cos(angle) * distance, Math.sin(angle) * distance,
                    1.65 + random.nextDouble() * 0.4));
        }
        patches = List.copyOf(groups);
    }

    private static Patch patch(Random random, double x, double z, double radius) {
        return new Patch(x, z, radius, 0.28f + random.nextFloat() * 0.42f,
                0.92f + random.nextFloat() * 0.16f, random.nextFloat() * (float) Math.PI * 2);
    }

    Plant at(int x, int z) {
        Patch nearest = null;
        double normalized = Double.POSITIVE_INFINITY;
        for (Patch patch : patches) {
            double distance = Math.hypot(x - patch.x(), z - patch.z()) / patch.radius();
            if (distance < normalized) {
                nearest = patch;
                normalized = distance;
            }
        }
        Random random = new Random(seed ^ x * 73428767L ^ z * 912271L);
        float edge = (float) Math.min(1, normalized);
        float radialEdge = (float) Math.min(1, Math.hypot(x, z) / radius);
        float coverage = 0.97f - edge * 0.18f;
        if ((x != 0 || z != 0) && random.nextFloat() >= coverage) return null;
        float flowerRoll = random.nextFloat();
        float tallFlowerChance = 0.075f - radialEdge * 0.055f;
        Species species;
        if (flowerRoll < tallFlowerChance) {
            species = switch (random.nextInt(3)) {
                case 0 -> Species.ROSE_BUSH;
                case 1 -> Species.PEONY;
                default -> Species.LILAC;
            };
        } else if (flowerRoll < tallFlowerChance + 0.10f) {
            species = switch (random.nextInt(3)) {
                case 0 -> Species.TULIP;
                case 1 -> Species.DAISY;
                default -> Species.DANDELION;
            };
        } else {
            float lowChance = 0.06f + radialEdge * 0.22f;
            species = random.nextFloat() < lowChance ? Species.SHORT_GRASS
                    : random.nextFloat() < nearest.fernBias() ? Species.FERN : Species.TALL_GRASS;
        }
        float scale = switch (species) {
            case TALL_GRASS -> 0.53f + random.nextFloat() * 0.18f;
            case FERN -> 0.72f + random.nextFloat() * 0.27f;
            case SHORT_GRASS -> 0.45f + random.nextFloat() * 0.23f;
            case TULIP, DAISY, DANDELION -> 0.62f + random.nextFloat() * 0.20f;
            case ROSE_BUSH, PEONY, LILAC -> 0.58f + random.nextFloat() * 0.20f;
        };
        // Layer the whole radius, not just the edge of each fern patch.
        scale *= nearest.heightBias() * (1.10f - radialEdge * 0.30f) * (1 - edge * 0.05f);
        float yaw = windDirection * 180 / (float) Math.PI + random.nextFloat() * 90;
        double offsetAngle = random.nextDouble() * Math.PI * 2;
        double offsetRadius = 0.12 + random.nextDouble() * 0.18;
        return new Plant(species, scale, yaw, Math.cos(offsetAngle) * offsetRadius,
                Math.sin(offsetAngle) * offsetRadius, nearest.phase() + random.nextFloat() * 0.3f,
                1.075f + random.nextFloat() * 0.035f, windDirection - yaw * (float) Math.PI / 180);
    }
}
