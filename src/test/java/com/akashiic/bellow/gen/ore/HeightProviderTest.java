package com.akashiic.bellow.gen.ore;

import static com.akashiic.bellow.gen.ore.Anchor.aboveBottom;
import static com.akashiic.bellow.gen.ore.Anchor.absolute;
import static com.akashiic.bellow.gen.ore.Anchor.bottom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class HeightProviderTest {

    private static void assertDistribution(HeightProvider h, int minY) {
        int lo = h.minInclusive(minY), hi = h.maxInclusive(minY);
        double total = 0;
        for (int y = lo - 5; y <= hi + 5; y++) total += h.probability(y, minY);
        assertEquals(1.0, total, 1e-9, "probability mass of " + h);

        int n = 2_000_000;
        long[] counts = new long[hi - lo + 1];
        Random r = new Random(1234);
        for (int i = 0; i < n; i++) {
            int y = h.sample(r, minY);
            assertTrue(y >= lo && y <= hi, h + " sampled " + y);
            counts[y - lo]++;
        }
        // Pearson chi-square against the analytic pmf; df = bins - 1. 5 sigma bound of chi2(df).
        double chi2 = 0;
        for (int y = lo; y <= hi; y++) {
            double e = n * h.probability(y, minY);
            chi2 += (counts[y - lo] - e) * (counts[y - lo] - e) / e;
        }
        int df = counts.length - 1;
        assertTrue(chi2 < df + 5 * Math.sqrt(2.0 * df), h + " chi2=" + chi2 + " df=" + df);
    }

    @Test
    void uniformMatchesMinecraft() {
        assertDistribution(HeightProvider.uniform(bottom(), absolute(15)), -64); // ore_redstone: -64..15
        assertDistribution(HeightProvider.uniform(absolute(-64), absolute(-48)), -64); // ore_gold_lower
    }

    @Test
    void trapezoidMatchesMinecraft() {
        // ore_diamond: triangle(aboveBottom(-80), aboveBottom(80)) -> [-144, 16], mode -64
        HeightProvider diamond = HeightProvider.triangle(aboveBottom(-80), aboveBottom(80));
        assertEquals(-144, diamond.minInclusive(-64));
        assertEquals(16, diamond.maxInclusive(-64));
        assertDistribution(diamond, -64);
        // symmetric triangle: P(-64) is the maximum, 1/81 for k = 160 (l = 80, m = 80)
        assertEquals(1.0 / 81.0, diamond.probability(-64, -64), 1e-12);
        assertEquals(diamond.probability(-64 - 10, -64), diamond.probability(-64 + 10, -64), 1e-12);
        // odd span: iron_middle [-24, 56], k = 80
        assertDistribution(HeightProvider.triangle(absolute(-24), absolute(56)), -64);
        assertDistribution(HeightProvider.triangle(absolute(-15), absolute(16)), -64); // k odd = 31
    }

    @Test
    void anchorsFollowTheFloor() {
        HeightProvider redstoneLower = HeightProvider.triangle(aboveBottom(-32), aboveBottom(32));
        assertEquals(-96, redstoneLower.minInclusive(-64));
        assertEquals(-544, redstoneLower.minInclusive(-512));
        assertEquals(-480, redstoneLower.maxInclusive(-512));
        assertTrue(
            HeightProvider.uniform(bottom(), absolute(72))
                .isBottomAnchoredUniform());
        assertTrue(
            !HeightProvider.uniform(absolute(-64), absolute(-4))
                .isBottomAnchoredUniform());
    }
}
