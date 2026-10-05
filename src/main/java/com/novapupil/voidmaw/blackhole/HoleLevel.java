package com.novapupil.voidmaw.blackhole;

/**
 * Absorption levels, Hole.io style: everything is gated by level - pit radius,
 * which blocks/entities can be devoured, and how hard the maw can detonate.
 */
public final class HoleLevel {
    public static final int MIN = 1;
    public static final int MAX = 5;

    /** Mass required to be at each level (index 0 = level 1). Tuned for a slow burn. */
    private static final double[] MASS_THRESHOLDS = {0, 100, 400, 1200, 3000};
    /** Pit radius per level - small steps so leveling does not jolt the size. */
    private static final double[] RADII = {2.5, 3.5, 4.5, 5.5, 7.0};
    /** Blocks harder than this need the next level (obsidian 50, ancient debris 30 -> Lv5). */
    private static final float[] MAX_BLOCK_HARDNESS = {1.0f, 3.0f, 6.0f, 15.0f, Float.MAX_VALUE};
    /** Entities with a larger average bbox dimension need the next level. */
    private static final double[] MAX_ENTITY_SIZE = {0.85, 1.4, 2.2, 3.2, Double.MAX_VALUE};
    /** Detonation power (shift+use), per level. */
    private static final float[] EXPLOSION_POWER = {1.5f, 2.5f, 3.5f, 4.5f, 6.0f};

    private HoleLevel() {
    }

    public static int levelFor(double mass) {
        int level = MIN;
        for (int i = MIN; i <= MAX; i++) {
            if (mass >= MASS_THRESHOLDS[i - 1]) {
                level = i;
            }
        }
        return level;
    }

    public static double radiusFor(int level) {
        return RADII[level - 1];
    }

    public static float maxBlockHardnessFor(int level) {
        return MAX_BLOCK_HARDNESS[level - 1];
    }

    public static double maxEntitySizeFor(int level) {
        return MAX_ENTITY_SIZE[level - 1];
    }

    public static float explosionPowerFor(int level) {
        return EXPLOSION_POWER[level - 1];
    }

    /** Mass still needed for the next level, or -1 at max level. */
    public static double nextLevelMass(int level) {
        return level < MAX ? MASS_THRESHOLDS[level] : -1;
    }
}
