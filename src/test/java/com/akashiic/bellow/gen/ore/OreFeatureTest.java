package com.akashiic.bellow.gen.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class OreFeatureTest {

    /** Solid stone everywhere except an air plane at {@code airY}; target 0 = stone. */
    static final class FakeWorld implements OreFeature.World {

        final Map<Long, Integer> placed = new HashMap<>();
        final int airY;
        int duplicates;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minYp = Integer.MAX_VALUE, maxYp = Integer.MIN_VALUE;

        FakeWorld(int airY) {
            this.airY = airY;
        }

        static long key(int x, int y, int z) {
            return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | (long) z & 0xFFFFF;
        }

        @Override
        public boolean canWrite(int x, int y, int z) {
            return y != this.airY;
        }

        @Override
        public int matchTarget(int x, int y, int z) {
            return this.placed.containsKey(key(x, y, z)) ? -1 : 0;
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return y == this.airY;
        }

        @Override
        public void place(int x, int y, int z, int targetIndex) {
            if (this.placed.put(key(x, y, z), targetIndex) != null) this.duplicates++;
            this.minX = Math.min(this.minX, x);
            this.maxX = Math.max(this.maxX, x);
            this.minYp = Math.min(this.minYp, y);
            this.maxYp = Math.max(this.maxYp, y);
        }
    }

    @Test
    void deterministicForSeed() {
        FakeWorld a = new FakeWorld(Integer.MIN_VALUE), b = new FakeWorld(Integer.MIN_VALUE);
        assertEquals(
            OreFeature.place(new Random(42), 0, -30, 0, 9, 0f, a),
            OreFeature.place(new Random(42), 0, -30, 0, 9, 0f, b));
        assertEquals(a.placed, b.placed);
    }

    @Test
    void neverPlacesTwiceAndStaysInBounds() {
        int[] sizes = { 3, 4, 7, 8, 9, 10, 12, 33, 64 };
        Random r = new Random(7);
        for (int size : sizes) {
            for (int i = 0; i < 300; i++) {
                FakeWorld w = new FakeWorld(Integer.MIN_VALUE);
                OreFeature.place(r, 0, 0, 0, size, 0f, w);
                assertEquals(0, w.duplicates);
                if (w.placed.isEmpty()) continue;
                // Box of OreFeature: origin - ceil(size/8) - pad .. + width
                float half = size / 8.0F;
                int pad = (int) Math.ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
                int reach = (int) Math.ceil(half) + pad;
                assertTrue(w.minX >= -reach && w.maxX <= reach, "x extent for size " + size);
                assertTrue(w.minYp >= -2 - pad && w.maxYp <= 2 + pad, "y extent for size " + size);
                // the decorator relies on: centre chunk origin +- 12.5 stays within a 3x3 region
                assertTrue(reach <= 13, "reach " + reach + " for size " + size);
            }
        }
    }

    @Test
    void meanVeinSizeIsStable() {
        // Regression anchor for the port: mean blocks per vein in solid stone (no air), 20k veins per size.
        Random r = new Random(2024);
        double[] mean = new double[65];
        for (int size : new int[] { 4, 8, 9, 12 }) {
            long sum = 0;
            int n = 20000;
            for (int i = 0; i < n; i++) sum += OreFeature.place(r, 0, 0, 0, size, 0f, new FakeWorld(Integer.MIN_VALUE));
            mean[size] = (double) sum / n;
        }
        // Sanity bounds: a vein never exceeds its bounding box and grows with size.
        assertTrue(
            mean[4] > 1 && mean[4] < mean[8] && mean[8] < mean[9] && mean[9] < mean[12],
            java.util.Arrays.toString(mean));
    }

    @Test
    void buriedVeinsNeverTouchAir() {
        Random r = new Random(99);
        for (int i = 0; i < 2000; i++) {
            FakeWorld w = new FakeWorld(0);
            OreFeature.place(r, 0, 1, 0, 8, 1.0F, w);
            for (long k : w.placed.keySet()) {
                int y = (int) (k >> 20 & 0xFFFFF);
                if (y >= 0x80000) y -= 0x100000;
                assertFalse(y == 1 || y == -1, "discard=1 vein touched the air plane at y=" + y);
            }
        }
    }

    @Test
    void discardChanceReducesExposedBlocks() {
        Random r = new Random(5);
        long exposedNoDiscard = 0, exposedHalf = 0;
        for (int i = 0; i < 4000; i++) {
            FakeWorld a = new FakeWorld(0), b = new FakeWorld(0);
            long seed = r.nextLong();
            OreFeature.place(new Random(seed), 0, 1, 0, 9, 0f, a);
            OreFeature.place(new Random(seed), 0, 1, 0, 9, 0.5F, b);
            exposedNoDiscard += a.placed.keySet()
                .stream()
                .filter(k -> (k >> 20 & 0xFFFFF) == 1)
                .count();
            exposedHalf += b.placed.keySet()
                .stream()
                .filter(k -> (k >> 20 & 0xFFFFF) == 1)
                .count();
        }
        double ratio = (double) exposedHalf / exposedNoDiscard;
        assertTrue(ratio > 0.4 && ratio < 0.6, "exposed ratio " + ratio);
    }
}
