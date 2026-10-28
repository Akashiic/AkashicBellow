package com.akashiic.bellow.api;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Stable entry points for other mods (deep cave generators, map mods, etc.).
 */
public final class BellowAPI {

    private BellowAPI() {}

    /** Lowest valid block Y (inclusive). 0 for worlds without deep layers. */
    public static int getMinY(World world) {
        return world == null ? 0 : ((IDeepWorld) world).bellow$getMinY();
    }

    public static boolean hasDeepLayers(World world) {
        return getMinY(world) < 0;
    }

    /**
     * Returns the storage of a deep section ({@code minY >> 4 <= sectionY < 0}) or {@code null}; sections below the
     * world floor (dormant data the world itself never reads) are not exposed. Do not cache the result across ticks:
     * chunks may reload.
     */
    public static ExtendedBlockStorage getDeepSection(Chunk chunk, int sectionY) {
        int slot = DeepConstants.slot(sectionY);
        if (slot < 0 || sectionY << 4 < getMinY(chunk.worldObj)) return null;
        ExtendedBlockStorage[] deep = ((IDeepChunk) chunk).bellow$peekDeepSections();
        return deep == null ? null : deep[slot];
    }
}
