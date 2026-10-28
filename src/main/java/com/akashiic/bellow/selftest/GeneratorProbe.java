package com.akashiic.bellow.selftest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.api.IDeepGenerator;
import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.bellow.gen.DeepBlocks;
import com.akashiic.bellow.world.LegacyGeneration;

/**
 * Self-test of the deep generator API (phase {@code full}). A test generator is registered during init; in chunks
 * 2000..2004 x 2000..2004 its terrain step carves an 8 x 8 x 8 chamber at {@code minY + 20}, and its decoration step
 * places marker blocks through {@link IDeepRegion}. Elsewhere it only records the calls. {@link #run} loads the area
 * (and a second one with ores disabled) and checks the order and the guarantees of {@link IDeepGenerator}.
 */
public final class GeneratorProbe implements IDeepGenerator {

    static final String ID = "akashicbellow:selftest";
    private static final GeneratorProbe INSTANCE = new GeneratorProbe();

    private static final int A0 = 2000, A1 = 2004; // carving area (chunks)
    private static final int B = 2101; // centre chunk of the ores-off area
    private static final int C = 2201; // centre chunk of the area loaded inside a (simulated) legacy population
    private static final int CHAMBER = 20; // chamber floor, above the floor of the world

    private final Map<Long, Integer> terrainCalls = new HashMap<>();
    private int retrogenCalls;
    private int retrogenMismatches;
    private final Set<Long> decorated = new HashSet<>();
    private int decorateTwice;
    private int decoratedBeforeNeighbourTerrain;
    private int chamberFilledBeforeDecorate;
    private int decoratedBeforeOres;
    private int writeOk, writeRefused, writeWrong;
    private int chestsMissing;
    private int onDecoratedTooEarly;
    private int onDecoratedCalls;
    private Boolean worldWriteInLegacyPhase;

    private GeneratorProbe() {}

    public static void register() {
        BellowAPI.registerGenerator(ID, INSTANCE);
    }

    private static long key(int cx, int cz) {
        return ChunkCoordIntPair.chunkXZ2Int(cx, cz);
    }

    private static boolean inArea(int cx, int cz) {
        return cx >= A0 && cx <= A1 && cz >= A0 && cz <= A1;
    }

