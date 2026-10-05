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

    /** Block devouring: random column samples every few ticks. */
    public static final int BASE_BLOCK_ATTEMPTS = 1;
    public static final int MAX_BLOCK_ATTEMPTS = 6;

    /** Hand every player one Singularity Core the first time they enter the world. */
    public static final boolean GIVE_CORE_ON_FIRST_JOIN = true;

    public static double pullStrengthFor(double mass) {
        return PULL_BASE + Math.sqrt(mass) * PULL_PER_SQRT_MASS;
    }

    public static int blockAttemptsFor(double mass) {
        return (int) Math.min(BASE_BLOCK_ATTEMPTS + mass / 40.0, MAX_BLOCK_ATTEMPTS);
    }
}
