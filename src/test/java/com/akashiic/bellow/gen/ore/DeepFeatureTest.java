package com.akashiic.bellow.gen.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

class DeepFeatureTest {

    @Test
    void defaultsConsumeNoExtraRandom() {
        DeepFeature redstone = DeepFeature
            .ore("r", "minecraft:redstone_ore", 8, 0f, 4, HeightProvider.uniform(Anchor.bottom(), Anchor.absolute(15)));
        Random a = new Random(1), b = new Random(1);
        assertEquals(4, redstone.attempts(a, -64, true, 1.0));
        assertEquals(b.nextLong(), a.nextLong(), "constant count must not consume the stream");
    }

    @Test
    void bottomAnchoredDensityIsPreserved() {
        DeepFeature redstone = DeepFeature
            .ore("r", "minecraft:redstone_ore", 8, 0f, 4, HeightProvider.uniform(Anchor.bottom(), Anchor.absolute(15)));
        // span -512..15 = 528 vs -64..15 = 80
        assertEquals(528.0 / 80.0, redstone.densityScale(-512, true), 1e-12);
        assertEquals(1.0, redstone.densityScale(-512, false), 1e-12);
        // and for shallower floors: span -16..15 = 32 vs 80
        assertEquals(32.0 / 80.0, redstone.densityScale(-16, true), 1e-12);
        assertEquals(1.0, redstone.densityScale(-64, true), 1e-12);
        Random r = new Random(3);
        long sum = 0;
        int n = 200000;
        for (int i = 0; i < n; i++) sum += redstone.attempts(r, -512, true, 1.0);
        assertEquals(4 * 528.0 / 80.0, (double) sum / n, 0.02);
    }

    @Test
    void rarityAndUniformCount() {
        DeepFeature large = DeepFeature
            .ore("d", "minecraft:diamond_ore", 12, 0.7f, 1, HeightProvider.uniform(Anchor.bottom(), Anchor.absolute(0)))
            .withRarity(9);
        DeepFeature goldLower = DeepFeature
            .ore(
                "g",
                "minecraft:gold_ore",
                9,
                0.5f,
                0,
                HeightProvider.uniform(Anchor.absolute(-64), Anchor.absolute(-48)))
            .withCountRange(0, 1);
        Random r = new Random(11);
        long l = 0, g = 0;
        int n = 900000;
        for (int i = 0; i < n; i++) {
            l += large.attempts(r, -64, true, 1.0);
            g += goldLower.attempts(r, -64, true, 1.0);
        }
        assertEquals(1.0 / 9.0, (double) l / n, 0.002);
        assertEquals(0.5, (double) g / n, 0.003);
        assertEquals(1.0 / 9.0, large.expectedAttempts(-64, true, 1.0), 1e-12);
    }
}
