package com.akashiic.bellow.gen;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.storage.IDeepChunkInternal;

/**
 * Entry point of deep generation, called from {@code Chunk.onChunkLoad} on the server. That method runs for every
 * chunk that enters a world: freshly generated, loaded synchronously, or loaded by Forge's async chunk IO (stage 2).
 */
public final class DeepGen {

    private DeepGen() {}

    /**
     * Start of {@code Chunk.onChunkLoad}: the deep terrain, so {@code ChunkEvent.Load} listeners already see it. Only
     * this chunk is touched. The sections from the floor up to Y -1 are created (empty) and handed to the generators,
     * which make the layer; without any generator for the dimension the chunk gets the provisional layer instead (see
     * {@link DeepTerrain#provisional}).
     */
    public static void onChunkLoad(Chunk chunk) {
        World world = chunk.worldObj;
        if (world == null || world.isRemote || !(world instanceof WorldServer) || !GenConfig.enabled) return;
        int minY = BellowAPI.getMinY(world);
        if (minY >= 0) return;
        IDeepChunk deep = (IDeepChunk) chunk;
        if (deep.bellow$getGenState() != DeepConstants.GEN_NONE) return;
        // fromDisk && state NONE: chunk saved before the install (or before this dimension was enabled), or with the
        // provisional layer only
        boolean retrogen = deep.bellow$isFromDisk();
        if (retrogen && !GenConfig.retrogen) return;
        IDeepChunkInternal state = (IDeepChunkInternal) chunk;
        if (!DeepGenerators.anyFor(world)) {
            // no generator makes this dimension's deep layer: a provisional one, once, or none; the chunk stays NONE
            if (!state.bellow$isProvisional() && GenConfig.provisionalLayer) DeepTerrain.provisional(chunk, minY);
            return;
        }
        DeepTerrain.prepare(chunk, minY);
        DeepGenerators.generateTerrain(chunk, minY, world.getSeed(), retrogen);
        // after the generators, only where they left Y -1 solid (see replaceLegacyBedrock)
        DeepTerrain.replaceLegacyBedrock(chunk);
        state.bellow$setProvisional(false);
        state.bellow$setGenState(DeepConstants.GEN_TERRAIN);
    }

    /**
     * End of {@code Chunk.onChunkLoad}: decoration of the 3x3 neighbourhoods this chunk completes. Only now are the
     * chunk's tile entities and entities registered with the world: a tile entity created during decoration (a spawner
     * placed by a generator) would otherwise be added to the ticking list twice, and old ones could not be removed.
     */
    public static void afterChunkLoad(Chunk chunk) {
        World world = chunk.worldObj;
        if (world == null || world.isRemote || !(world instanceof WorldServer) || !GenConfig.enabled) return;
        int minY = BellowAPI.getMinY(world);
        byte state = ((IDeepChunk) chunk).bellow$getGenState();
        if (minY >= 0 || state == DeepConstants.GEN_NONE) return;
        // a decorated chunk may complete the 5x5 a neighbour's light stage waits for, or have a stage of its own due
        if (state >= DeepConstants.GEN_DECORATED && DeepGenerators.anyFor(world)) {
            DeepStages.queue((WorldServer) world, chunk);
        }
        DeepDecorator.tryAround((WorldServer) world, chunk, minY);
    }
}
