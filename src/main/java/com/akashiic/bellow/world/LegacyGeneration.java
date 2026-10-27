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
}
