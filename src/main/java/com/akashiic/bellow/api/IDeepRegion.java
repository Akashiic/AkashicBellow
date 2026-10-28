package com.akashiic.bellow.api;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * The 3x3 chunks around the chunk being decorated ({@link IDeepGenerator#decorate}). Coordinates are world block
 * coordinates. Reads cover {@code minY..255}; writes cover {@code minY..-1} only, because the 1.7.10 generators of the
 * pack own {@code y >= 0}. Chunks whose deep data was saved by a newer Bellow format are read-only (they show no deep
 * layer in this session): writes there are refused.
 */
public interface IDeepRegion {

    /** The world, for reads and for the seed or the dimension; writes go through {@link #setBlock}. */
    WorldServer world();

    long seed();

    int minY();

    /** Chunk coordinates of the centre chunk. */
    int centerChunkX();

    int centerChunkZ();

    /**
     * The loaded chunk holding block column {@code (x, z)}, or null outside the 3x3 region. How long players have been
     * near it: {@code chunk.inhabitedTime} (ticks).
     */
    Chunk chunkAt(int x, int z);

    /**
     * Generation state the chunk holding column {@code (x, z)} had when this decoration started, -1 outside the region.
     * The centre is {@link DeepConstants#GEN_TERRAIN}; a neighbour at {@link DeepConstants#GEN_DECORATED} or later was
     * decorated before, possibly long ago, and may hold what players built ({@link BellowAPI#getGeneratorStages} tells
     * which generators shaped it).
     *
     * @since API 3
     */
    byte genStateAt(int x, int z);

    /** The block at a position, or null outside the region or outside {@code minY..255}. */
    Block getBlock(int x, int y, int z);

    /** The metadata at a position, 0 outside the region. */
    int getMeta(int x, int y, int z);

    /**
     * Writes a block straight into the deep section: no light, neighbour or physics updates (a fluid placed here only
     * flows once something next to it changes; blocks that emit light are lit by Bellow's light stage, see
     * {@link IDeepGenerator#onLit}). The tile entity of the block that was there is removed; a block with a tile entity
     * gets a new one, returned by {@link #getTileEntity}. Changed sections are sent to players that already received
     * the chunk once decoration ends.
     *
     * @return false, writing nothing, outside the region, outside {@code minY..-1} or in a read-only chunk
     */
    boolean setBlock(int x, int y, int z, Block block, int meta);

    /** The tile entity at a position inside the region (created if its block has one), or null. */
    TileEntity getTileEntity(int x, int y, int z);

    /**
     * A 1.18 ore vein ({@code OreFeature}) like Bellow's own: replaces stone with {@code ore} and the deep hosts
     * (deepslate, tuff; {@link BellowAPI#isDeepHost}) with its deepslate variant from Et Futurum's registry (or
     * {@code ore} itself). Each block touching air is skipped with probability {@code discardOnAir}; air is never
     * replaced. Blocks outside the region, outside {@code minY..-1} or in a read-only chunk are clipped.
     *
     * @param size 0..64, the 1.18 vein size
     * @return the number of blocks placed
     * @throws IllegalArgumentException for air, a block with a tile entity, or values out of range
     * @since API 3
     */
    int placeOre(Random random, int x, int y, int z, int size, float discardOnAir, Block ore, int meta);

    /**
     * A 1.18 blob (the same vein shape, as {@code ore_clay}, {@code ore_tuff} or {@code ore_gravel}): replaces every
     * base stone ({@link BellowAPI#isBaseStone}) with {@code block}. Same rules as {@link #placeOre}.
     *
     * @since API 3
     */
    int placeBlob(Random random, int x, int y, int z, int size, float discardOnAir, Block block, int meta);
}
