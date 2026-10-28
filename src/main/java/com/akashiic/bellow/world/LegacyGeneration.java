package com.akashiic.bellow.world;

/**
 * Marks the legacy population phase ({@code Chunk.populateChunk}: the vanilla decorator and every mod's
 * {@code IWorldGenerator}). Those generators were written for a world that ends at Y 0, where a write below 0 is a
 * no-op; ore veins and blobs drawn near the bottom relied on that clipping. While this phase runs, World writes below
 * Y 0 keep the 1.7.10 behaviour, so the deep layer only receives what the deep generator places (which writes the
 * sections directly and is not affected).
 */
public final class LegacyGeneration {

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private LegacyGeneration() {}

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        DEPTH.get()[0]--;
    }

    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }

    /**
     * Leaves the phase for code that owns the deep layer (deep generators called from a chunk load that happens inside
     * another chunk's population); returns what {@link #resume} needs.
     */
    public static int suspend() {
        int[] depth = DEPTH.get();
        int previous = depth[0];
        depth[0] = 0;
        return previous;
    }

    public static void resume(int previous) {
        DEPTH.get()[0] = previous;
    }
}
