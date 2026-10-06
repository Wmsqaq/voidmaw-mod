package com.novapupil.voidmaw.blackhole;

/** Maps a bounded scan cursor onto a full-height column volume without random gaps. */
public final class ColumnSweep {
    public record Cell(int x, int y, int z) {
    }

    private ColumnSweep() {
    }

    public static long size(int width, int bottom, int top) {
        return (long) width * width * Math.max(0L, (long) top - bottom + 1);
    }

    public static Cell cell(long index, int minX, int minZ, int width, int bottom) {
        long plane = (long) width * width;
        int x = (int) (index % width);
        int z = (int) ((index / width) % width);
        int y = bottom + (int) (index / plane);
        return new Cell(minX + x, y, minZ + z);
    }
}
