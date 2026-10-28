package com.akashiic.bellow.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.akashiic.bellow.gen.ore.OreFeature;

class DeepDecoratorTest {

    @Test
    void veinReachMatchesThePlacementBox() {
        // OreFeature's box: y - 2 - pad .. y + 2 + pad, pad = ceil((size / 8 + 1) / 2)
        assertEquals(3, OreFeature.verticalReach(1));
        assertEquals(4, OreFeature.verticalReach(9));
        assertEquals(5, OreFeature.verticalReach(33));
        assertEquals(7, OreFeature.verticalReach(64));
    }

    @Test
    void originsAboveZeroThatReachBelowAreKept() {
        // 1.18 places every origin and clips blocks: a size-9 vein at Y 3 reaches Y -1
        assertTrue(DeepDecorator.reachesDeepBand(3, 9, -64));
        assertFalse(DeepDecorator.reachesDeepBand(4, 9, -64));
        assertTrue(DeepDecorator.reachesDeepBand(0, 64, -64));
        assertTrue(DeepDecorator.reachesDeepBand(6, 64, -64));
        assertFalse(DeepDecorator.reachesDeepBand(7, 64, -64));
        // and below the floor only veins that still reach it
        assertTrue(DeepDecorator.reachesDeepBand(-68, 9, -64));
        assertFalse(DeepDecorator.reachesDeepBand(-69, 9, -64));
    }
}
