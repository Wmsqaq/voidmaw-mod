package com.novapupil.voidmaw.blackhole;

/**
 * Every gameplay number lives here so the whole mod can be re-balanced in one place.
 */
public final class Balance {
    private Balance() {
    }

    /** Transformation duration: starts at BASE, each mass point extends it, capped. */
    public static final int BASE_DURATION_TICKS = 20 * 60;
    public static final int TICKS_PER_MASS = 10;
    public static final int MAX_DURATION_TICKS = 20 * 300;

    /** Entity suction: velocity is SET directly each tick (not accumulated) so
     * ground friction can never eat the pull. */
    public static final double PULL_SPEED_BASE = 0.2;
    public static final double PULL_SPEED_PER_LEVEL = 0.05;
    /** Anything whose distance squared to the core is below this gets swallowed. */
    public static final double DEVOUR_DISTANCE_SQ = 1.4 * 1.4;

    /** Block devouring: random column samples every few ticks. */
    public static final int BASE_BLOCK_ATTEMPTS = 1;
    public static final int MAX_BLOCK_ATTEMPTS = 6;

    /** Hand every player one Singularity Core the first time they enter the world. */
    public static final boolean GIVE_CORE_ON_FIRST_JOIN = true;

    public static double pullSpeedFor(int level) {
        return PULL_SPEED_BASE + level * PULL_SPEED_PER_LEVEL;
    }

    public static int blockAttemptsFor(double mass) {
        return (int) Math.min(BASE_BLOCK_ATTEMPTS + mass / 40.0, MAX_BLOCK_ATTEMPTS);
    }
}
