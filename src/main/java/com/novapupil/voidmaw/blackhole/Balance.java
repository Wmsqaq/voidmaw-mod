package com.novapupil.voidmaw.blackhole;

/**
 * Every gameplay number lives here so the whole mod can be re-balanced in one place.
 */
public final class Balance {
    private Balance() {
    }

    /** Pull radius: radius = BASE + sqrt(mass) * PER_SQRT_MASS, hard-capped. */
    public static final double BASE_RADIUS = 3.0;
    public static final double RADIUS_PER_SQRT_MASS = 1.2;
    public static final double MAX_RADIUS = 16.0;

    /** Transformation duration: starts at BASE, each mass point extends it, capped. */
    public static final int BASE_DURATION_TICKS = 20 * 60;
    public static final int TICKS_PER_MASS = 10;
    public static final int MAX_DURATION_TICKS = 20 * 300;

    /** Entity suction: strength grows with mass and with proximity to the core. */
    public static final double PULL_BASE = 0.06;
    public static final double PULL_PER_SQRT_MASS = 0.004;
    /** Anything whose distance squared to the core is below this gets swallowed. */
    public static final double DEVOUR_DISTANCE_SQ = 1.4 * 1.4;

    /** Block devouring: random sample attempts every other tick. */
    public static final int BASE_BLOCK_ATTEMPTS = 2;
    public static final int MAX_BLOCK_ATTEMPTS = 10;
    /** Mass required before the maw can chew blocks harder than 10 (obsidian & co). */
    public static final double HARD_BLOCK_MASS_GATE = 30.0;
    public static final float HARD_BLOCK_HARDNESS = 10.0f;

    /** End-of-transformation energy release. */
    public static final float EXPLOSION_BASE = 2.0f;
    public static final float EXPLOSION_PER_MASS = 0.1f;
    public static final float EXPLOSION_CAP = 6.0f;

    public static double radiusFor(double mass) {
        return Math.min(BASE_RADIUS + Math.sqrt(mass) * RADIUS_PER_SQRT_MASS, MAX_RADIUS);
    }

    public static float explosionPowerFor(double mass) {
        return (float) Math.min(EXPLOSION_BASE + mass * EXPLOSION_PER_MASS, EXPLOSION_CAP);
    }

    public static double pullStrengthFor(double mass) {
        return PULL_BASE + Math.sqrt(mass) * PULL_PER_SQRT_MASS;
    }

    public static int blockAttemptsFor(double mass) {
        return (int) Math.min(BASE_BLOCK_ATTEMPTS + mass / 20.0, MAX_BLOCK_ATTEMPTS);
    }
}
