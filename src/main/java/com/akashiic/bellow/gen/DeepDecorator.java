package com.akashiic.bellow.gen;

import java.util.ArrayDeque;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.storage.IDeepChunkInternal;

/**
 * DECORATED stage. A chunk is decorated once it and its 8 neighbours are loaded and have their deep terrain (the 3x3
 * neighbourhood of 1.18 feature placement): the generators' {@code decorate} with the 3x3 as an
 * {@link com.akashiic.bellow.api.IDeepRegion}. The stage runs only in dimensions with a generator.
 */
public final class DeepDecorator {

    private DeepDecorator() {}

    /** Decorations running now (main thread): more than 0 while one runs, and a chunk it loads would nest another. */
    private static int depth;
    /** Chunk loads that happened during a decoration, tried once it ends. */
    private static final ArrayDeque<Chunk> deferred = new ArrayDeque<>();

    /**
     * Try every chunk whose 3x3 neighbourhood may have just been completed by {@code loaded}.
     * <p>
     * Not reentrant: a chunk loaded from inside a decoration (a generator reading the World past its region) is only
     * queued, and its neighbourhoods are tried when the outer decoration has finished. Decorations never nest, so
     * neither does the stack depth they add to a chunk load.
     */
    public static void tryAround(WorldServer world, Chunk loaded, int minY) {
        if (!DeepGenerators.any()) return;
        if (depth > 0) {
            deferred.add(loaded);
            return;
        }
        depth++;
        try {
            around(world, loaded, minY);
            Chunk next;
            while ((next = deferred.poll()) != null) {
                WorldServer w = (WorldServer) next.worldObj;
                // still the loaded chunk of its position (it may have been unloaded since)
                if (!w.getChunkProvider()
                    .chunkExists(next.xPosition, next.zPosition)
                    || w.getChunkProvider()
                        .provideChunk(next.xPosition, next.zPosition) != next)
                    continue;
                around(w, next, BellowAPI.getMinY(w));
            }
        } finally {
            depth--;
            // only left over when a fatal error unwinds from a decoration
            if (depth == 0) deferred.clear();
        }
    }

    /** Whether a decoration is running (the self test checks that nested loads are deferred). */
    public static boolean decorating() {
        return depth > 0;
    }

    private static void around(WorldServer world, Chunk loaded, int minY) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                tryDecorate(world, loaded, loaded.xPosition + dx, loaded.zPosition + dz, minY);
            }
        }
    }

    private static Chunk loaded(WorldServer world, Chunk hint, int x, int z) {
        if (hint.xPosition == x && hint.zPosition == z) return hint;
        IChunkProvider provider = world.getChunkProvider();
        return provider.chunkExists(x, z) ? provider.provideChunk(x, z) : null;
    }

    private static void tryDecorate(WorldServer world, Chunk hint, int cx, int cz, int minY) {
        Chunk centre = loaded(world, hint, cx, cz);
        if (centre == null || ((IDeepChunk) centre).bellow$getGenState() != DeepConstants.GEN_TERRAIN) return;
        Chunk[] region = new Chunk[9];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Chunk c = loaded(world, hint, cx + dx, cz + dz);
                if (c == null || ((IDeepChunk) c).bellow$getGenState() < DeepConstants.GEN_TERRAIN) return;
                region[(dz + 1) * 3 + dx + 1] = c;
            }
        }
        DeepRegion view = new DeepRegion(world, world.getSeed(), region, cx, cz, minY);
        // claimed before decorating: a chunk load from inside the stage that completes this neighbourhood again must
        // not
        // decorate it a second time. The claim stands whatever happens below (a retry would place the same blocks
        // twice)
        ((IDeepChunkInternal) centre).bellow$setGenState(DeepConstants.GEN_DECORATED);
        try {
            DeepGenerators.decorate(view, centre);
        } finally {
            // whatever was written is saved and sent, even when a step failed
            centre.setChunkModified();
            for (int i = 0; i < 9; i++) {
                int slots = view.touchedSlots(i);
                if (slots == 0) continue;
                Chunk c = view.chunk(i);
                c.setChunkModified();
                DeepStages.resend(world, c, slots);
            }
            // what generators placed is lit once the chunks around are decorated too (see DeepStages)
            ((IDeepChunkInternal) centre).bellow$setLightPending(true);
            DeepStages.queue(world, centre);
        }
    }
}
