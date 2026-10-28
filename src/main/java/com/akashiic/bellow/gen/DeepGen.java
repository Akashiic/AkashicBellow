package com.akashiic.bellow.gen;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;

/**
 * Entry point of deep generation, called from {@code Chunk.onChunkLoad} on the server. That method runs for every
 * chunk that enters a world: freshly generated, loaded synchronously, or loaded by Forge's async chunk IO (stage 2).
 */
public final class DeepGen {

    private DeepGen() {}

    public static void onChunkLoad(Chunk chunk) {
        World world = chunk.worldObj;
        if (world == null || world.isRemote || !(world instanceof WorldServer) || !GenConfig.enabled) return;
        int minY = BellowAPI.getMinY(world);
        if (minY >= 0) return;
        IDeepChunk deep = (IDeepChunk) chunk;
        if (deep.bellow$getGenState() == DeepConstants.GEN_NONE) {
            // fromDisk && state NONE: chunk saved before the install (or before this dimension was enabled).
            boolean retrogen = deep.bellow$isFromDisk();
            if (retrogen && !GenConfig.retrogen) return;
            DeepTerrain.fill(chunk, minY, world.getSeed());
            // deep generators carve the freshly filled layer, before any ore of the 3x3 stage (1.18: carvers first)
            DeepGenerators.generateTerrain(chunk, minY, world.getSeed(), retrogen);
            deep.bellow$setGenState(DeepConstants.GEN_TERRAIN);
        }
        DeepDecorator.tryAround((WorldServer) world, chunk, minY);
    }
}
