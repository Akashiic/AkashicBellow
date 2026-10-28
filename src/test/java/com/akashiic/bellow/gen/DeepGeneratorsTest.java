package com.akashiic.bellow.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.world.chunk.Chunk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.akashiic.bellow.api.IDeepGenerator;

class DeepGeneratorsTest {

    @AfterEach
    void reset() {
        DeepGenerators.resetForTests();
    }

    @Test
    void runsInRegistrationOrderAndSurvivesAFailingGenerator() {
        List<String> calls = new ArrayList<>();
        DeepGenerators.register("a:first", new IDeepGenerator() {

            @Override
            public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
                calls.add("first");
                throw new IllegalStateException("boom");
            }
        });
        DeepGenerators.register("b:second", new IDeepGenerator() {

            @Override
            public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
                calls.add("second " + minY + " " + retrogen);
            }
        });
        DeepGenerators.generateTerrain(new Chunk(null, 3, 4), -64, 1L, true);
        assertEquals(Arrays.asList("first", "second -64 true"), calls);
        assertEquals(Arrays.asList("a:first", "b:second"), DeepGenerators.ids());
    }

    @Test
    void rejectsDuplicatesAndLateRegistration() {
        IDeepGenerator noop = new IDeepGenerator() {};
        DeepGenerators.register("a:x", noop);
        assertThrows(IllegalArgumentException.class, () -> DeepGenerators.register("a:x", noop));
        assertThrows(IllegalArgumentException.class, () -> DeepGenerators.register("", noop));
        assertThrows(IllegalArgumentException.class, () -> DeepGenerators.register("a:y", null));
        DeepGenerators.freeze();
        assertThrows(IllegalStateException.class, () -> DeepGenerators.register("a:z", noop));
        assertTrue(DeepGenerators.any());
    }

    @Test
    void firstGenerationFreezesTheList() {
        DeepGenerators.generateTerrain(new Chunk(null, 0, 0), -64, 1L, false);
        assertThrows(IllegalStateException.class, () -> DeepGenerators.register("a:late", new IDeepGenerator() {}));
    }
}
