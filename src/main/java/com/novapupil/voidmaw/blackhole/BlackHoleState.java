package com.novapupil.voidmaw.blackhole;

/**
 * Per-player black hole state. Mass feeds both size and remaining time.
 */
public final class BlackHoleState {
    private double mass;
    private int ticksLeft;

    BlackHoleState() {
        this.ticksLeft = Balance.BASE_DURATION_TICKS;
    }

    public double mass() {
        return mass;
    }

    public double radius() {
        return Balance.radiusFor(mass);
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    void addMass(double amount) {
        mass += amount;
        ticksLeft = (int) Math.min(ticksLeft + Math.round(amount * Balance.TICKS_PER_MASS),
                Balance.MAX_DURATION_TICKS);
    }

    void tick() {
        ticksLeft--;
    }

    boolean expired() {
        return ticksLeft <= 0;
    }
}
