package com.novapupil.voidmaw.blackhole;

/** Absorption keeps growing beyond the original five introductory levels. */
public final class HoleLevel {
    public static final int MIN = 1;
    private static final double[] MASS_THRESHOLDS = {0, 100, 400, 1200, 3000};
    private static final double[] RADII = {2.5, 3.5, 4.5, 5.5, 7.0};
    private static final double[] MAX_ENTITY_SIZE = {0.85, 1.4, 2.2, 3.2, Double.MAX_VALUE};

    private HoleLevel() {
    }

    public static int levelFor(double mass) {
        if (!Double.isFinite(mass) || mass < 0) {
            return MIN;
        }
        int low = MIN;
        int high = Integer.MAX_VALUE;
        while (low < high) {
            int middle = low + (int) (((long) high - low + 1) / 2);
            if (levelThreshold(middle) <= mass) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    public static double radiusFor(int level) {
        if (level <= 5) {
            return RADII[Math.max(MIN, level) - 1];
        }
        return 7.0 + 2.0 * Math.log1p((double) level - 5);
    }

    public static double maxEntitySizeFor(int level) {
        return MAX_ENTITY_SIZE[Math.clamp(level, MIN, 5) - 1];
    }

    public static float explosionPowerFor(int level) {
        return (float) (3.0 + 0.75 * Math.pow(Math.max(0.0, (double) level - 1), 0.75));
    }

    /** Detonation releases the level's floor plus the actually stored mass, capped
     * so late-game blasts stay server-friendly while always feeling heavy. */
    public static float detonationPowerFor(int level, double mass) {
        float stored = (float) Math.min(Math.sqrt(Math.max(0.0, mass)) * 0.08, 12.0);
        return Math.min(explosionPowerFor(level) + stored, 24.0f);
    }

    public static double levelThreshold(int level) {
        if (level <= 5) {
            return MASS_THRESHOLDS[Math.max(MIN, level) - 1];
        }
        double beyond = (double) level - 5;
        return 3000.0 + 1800.0 * beyond + 900.0 * beyond * beyond;
    }

    public static double nextLevelMass(int level) {
        return levelThreshold(level == Integer.MAX_VALUE ? level : level + 1);
    }
}
