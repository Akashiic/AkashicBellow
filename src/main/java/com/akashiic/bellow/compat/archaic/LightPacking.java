package com.akashiic.bellow.compat.archaic;

/**
 * Position packing of {@link DeepPhosphorEngine}:
 * bits 0..25 {@code z + 2^25}, bits 26..51 {@code x + 2^25}, bits 52..61 {@code y + 512}.
 */
public final class LightPacking {

    static final int Y_OFFSET = 512;
    static final long XZ_OFFSET = 1L << 25;
    static final long M26 = (1L << 26) - 1;
    static final long M10 = (1L << 10) - 1;
    /** Bits that identify a chunk column: {@code (x + 2^25) >> 4} and {@code (z + 2^25) >> 4}. */
    static final long CHUNK_MASK = (M26 & ~15L) << 26 | M26 & ~15L;
    /** Down, up, north, south, west, east. */
    static final long[] NEIGHBOR_SHIFTS = { -(1L << 52), 1L << 52, -1L, 1L, -(1L << 26), 1L << 26 };

    private LightPacking() {}

    public static long encode(int x, int y, int z) {
        return (long) (y + Y_OFFSET) << 52 | (x + XZ_OFFSET) << 26 | z + XZ_OFFSET;
    }

    public static int decodeX(long key) {
        return (int) ((key >> 26 & M26) - XZ_OFFSET);
    }

    public static int decodeY(long key) {
        return (int) ((key >> 52 & M10) - Y_OFFSET);
    }

    public static int decodeZ(long key) {
        return (int) ((key & M26) - XZ_OFFSET);
    }

    public static long chunkId(long key) {
        return key & CHUNK_MASK;
    }
}
