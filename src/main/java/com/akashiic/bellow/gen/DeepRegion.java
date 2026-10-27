package com.akashiic.bellow.gen;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.gen.ore.OreFeature;

/**
 * 3x3 chunks around the chunk being decorated. Writes go straight into deep sections (no lighting or neighbour
 * updates: base stone and ores are opaque with light 0, so no light value changes). Reads outside the region report a
 * solid, non-target block, so veins never reach unloaded chunks.
 */
final class DeepRegion implements OreFeature.World {

    private final Chunk[] chunks; // index (dz + 1) * 3 + (dx + 1)
    private final int originChunkX;
    private final int originChunkZ;
    private final int minY;
    private final int[] touchedSlots = new int[9];

    private Block[] targetBlocks = new Block[2];
    private int[] targetMetas = new int[2];
    private boolean blob;

    DeepRegion(Chunk[] chunks, int originChunkX, int originChunkZ, int minY) {
        this.chunks = chunks;
        this.originChunkX = originChunkX;
        this.originChunkZ = originChunkZ;
        this.minY = minY;
    }

    /** Ore: target 0 replaces stone, target 1 replaces deepslate/tuff. */
    void useOre(Block stoneOre, int stoneMeta, Block deepOre, int deepMeta) {
        this.blob = false;
        this.targetBlocks[0] = stoneOre;
        this.targetMetas[0] = stoneMeta;
        this.targetBlocks[1] = deepOre;
        this.targetMetas[1] = deepMeta;
    }

    /** Blob: target 0 replaces any base stone. */
    void useBlob(Block block, int meta) {
        this.blob = true;
        this.targetBlocks[0] = block;
        this.targetMetas[0] = meta;
    }

    Chunk chunkAt(int x, int z) {
        int dx = (x >> 4) - this.originChunkX + 1;
        int dz = (z >> 4) - this.originChunkZ + 1;
        if (dx < 0 || dx > 2 || dz < 0 || dz > 2) return null;
        return this.chunks[dz * 3 + dx];
    }

    int touchedSlots(int index) {
        return this.touchedSlots[index];
    }

    Chunk chunk(int index) {
        return this.chunks[index];
    }

    private ExtendedBlockStorage section(Chunk chunk, int y) {
        return BellowAPI.getDeepSection(chunk, y >> 4);
    }

    @Override
    public boolean canWrite(int x, int y, int z) {
        if (y < this.minY || y >= 0) return false;
        Chunk c = this.chunkAt(x, z);
        return c != null && this.section(c, y) != null;
    }

    @Override
    public int matchTarget(int x, int y, int z) {
        ExtendedBlockStorage ebs = this.section(this.chunkAt(x, z), y);
        Block host = ebs.getBlockByExtId(x & 15, y & 15, z & 15);
        int meta = ebs.getExtBlockMetadata(x & 15, y & 15, z & 15);
        boolean stone = DeepBlocks.isStoneHost(host, meta);
        boolean deep = !stone && DeepBlocks.isDeepHost(host, meta);
        if (this.blob) return stone || deep ? 0 : -1;
        return stone ? 0 : deep ? 1 : -1;
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        if (y < this.minY || y > 255) return false;
        Chunk c = this.chunkAt(x, z);
        if (c == null) return false;
        return c.getBlock(x & 15, y, z & 15)
            .getMaterial() == Material.air;
    }

    @Override
    public void place(int x, int y, int z, int targetIndex) {
        Chunk c = this.chunkAt(x, z);
        ExtendedBlockStorage ebs = this.section(c, y);
        ebs.func_150818_a(x & 15, y & 15, z & 15, this.targetBlocks[targetIndex]);
        ebs.setExtBlockMetadata(x & 15, y & 15, z & 15, this.targetMetas[targetIndex]);
        int index = ((z >> 4) - this.originChunkZ + 1) * 3 + (x >> 4) - this.originChunkX + 1;
        this.touchedSlots[index] |= 1 << DeepConstants.slot(y >> 4);
    }
}
