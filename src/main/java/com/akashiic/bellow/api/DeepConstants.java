package com.akashiic.bellow.api;

public final class DeepConstants {

    /** Deepest floor supported by storage, network and lighting encodings. */
    public static final int ABSOLUTE_MIN_Y = -512;

    /** Number of deep section slots per chunk ({@code -ABSOLUTE_MIN_Y / 16}). */
    public static final int MAX_DEEP_SECTIONS = 32;

    public static final byte GEN_NONE = 0;
    public static final byte GEN_TERRAIN = 1;
    public static final byte GEN_DECORATED = 2;

    private DeepConstants() {}

    /**
     * Slot of a negative section index {@code sy = y >> 4} in the deep array, or -1 when it is out of storage range.
     */
    public static int slot(int sectionY) {
        int slot = sectionY + MAX_DEEP_SECTIONS;
        return slot >= 0 && sectionY < 0 ? slot : -1;
    }

    /** Bit of a section in a slot mask ({@link BellowAPI#resendDeepSections}), 0 when it is out of storage range. */
    public static int slotBit(int sectionY) {
        int slot = slot(sectionY);
        return slot < 0 ? 0 : 1 << slot;
    }

    /** Rounds a configured floor down to a multiple of 16 and clamps it to the supported range. */
    public static int sanitizeMinY(int minY) {
        if (minY >= 0) return 0;
        int floored = minY & ~15;
        return Math.max(floored, ABSOLUTE_MIN_Y);
    }
}
