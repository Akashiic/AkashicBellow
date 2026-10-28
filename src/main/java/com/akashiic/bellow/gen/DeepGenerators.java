package com.akashiic.bellow.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.akashiic.bellow.api.IDeepGenerator;
import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.bellow.world.LegacyGeneration;

/**
 * Generators registered through {@link com.akashiic.bellow.api.BellowAPI#registerGenerator}. The list is frozen when
 * the first server starts (or the first chunk is generated), so every chunk of a session sees the same generators in
 * the same order. A generator that throws is logged and skipped for that chunk; the chunk itself goes on.
 */
public final class DeepGenerators {

    /** The mod's logger, by name: this class must load without the mod class (unit tests). */
    private static final Logger LOG = LogManager.getLogger("AkashicBellow");
    /** Full stack traces per generator before errors are only counted. */
    private static final int TRACED_ERRORS = 3;

    private static final List<Entry> entries = new ArrayList<>();
    private static volatile boolean frozen;

    private DeepGenerators() {}

    private static final class Entry {

        final String id;
        final IDeepGenerator generator;
        int errors;

        Entry(String id, IDeepGenerator generator) {
            this.id = id;
            this.generator = generator;
        }
    }

    public static synchronized void register(String id, IDeepGenerator generator) {
        if (id == null || id.isEmpty() || generator == null) {
            throw new IllegalArgumentException("Deep generator needs an id and an instance");
        }
        if (frozen) {
            throw new IllegalStateException(
                "Deep generator " + id + " registered too late: register during preInit, init or postInit");
        }
        for (Entry e : entries) {
            if (e.id.equals(id)) throw new IllegalArgumentException("Deep generator " + id + " is already registered");
        }
        entries.add(new Entry(id, generator));
    }

    /** Ends registration and logs the generators (once). */
    public static synchronized void freeze() {
        if (frozen) return;
        frozen = true;
        if (entries.isEmpty()) {
            LOG.info("Deep generators: none registered");
        } else {
            LOG.info("Deep generators, in order: {}", ids());
        }
    }

    public static synchronized List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Entry e : entries) ids.add(e.id);
        return Collections.unmodifiableList(ids);
    }

    public static boolean any() {
        return !entries.isEmpty();
    }

    // Each call leaves the legacy population phase: a chunk load (and so a deep generator) can happen inside another
    // chunk's population, where World writes below 0 are refused for the 1.7.10 generators.

    static void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
        freeze();
        if (entries.isEmpty()) return;
        int legacy = LegacyGeneration.suspend();
        try {
            for (Entry e : entries) {
                try {
                    e.generator.generateTerrain(chunk, minY, seed, retrogen);
                } catch (RuntimeException | LinkageError ex) {
                    failed(e, "generateTerrain", chunk, ex);
                }
            }
        } finally {
            LegacyGeneration.resume(legacy);
        }
    }

    static void decorate(IDeepRegion region, Chunk centre) {
        if (entries.isEmpty()) return;
        int legacy = LegacyGeneration.suspend();
        try {
            for (Entry e : entries) {
                try {
                    e.generator.decorate(region);
                } catch (RuntimeException | LinkageError ex) {
                    failed(e, "decorate", centre, ex);
                }
            }
        } finally {
            LegacyGeneration.resume(legacy);
        }
    }

    static void onDecorated(WorldServer world, Chunk chunk) {
        if (entries.isEmpty()) return;
        int legacy = LegacyGeneration.suspend();
        try {
            for (Entry e : entries) {
                try {
                    e.generator.onDecorated(world, chunk);
                } catch (RuntimeException | LinkageError ex) {
                    failed(e, "onDecorated", chunk, ex);
                }
            }
        } finally {
            LegacyGeneration.resume(legacy);
        }
    }

    private static void failed(Entry e, String step, Chunk chunk, Throwable ex) {
        int n = ++e.errors;
        String where = "chunk " + chunk.xPosition + "," + chunk.zPosition;
        if (n <= TRACED_ERRORS) {
            LOG.error("Deep generator {} failed in {} at {} (error {}); the chunk goes on", e.id, step, where, n, ex);
        } else if (Integer.bitCount(n) == 1) {
            LOG.error("Deep generator {} failed in {} at {}: {} errors so far ({})", e.id, step, where, n, ex);
        }
    }

    /** Tests only. */
    static synchronized void resetForTests() {
        entries.clear();
        frozen = false;
    }
}
