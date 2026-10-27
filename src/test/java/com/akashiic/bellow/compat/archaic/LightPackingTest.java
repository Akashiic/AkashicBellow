package com.akashiic.bellow.compat.archaic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class LightPackingTest {

    @Test
    void roundTripAcrossTheWholeRange() {
        int[] xs = { -30_000_000, -1, 0, 15, 16, 29_999_999 };
        int[] ys = { -512, -65, -64, -1, 0, 1, 255 };
        for (int x : xs) for (int y : ys) for (int z : xs) {
            long k = LightPacking.encode(x, y, z);
            assertEquals(x, LightPacking.decodeX(k));
            assertEquals(y, LightPacking.decodeY(k));
            assertEquals(z, LightPacking.decodeZ(k));
        }
    }

    @Test
    void neighbourShiftsMoveOneBlock() {
        int[][] d = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
        int[][] points = { { 0, 0, 0 }, { -1, -1, -1 }, { 15, -64, 15 }, { -16, -512 + 1, 31 }, { 100, 255, -100 } };
        for (int[] p : points) {
            long k = LightPacking.encode(p[0], p[1], p[2]);
            for (int i = 0; i < 6; i++) {
                long n = k + LightPacking.NEIGHBOR_SHIFTS[i];
                assertEquals(p[0] + d[i][0], LightPacking.decodeX(n));
                assertEquals(p[1] + d[i][1], LightPacking.decodeY(n));
                assertEquals(p[2] + d[i][2], LightPacking.decodeZ(n));
            }
        }
        // below the supported floor decodes far out of range (rejected by the engine's range check)
        long bottom = LightPacking.encode(0, -512, 0) + LightPacking.NEIGHBOR_SHIFTS[0];
        assertNotEquals(-513, LightPacking.decodeY(bottom));
    }

    @Test
    void chunkIdIgnoresYAndLocalCoordinates() {
        assertEquals(
            LightPacking.chunkId(LightPacking.encode(0, -64, 0)),
            LightPacking.chunkId(LightPacking.encode(15, 200, 15)));
        assertNotEquals(
            LightPacking.chunkId(LightPacking.encode(15, 0, 0)),
            LightPacking.chunkId(LightPacking.encode(16, 0, 0)));
        assertNotEquals(
            LightPacking.chunkId(LightPacking.encode(-1, 0, 0)),
            LightPacking.chunkId(LightPacking.encode(0, 0, 0)));
    }
}
