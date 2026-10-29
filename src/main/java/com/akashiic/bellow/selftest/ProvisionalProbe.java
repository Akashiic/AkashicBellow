package com.akashiic.bellow.selftest;

import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.gen.DeepGen;
import com.akashiic.bellow.gen.DeepGenerators;
import com.akashiic.bellow.storage.IDeepChunkInternal;

/**
 * A dimension without any deep generator (the provider removed from the server): Bellow places no content of its own,
 * so new chunks get the provisional layer, stone over a bedrock floor, and stay not generated with their legacy
 * bedrock. Phase {@code full} loads area P (chunks 3199..3201) with the generators hidden; phase {@code verify} (a new
 * JVM, generators installed) loads it again: the generators receive it as retrogen chunks and replace the layer.
 */
public final class ProvisionalProbe {

    private static final int P = 3200;

    private ProvisionalProbe() {}

    public static void run(WorldServer world, int minY, BukkitProbe.Check check) {
        int calls = TestLayer.INSTANCE.calls;
        int x = (P << 4) + 8, z = (P << 4) + 8;
        DeepGenerators.hideForTests(true);
        try {
            for (int cx = P - 1; cx <= P + 1; cx++) for (int cz = P - 1; cz <= P + 1; cz++) world.getChunkProvider()
                .loadChunk(cx, cz);
            Chunk c = world.getChunkFromChunkCoords(P, P);
            int notNone = 0, notProvisional = 0, legacyKept = 0, columns = 0;
            for (int cx = P - 1; cx <= P + 1; cx++) for (int cz = P - 1; cz <= P + 1; cz++) {
                Chunk n = world.getChunkFromChunkCoords(cx, cz);
                if (((IDeepChunk) n).bellow$getGenState() != DeepConstants.GEN_NONE) notNone++;
                if (!((IDeepChunkInternal) n).bellow$isProvisional()) notProvisional++;
                for (int i = 0; i < 256; i++) {
                    columns++;
                    if (n.getBlock(i & 15, 0, i >> 4) == Blocks.bedrock) legacyKept++;
                }
            }
            boolean layer = c.getBlock(8, minY, 8) == Blocks.bedrock && c.getBlock(8, minY + 20, 8) == Blocks.stone
                && c.getBlock(8, -1, 8) == Blocks.stone;
            check.check(
                "without a generator: provisional stone layer, chunk not generated, legacy bedrock kept",
                layer && notNone == 0
                    && notProvisional == 0
                    && legacyKept == columns
                    && TestLayer.INSTANCE.calls == calls,
                "stone over bedrock " + layer
                    + ", chunks not NONE "
                    + notNone
                    + ", not provisional "
                    + notProvisional
                    + ", columns with bedrock at Y 0 "
                    + legacyKept
                    + "/"
                    + columns
                    + ", generator calls "
                    + (TestLayer.INSTANCE.calls - calls));
            // a tunnel dug into the provisional layer survives the chunk loading again (the layer is made once)
            world.setBlockToAir(x, minY + 20, z);
            DeepGen.onChunkLoad(c);
            check.check(
                "the provisional layer is made once",
                world.isAirBlock(x, minY + 20, z),
                "tunnel " + (world.isAirBlock(x, minY + 20, z) ? "kept" : "filled again"));
        } finally {
            DeepGenerators.hideForTests(false);
        }
    }

    /** Phase {@code verify}: the generators take the provisional chunks over. */
    public static void verify(WorldServer world, int minY, BukkitProbe.Check check) {
        world.getChunkProvider()
            .loadChunk(P, P);
        Chunk c = world.getChunkFromChunkCoords(P, P);
        long key = ChunkCoordIntPair.chunkXZ2Int(P, P);
        int legacyLeft = 0;
        for (int i = 0; i < 256; i++) for (int y = 0; y <= GenConfig.legacyBedrockMaxY; y++)
            if (c.getBlock(i & 15, y, i >> 4) == Blocks.bedrock) legacyLeft++;
        boolean terrain = ((IDeepChunk) c).bellow$getGenState() >= DeepConstants.GEN_TERRAIN;
        boolean provisional = ((IDeepChunkInternal) c).bellow$isProvisional();
        check.check(
            "a generator installed later takes the provisional layer over (retrogen)",
            terrain && !provisional
                && TestLayer.INSTANCE.retrogenOnProvisional.contains(key)
                && (!GenConfig.replaceLegacyBedrock || legacyLeft == 0)
                && BellowAPI.getGeneratorStages(c, TestLayer.ID) != 0,
            "state " + ((IDeepChunk) c).bellow$getGenState()
                + ", provisional "
                + provisional
                + ", retrogen on the provisional layer "
                + TestLayer.INSTANCE.retrogenOnProvisional.contains(key)
                + ", legacy bedrock left "
                + legacyLeft);
    }
}
