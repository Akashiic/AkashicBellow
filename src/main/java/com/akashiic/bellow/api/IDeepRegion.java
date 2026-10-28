package com.akashiic.bellow.api;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * The 3x3 chunks around the chunk being decorated ({@link IDeepGenerator#decorate}). Coordinates are world block
 * coordinates. Reads cover {@code minY..255}; writes cover {@code minY..-1} only, because the 1.7.10 generators of the
 * pack own {@code y >= 0}.
 */
public interface IDeepRegion {

    WorldServer world();

    long seed();

    int minY();

    /** Chunk coordinates of the centre chunk. */
    int centerChunkX();

    int centerChunkZ();

    /** The loaded chunk holding block column {@code (x, z)}, or null outside the 3x3 region. */
    Chunk chunkAt(int x, int z);

    /** The block at a position, or null outside the region or outside {@code minY..255}. */
    Block getBlock(int x, int y, int z);

    /** The metadata at a position, 0 outside the region. */
    int getMeta(int x, int y, int z);

    /**
     * Writes a block straight into the deep section: no light, neighbour or physics updates (a fluid placed here only
     * flows once something next to it changes). The tile entity of the block that was there is removed; a block with a
     * tile entity gets a new one, returned by {@link #getTileEntity}. Changed sections are sent to players that
     * already received the chunk once decoration ends.
     *
     * @return false, writing nothing, outside the region or outside {@code minY..-1}
     */
    boolean setBlock(int x, int y, int z, Block block, int meta);

    /** The tile entity at a position inside the region (created if its block has one), or null. */
    TileEntity getTileEntity(int x, int y, int z);
}
