package com.novapupil.voidmaw.blackhole;

import net.minecraft.entity.FallingBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player black hole state: mass, absorption level and blocks still tumbling into the pit.
 */
public final class BlackHoleState {
    private double mass;
    private int ticksLeft;
    private int level = HoleLevel.MIN;
    /** Ground level the pit mouth is anchored to (latched, not the live player Y). */
    private double mouthY;
    /** Golden-angle sweep cursor so block sampling covers the disc evenly. */
    private double sweepAngle;
    private final List<FallingBlockEntity> pendingBlocks = new ArrayList<>();

    BlackHoleState() {
        this.ticksLeft = Balance.BASE_DURATION_TICKS;
    }

    double nextSweepAngle() {
        sweepAngle += 2.399963;
        return sweepAngle;
    }

    public double mouthY() {
        return mouthY;
    }

    void setMouthY(double mouthY) {
        this.mouthY = mouthY;
    }

    public double mass() {
        return mass;
    }

    public int level() {
        return level;
    }

    void setLevel(int level) {
        this.level = level;
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public List<FallingBlockEntity> pendingBlocks() {
        return pendingBlocks;
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
