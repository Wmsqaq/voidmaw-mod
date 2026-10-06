package com.novapupil.voidmaw.blackhole;

import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;

/**
 * Per-player black hole state: mass, absorption level and the display entity that
 * draws the flat hole disc at the player's feet.
 */
public final class BlackHoleState {
    private double mass;
    private int ticksLeft;
    private int level = HoleLevel.MIN;
    /** Mouth plane follows the player's feet, including slabs and stairs. */
    private double mouthY;
    /** Golden-angle sweep cursor so block sampling covers the disc evenly. */
    private double sweepAngle;
    /** The ItemDisplay that draws the hole disc; spawned and moved by the manager. */
    private ItemDisplayEntity discVisual;
    /** Diameter currently applied to the disc, so level-ups animate once. */
    private float discScale = 1.0f;

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

    public ItemDisplayEntity discVisual() {
        return discVisual;
    }

    void setDiscVisual(ItemDisplayEntity discVisual) {
        this.discVisual = discVisual;
    }

    public float discScale() {
        return discScale;
    }

    void setDiscScale(float discScale) {
        this.discScale = discScale;
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
