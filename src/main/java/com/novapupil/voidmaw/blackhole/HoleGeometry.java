package com.novapupil.voidmaw.blackhole;

public final class HoleGeometry {
    private HoleGeometry() {
    }

    public static boolean contains(double dx, double dz, double bottomY, double mouthY, double radius) {
        return bottomY >= mouthY - 0.05 && dx * dx + dz * dz <= radius * radius;
    }

    public static int firstBlockY(double mouthY) {
        return (int) Math.ceil(mouthY - 1.0e-6);
    }

    public static double suctionY(double entityY, double mouthY, boolean grounded) {
        if (grounded) {
            return 0.12;
        }
        return Math.max(-0.35, Math.min(0.12, (mouthY + 0.15 - entityY) * 0.15));
    }
}
