package com.novapupil.voidmaw.blackhole;

import net.minecraft.util.math.MathHelper;

/**
 * Pure geometry for the chunk-section based disc sweep: which chunks the disc covers,
 * a rotating cursor over that chunk grid, and the in-circle row bounds per chunk column.
 */
public final class DiscSweep {
    /** Inclusive local-z range inside the disc for one chunk column; start > end means no cells. */
    public record Row(int start, int endInclusive) {
        public boolean empty() {
            return start > endInclusive;
        }
    }

    private DiscSweep() {
    }

    public static int minChunk(double center, double radius) {
        return chunkOf(center - radius);
    }

    public static int maxChunk(double center, double radius) {
        return chunkOf(center + radius);
    }

    public static int chunkOf(double world) {
        return Math.floorDiv(MathHelper.floor(world), 16);
    }

    public static long volume(int minCx, int maxCx, int minCz, int maxCz) {
        return (long) (maxCx - minCx + 1) * (maxCz - minCz + 1);
    }

    public static int chunkX(long index, int minCx, int zSpan) {
        return minCx + (int) Math.floorDiv(index, zSpan);
    }

    public static int chunkZ(long index, int minCz, int zSpan) {
        return minCz + (int) Math.floorMod(index, zSpan);
    }

    /**
     * Local z cells (0-15) of chunk column {@code localX} that fall inside the disc,
     * treating each cell center as its sample point. Slightly generous at the rim.
     * The x distance uses the chunk's X coord while the z bounds are relative to the
     * chunk's Z coord - mixing them up collapses the disc into a line along X.
     */
    public static Row rowRange(int chunkX, int chunkZ, int localX, double centerX, double centerZ, double radius) {
        double dx = (chunkX << 4) + localX + 0.5 - centerX;
        double rest = radius * radius - dx * dx;
        if (rest <= 0.0) {
            return new Row(1, 0);
        }
        double half = Math.sqrt(rest);
        int baseZ = chunkZ << 4;
        int start = MathHelper.clamp(MathHelper.floor(centerZ - half) - baseZ, 0, 15);
        int end = MathHelper.clamp(MathHelper.ceil(centerZ + half) - baseZ, 0, 15);
        return new Row(start, end);
    }
}
