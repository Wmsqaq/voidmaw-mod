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
    private long blockCursor;
    private final HoleProgress progress;
    /** The ItemDisplay that draws the hole disc; spawned and moved by the manager. */
    private ItemDisplayEntity discVisual;
    /** Diameter currently applied to the disc, so level-ups animate once. */
    private float discScale = 1.0f;

    BlackHoleState() {
        this(null);
    }

    BlackHoleState(HoleProgress progress) {
        this.progress = progress;
        this.mass = progress == null ? 0.0 : progress.mass();
        this.level = HoleLevel.levelFor(mass);
        this.ticksLeft = Balance.BASE_DURATION_TICKS;
    }

    long nextSweepIndex(long count) {
        long index = blockCursor % count;
        blockCursor = (index + 1) % count;
        return index;
    }

    void retainProgress() {
        if (progress != null) {
            progress.retain(mass);
            progress.flush();
        }
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
        if (!Double.isFinite(amount) || amount <= 0) {
            return;
        }
        mass = Math.min(Double.MAX_VALUE, mass + amount);
        if (progress != null) {
            progress.retain(mass);
        }
        ticksLeft = (int) Math.min(ticksLeft + Math.min(amount * Balance.TICKS_PER_MASS,
                Balance.MAX_DURATION_TICKS), Balance.MAX_DURATION_TICKS);
    }

    void tick() {
        ticksLeft--;
    }

    boolean expired() {
        return ticksLeft <= 0;
    }
}