    @Override
    public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
        this.terrainCalls.merge(key(chunk.xPosition, chunk.zPosition), 1, Integer::sum);
        if (retrogen) this.retrogenCalls++;
        if (retrogen != ((IDeepChunk) chunk).bellow$isFromDisk()) this.retrogenMismatches++;
        if (!inArea(chunk.xPosition, chunk.zPosition)) return;
        for (int y = minY + CHAMBER; y < minY + CHAMBER + 8; y++) {
            ExtendedBlockStorage ebs = BellowAPI.getDeepSection(chunk, y >> 4);
            for (int x = 4; x < 12; x++) for (int z = 4; z < 12; z++) ebs.func_150818_a(x, y & 15, z, Blocks.air);
        }
    }

    @Override
    public void decorate(IDeepRegion region) {
        int cx = region.centerChunkX(), cz = region.centerChunkZ();
        if (!this.decorated.add(key(cx, cz))) this.decorateTwice++;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (!this.terrainCalls.containsKey(key(cx + dx, cz + dz))) this.decoratedBeforeNeighbourTerrain++;
        }
        if (!inArea(cx, cz)) return;
        int minY = region.minY(), bx = cx << 4, bz = cz << 4;
        // Bellow's ores already ran (1.18 order) and left the chamber alone (veins never replace air)
        if (countSolid(region, bx, bz, minY) != 0) this.chamberFilledBeforeDecorate++;
        if (GenConfig.ores && countPlaced(region.world(), cx, cz, minY) == 0) this.decoratedBeforeOres++;

        int y = minY + CHAMBER;
        this.expectWrite(
            region.setBlock(bx + 8, y, bz + 8, Blocks.obsidian, 0),
            region,
            bx + 8,
            y,
            bz + 8,
            Blocks.obsidian);
        // a block with a tile entity gets one at once
        this.expectWrite(region.setBlock(bx + 5, y, bz + 5, Blocks.chest, 0), region, bx + 5, y, bz + 5, Blocks.chest);
        if (!(region.getTileEntity(bx + 5, y, bz + 5) instanceof TileEntityChest)) this.chestsMissing++;
        // a neighbour inside the region, outside its chamber
        this.expectWrite(
            region.setBlock(bx + 17, minY + 40, bz + 1, Blocks.glass, 0),
            region,
            bx + 17,
            minY + 40,
            bz + 1,
            Blocks.glass);
        // refused: Y >= 0, below the floor, outside the 3x3
        if (region.setBlock(bx + 8, 0, bz + 8, Blocks.obsidian, 0)) this.writeWrong++;
        else this.writeRefused++;
        if (region.setBlock(bx + 8, minY - 1, bz + 8, Blocks.obsidian, 0)) this.writeWrong++;
        else this.writeRefused++;
        if (region.setBlock(bx + 40, y, bz + 8, Blocks.obsidian, 0)) this.writeWrong++;
        else this.writeRefused++;
    }

    private void expectWrite(boolean ok, IDeepRegion region, int x, int y, int z, Block block) {
        if (ok && region.getBlock(x, y, z) == block) this.writeOk++;
        else this.writeWrong++;
    }

    @Override
    public void onDecorated(WorldServer world, Chunk chunk) {
        this.onDecoratedCalls++;
        if (((IDeepChunk) chunk).bellow$getGenState() != DeepConstants.GEN_DECORATED) this.onDecoratedTooEarly++;
        if (chunk.xPosition == C && chunk.zPosition == C) {
            int minY = BellowAPI.getMinY(world);
            this.worldWriteInLegacyPhase = world
                .setBlock((C << 4) + 8, minY + 30, (C << 4) + 8, Blocks.lapis_block, 0, 2);
        }
    }

    private static int countSolid(IDeepRegion region, int bx, int bz, int minY) {
        int solid = 0;
        for (int y = minY + CHAMBER; y < minY + CHAMBER + 8; y++) for (int x = 4; x < 12; x++)
            for (int z = 4; z < 12; z++) if (region.getBlock(bx + x, y, bz + z) != Blocks.air) solid++;
        return solid;
    }

    /** Loads the two areas (the world must not have them yet) and checks everything recorded. */
    public static void run(WorldServer world, int minY, BukkitProbe.Check check) {
        GeneratorProbe p = INSTANCE;
        check.check(
            "generator registered",
            BellowAPI.getGeneratorIds()
                .contains(ID),
            "" + BellowAPI.getGeneratorIds());
        if (p.terrainCalls.containsKey(key(A0, A0))) {
            check.check("generator probe", true, "already ran in this session (needs a new world)");
            return;
        }
        int calls0 = p.terrainCalls.size();
        // 7 x 7 chunks: the inner 5 x 5 (the carving area) get their 3x3 neighbourhood and are decorated
        for (int cx = A0 - 1; cx <= A1 + 1; cx++) for (int cz = A0 - 1; cz <= A1 + 1; cz++) load(world, cx, cz);

        int missingTerrain = 0, notDecorated = 0;
        for (int cx = A0 - 1; cx <= A1 + 1; cx++) for (int cz = A0 - 1; cz <= A1 + 1; cz++) {
            if (!p.terrainCalls.containsKey(key(cx, cz))) missingTerrain++;
            if (inArea(cx, cz) && !p.decorated.contains(key(cx, cz))) notDecorated++;
        }
        int twice = 0;
        for (int n : p.terrainCalls.values()) if (n > 1) twice++;
        // the pack's own generators may load more chunks around the area while it populates (cascades): those get
        // the step too, so only "each of the 49, and no chunk twice" is checked
        check.check(
            "generator terrain step: once per new chunk",
            missingTerrain == 0 && twice == 0,
            "49 chunks of the area: missing " + missingTerrain
                + "; chunks called twice in the world: "
                + twice
                + "; new chunks during the load: "
                + (p.terrainCalls.size() - calls0));
        check.check(
            "generator decoration step: once, after the terrain of all 9 chunks",
            notDecorated == 0 && p.decorateTwice == 0 && p.decoratedBeforeNeighbourTerrain == 0,
            "not decorated " + notDecorated
                + ", twice "
                + p.decorateTwice
                + ", before a neighbour's terrain "
                + p.decoratedBeforeNeighbourTerrain);
        check.check(
            "ores run between the generator steps and leave carved space alone",
            p.chamberFilledBeforeDecorate == 0 && p.decoratedBeforeOres == 0,
            p.chamberFilledBeforeDecorate + " chambers with blocks when decoration started, "
                + p.decoratedBeforeOres
                + " chunks without ores yet");

        // after the whole area is decorated: chamber still open, ores around it, markers in place
        int solid = 0, ores = 0, obsidian = 0, chests = 0;
        for (int cx = A0; cx <= A1; cx++) for (int cz = A0; cz <= A1; cz++) {
            int bx = cx << 4, bz = cz << 4;
            for (int y = minY + CHAMBER; y < minY + CHAMBER + 8; y++)
                for (int x = 4; x < 12; x++) for (int z = 4; z < 12; z++) {
                    Block b = world.getBlock(bx + x, y, bz + z);
                    if (b == Blocks.obsidian) obsidian++;
                    else if (b == Blocks.chest) chests++;
                    else if (b != Blocks.air) solid++;
                }
            ores += countPlaced(world, cx, cz, minY);
        }
        check.check(
            "carved chamber stays open in the world",
            solid == 0 && obsidian == 25 && chests == 25,
            "25 chambers: " + solid + " stray blocks, " + obsidian + " obsidian, " + chests + " chests");
        check.check(
            "ores still placed around carved space",
            !GenConfig.ores || ores > 0,
            ores + " ore/tuff/gravel blocks in the 25 chunks (ores " + GenConfig.ores + ")");
        check.check(
            "IDeepRegion writes",
            p.writeWrong == 0 && p.writeOk == 75 && p.writeRefused == 75 && p.chestsMissing == 0,
            "ok " + p.writeOk
                + "/75, refused "
                + p.writeRefused
                + "/75 (Y 0, below the floor, outside the 3x3), wrong "
                + p.writeWrong
                + ", chests without tile entity "
                + p.chestsMissing);
        Block glass = world.getBlock((A0 << 4) + 17, minY + 40, (A0 << 4) + 1);
        check.check("IDeepRegion writes into a neighbour", glass == Blocks.glass, "" + glass);
        int open = countAir(world, A0 - 1, A0 + 2, minY);
        check.check(
            "chunks outside the generator's area unchanged",
            open == 0,
            open + " air blocks in its chamber band");
        check.check(
            "onDecorated after the chunk is marked decorated",
            p.onDecoratedCalls > 0 && p.onDecoratedTooEarly == 0,
            p.onDecoratedCalls + " calls, " + p.onDecoratedTooEarly + " before the state was set");
        check.check(
            "retrogen flag = chunk saved before its deep layer",
            p.retrogenMismatches == 0,
            p.retrogenCalls + " retrogen calls, " + p.retrogenMismatches + " mismatches");

        // ores off: a registered generator alone still brings chunks to the decorated state
        boolean oresOn = GenConfig.ores;
        GenConfig.ores = false;
        try {
            for (int cx = B - 1; cx <= B + 1; cx++) for (int cz = B - 1; cz <= B + 1; cz++) load(world, cx, cz);
        } finally {
            GenConfig.ores = oresOn;
        }
        Chunk b = world.getChunkFromChunkCoords(B, B);
        int placed = countPlaced(world, B, B, minY);
        check.check(
            "with ores off, generators still get the decoration step",
            ((IDeepChunk) b).bellow$getGenState() == DeepConstants.GEN_DECORATED && p.decorated.contains(key(B, B))
                && placed == 0,
            "state " + ((IDeepChunk) b).bellow$getGenState() + ", " + placed + " ore blocks");

        // a chunk load inside another chunk's population (where World refuses writes below 0 to the 1.7.10
        // generators) still lets generators use the World
        LegacyGeneration.enter();
        try {
            for (int cx = C - 1; cx <= C + 1; cx++) for (int cz = C - 1; cz <= C + 1; cz++) load(world, cx, cz);
        } finally {
            LegacyGeneration.exit();
        }
        Block lapis = world.getBlock((C << 4) + 8, minY + 30, (C << 4) + 8);
        check.check(
            "generators can write through World inside a legacy population",
            Boolean.TRUE.equals(p.worldWriteInLegacyPhase) && lapis == Blocks.lapis_block && !LegacyGeneration.active(),
            "setBlock " + p.worldWriteInLegacyPhase
                + ", block "
                + lapis
                + ", phase restored "
                + !LegacyGeneration.active());

        BellowAPI.resendDeepSections(world, b, DeepConstants.slotBit(-1));
        check.check("resendDeepSections with no player watching", true, "");
    }

    private static void load(WorldServer world, int cx, int cz) {
        world.getChunkProvider()
            .loadChunk(cx, cz);
    }

    /** Blocks other than the base fill, bedrock and air below 0 (ores, tuff, gravel, generator markers). */
    private static int countPlaced(WorldServer world, int cx, int cz, int minY) {
        int n = 0;
        Chunk c = world.getChunkFromChunkCoords(cx, cz);
        for (int y = minY; y < 0; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            Block b = c.getBlock(x, y, z);
            if (b.getMaterial() == Material.air || b == Blocks.bedrock || b == DeepBlocks.fill) continue;
            if (b == Blocks.obsidian || b == Blocks.chest || b == Blocks.glass) continue;
            n++;
        }
        return n;
    }

    private static int countAir(WorldServer world, int cx, int cz, int minY) {
        int n = 0;
        Chunk c = world.getChunkFromChunkCoords(cx, cz);
        for (int y = minY + CHAMBER; y < minY + CHAMBER + 8; y++)
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) if (c.getBlock(x, y, z) == Blocks.air) n++;
        return n;
    }
}
