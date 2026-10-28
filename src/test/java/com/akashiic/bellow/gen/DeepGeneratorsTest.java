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

    private static IDeepGenerator recording(List<String> calls, String name) {
        return new IDeepGenerator() {

            @Override
            public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
                calls.add(name);
            }
        };
    }

    @Test
    void survivesAFailingGenerator() {
        List<String> calls = new ArrayList<>();
        DeepGenerators.register("a:first", new IDeepGenerator() {

            @Override
            public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
                calls.add("first");
                throw new IllegalStateException("boom");
            }
        }, 0);
        DeepGenerators.register("b:second", new IDeepGenerator() {

            @Override
            public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
                calls.add("second " + minY + " " + retrogen);
            }
        }, 0);
        DeepGenerators.generateTerrain(new Chunk(null, 3, 4), -64, 1L, true);
        assertEquals(Arrays.asList("first", "second -64 true"), calls);
    }

    @Test
    void orderIsPriorityThenIdNotRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        DeepGenerators.register("m:mid", recording(calls, "mid"), 0);
        DeepGenerators.register("z:early", recording(calls, "early"), -5);
        DeepGenerators.register("a:mid", recording(calls, "a"), 0);
        DeepGenerators.register("b:late", recording(calls, "late"), 7);
        DeepGenerators.generateTerrain(new Chunk(null, 0, 0), -64, 1L, false);
        assertEquals(Arrays.asList("early", "a", "mid", "late"), calls);
        assertEquals(Arrays.asList("z:early", "a:mid", "m:mid", "b:late"), DeepGenerators.ids());
    }

    @Test
    void stackOverflowIsNotSwallowed() {
        List<String> calls = new ArrayList<>();
        DeepGenerators.register("a:deep", new IDeepGenerator() {

            @Override
            public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
                throw new StackOverflowError();
            }
        }, 0);
        DeepGenerators.register("b:after", recording(calls, "after"), 0);
        assertThrows(
            StackOverflowError.class,
            () -> DeepGenerators.generateTerrain(new Chunk(null, 0, 0), -64, 1L, false));
        assertEquals(Arrays.asList(), calls);
    }

    @Test
    void describesGeneratorsWithVersions() {
        DeepGenerators.register("b:two", new IDeepGenerator() {}, 0);
        DeepGenerators.register("a:one", new IDeepGenerator() {

            @Override
            public String version() {
                return "1.2";
            }
        }, 0);
        assertEquals(Arrays.asList("a:one@1.2", "b:two"), DeepGenerators.describe(null));
    }

    @Test
    void rejectsDuplicatesAndLateRegistration() {
        IDeepGenerator noop = new IDeepGenerator() {};
        DeepGenerators.register("a:x", noop, 0);
        assertThrows(IllegalArgumentException.class, () -> DeepGenerators.register("a:x", noop, 1));
        assertThrows(IllegalArgumentException.class, () -> DeepGenerators.register("", noop, 0));
        assertThrows(IllegalArgumentException.class, () -> DeepGenerators.register("a:y", null, 0));
        DeepGenerators.freeze();
        assertThrows(IllegalStateException.class, () -> DeepGenerators.register("a:z", noop, 0));
        assertTrue(DeepGenerators.any());
    }

    @Test
    void firstGenerationFreezesTheList() {
        DeepGenerators.generateTerrain(new Chunk(null, 0, 0), -64, 1L, false);
        assertThrows(IllegalStateException.class, () -> DeepGenerators.register("a:late", new IDeepGenerator() {}, 0));
    }
}
