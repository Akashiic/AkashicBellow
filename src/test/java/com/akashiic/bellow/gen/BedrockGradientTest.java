package com.akashiic.bellow.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BedrockGradientTest {

    @Test
    void matchesVerticalGradient() {
        assertEquals(1.0f, DeepTerrain.bedrockChance(-64, -64));
        assertEquals(0.8f, DeepTerrain.bedrockChance(-63, -64), 1e-6);
        assertEquals(0.6f, DeepTerrain.bedrockChance(-62, -64), 1e-6);
        assertEquals(0.4f, DeepTerrain.bedrockChance(-61, -64), 1e-6);
        assertEquals(0.2f, DeepTerrain.bedrockChance(-60, -64), 1e-6);
        assertEquals(0.0f, DeepTerrain.bedrockChance(-59, -64));
        assertEquals(0.0f, DeepTerrain.bedrockChance(-1, -64));
    }

    @Test
    void positionalRandomIsUniformAndOrderFree() {
        long seed = 123456789L;
        int n = 0;
        double sum = 0;
        int[] bins = new int[10];
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) for (int y = -64; y < -48; y++) {
            float f = PositionalRandom.nextFloat(seed, x, y, z);
            sum += f;
            bins[(int) (f * 10)]++;
            n++;
        }
        assertEquals(0.5, sum / n, 0.01);
        for (int b : bins) assertEquals(n / 10.0, b, n / 10.0 * 0.05);
        assertEquals(PositionalRandom.nextFloat(seed, 5, -60, 7), PositionalRandom.nextFloat(seed, 5, -60, 7));
    }

    @Test
    void floorFrequencies() {
        long seed = 42;
        int[] count = new int[6];
        int columns = 0;
        for (int x = -200; x < 200; x++) for (int z = -200; z < 200; z++) {
            columns++;
            for (int k = 0; k <= 5; k++) if (DeepTerrain.isBedrock(seed, x, -64 + k, z, -64)) count[k]++;
        }
        assertEquals(columns, count[0]);
        assertEquals(0.8, count[1] / (double) columns, 0.005);
        assertEquals(0.6, count[2] / (double) columns, 0.005);
        assertEquals(0.4, count[3] / (double) columns, 0.005);
        assertEquals(0.2, count[4] / (double) columns, 0.005);
        assertEquals(0, count[5]);
    }
}
