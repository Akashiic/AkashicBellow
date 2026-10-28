package com.akashiic.bellow.gen;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.storage.IDeepChunkInternal;

/**
 * TERRAIN stage: solid deep block from the floor up to Y -1 with the 1.18 bedrock floor, plus optional replacement of
 * the natural bedrock that used to be the floor (Y 0..legacyBedrockMaxY).
 * <p>
 * Pure function of (seed, chunk position, config): chunk-local, needs no neighbour, never overwrites a non-air block.
 */
public final class DeepTerrain {

    /** Salt of the positional random used by the bedrock gradient. */
    static final long BEDROCK_SALT = 0x0BED20C4F1003L;
    /** 1.18 {@code verticalGradient("bedrock_floor", aboveBottom(0), aboveBottom(5))}. */
    static final int BEDROCK_GRADIENT = 5;

    private DeepTerrain() {}

    /**
     * Probability that the block at {@code y} is bedrock: 1 at the floor, then {@code 1 - (y - minY) / 5}, i.e.
     * 100%, 80%, 60%, 40%, 20% and 0% from Y = minY + 5 upward.
     */
    public static float bedrockChance(int y, int minY) {
        int i = minY;
        int j = minY + BEDROCK_GRADIENT;
        if (y <= i) return 1.0F;
        if (y >= j) return 0.0F;
        return 1.0F - (float) (y - i) / (float) (j - i);
    }

    public static boolean isBedrock(long seed, int x, int y, int z, int minY) {
        float p = bedrockChance(y, minY);
        return p >= 1.0F || p > 0.0F && PositionalRandom.nextFloat(seed ^ BEDROCK_SALT, x, y, z) < p;
    }

    public static void fill(Chunk chunk, int minY, long seed) {
        boolean hasSky = !chunk.worldObj.provider.hasNoSky;
        ExtendedBlockStorage[] deep = ((IDeepChunkInternal) chunk).bellow$getDeepSections();
        Block fill = DeepBlocks.fill;
        int fillMeta = DeepBlocks.fillMeta;
        int baseX = chunk.xPosition << 4;
        int baseZ = chunk.zPosition << 4;

        for (int sy = minY >> 4; sy < 0; sy++) {
            int slot = DeepConstants.slot(sy);
            ExtendedBlockStorage ebs = deep[slot];
            if (ebs == null) ebs = deep[slot] = new ExtendedBlockStorage(sy << 4, hasSky);
            for (int ly = 0; ly < 16; ly++) {
                int y = (sy << 4) + ly;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (ebs.getBlockByExtId(lx, ly, lz)
                            .getMaterial() != Material.air) continue;
                        if (isBedrock(seed, baseX + lx, y, baseZ + lz, minY)) {
                            ebs.func_150818_a(lx, ly, lz, Blocks.bedrock);
                        } else {
                            ebs.func_150818_a(lx, ly, lz, fill);
                            if (fillMeta != 0) ebs.setExtBlockMetadata(lx, ly, lz, fillMeta);
                        }
                    }
                }
            }
        }

        if (GenConfig.replaceLegacyBedrock) replaceLegacyBedrock(chunk, fill, fillMeta);
        chunk.setChunkModified();
    }

    private static void replaceLegacyBedrock(Chunk chunk, Block fill, int fillMeta) {
        ExtendedBlockStorage bottom = chunk.getBlockStorageArray()[0];
        if (bottom == null) return;
        int maxY = Math.min(GenConfig.legacyBedrockMaxY, 15);
        for (int y = 0; y <= maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (bottom.getBlockByExtId(x, y, z) != Blocks.bedrock) continue;
                    bottom.func_150818_a(x, y, z, fill);
                    bottom.setExtBlockMetadata(x, y, z, fillMeta);
                }
            }
        }
    }
}
