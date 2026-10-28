package com.akashiic.bellow.gen;

import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.bellow.world.LegacyGeneration;

/**
 * Optional check of the generator contract ({@code worldgen.strictGenerators}): while a deep generator runs its terrain
 * or decoration step, {@code World} writes that the contract forbids are logged and refused. The terrain step may not
 * write through the World at all; the decoration step only below Y 0, above the floor, inside its 3x3 region. Writes of
 * a legacy population that runs nested inside the step (a chunk the generator caused to load) are not the generator's
 * and pass. Meant for testing generators; off by default.
 */
public final class StrictGuard {

    private static final Logger LOG = LogManager.getLogger("AkashicBellow");
    private static final int TRACED = 3;

    /** The step running now; null when strict mode is off or no step runs (the fast path of every World write). */
    private static volatile Context current;
    private static int violations;

    private StrictGuard() {}

    static final class Context {

        final Thread thread = Thread.currentThread();
        final World world;
        /** Null for the terrain step. */
        final IDeepRegion region;
        final int minY;
        String generator = "?";

        Context(World world, IDeepRegion region, int minY) {
            this.world = world;
            this.region = region;
            this.minY = minY;
        }
    }

    /** Starts a step; returns what {@link #exit} restores (steps nest when a generator loads chunks). */
    static Context enter(World world, IDeepRegion region, int minY) {
        Context outer = current;
        if (GenConfig.strictGenerators) current = new Context(world, region, minY);
        return outer;
    }

    static void generator(String id) {
        Context c = current;
        if (c != null && c.thread == Thread.currentThread()) c.generator = id;
    }

    static void exit(Context outer) {
        if (GenConfig.strictGenerators || current != null) current = outer;
    }

    /** From {@code World.setBlock} and {@code World.setBlockMetadataWithNotify}: true refuses the write. */
    public static boolean refuses(World world, int x, int y, int z) {
        Context c = current;
        if (c == null || c.thread != Thread.currentThread() || LegacyGeneration.active()) return false;
        if (c.region != null && world == c.world && y >= c.minY && y < 0 && c.region.chunkAt(x, z) != null) {
            return false;
        }
        int n = ++violations;
        String step = c.region == null ? "generateTerrain" : "decorate";
        if (n <= TRACED) {
            LOG.error(
                "Strict generators: deep generator {} wrote through the World at {}, {}, {} in {}; refused",
                c.generator,
                x,
                y,
                z,
                step,
                new IllegalStateException("contract violation"));
        } else if (Integer.bitCount(n) == 1) {
            LOG.error("Strict generators: {} refused World writes so far (last: {} in {})", n, c.generator, step);
        }
        return true;
    }

    /** Self test only. */
    public static int violations() {
        return violations;
    }
}
