package com.akashiic.bellow.selftest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.api.IDeepGenerator;
import com.akashiic.bellow.api.IDeepRegion;
import com.akashiic.bellow.gen.DeepDecorator;
import com.akashiic.bellow.gen.DeepStages;
import com.akashiic.bellow.gen.StrictGuard;
import com.akashiic.bellow.storage.DeepDataManager;
import com.akashiic.bellow.world.DeepWorlds;
import com.akashiic.bellow.world.LegacyGeneration;

/**
 * Self-test of the deep generator API (phase {@code full}; persistence in phase {@code verify}). Two test generators
 * are registered during init: this one (priority 0) and {@link Second} (priority 10, registered first, so the order
 * follows the priority). Each area below is loaded for one guarantee of {@link IDeepGenerator}; elsewhere the
 * generators only record the calls.
 * <ul>
 * <li>A, chunks 2000..2004 (loaded with a ring: 1999..2005): carving, decoration through {@link IDeepRegion},
 * between, the stages after the tick, the light stage (a glowstone in each chamber; only 2002,2002 has its 5x5
 * decorated);</li>
 * <li>C 2201: loaded inside a legacy population; D 2301: a neighbour of a newer format;</li>
 * <li>E 2401: a failing ore step and a failing generator; G 2501 / H 2510,2501: a chunk loaded from inside a
 * decoration;</li>
 * <li>S 2601: strict mode;</li>
 * <li>phase {@code verify} (fresh JVM, only {@link Second} registered): records, data and the light flag of the absent
 * generator are kept; the ring around 2000,2000 is loaded, so its light stage and {@code onLit}, due since the full
 * phase, run now.</li>
 * </ul>
 */
public final class GeneratorProbe implements IDeepGenerator {

    static final String ID = "akashicbellow:selftest";
    static final String ID2 = "akashicbellow:selftest2";
    static final String VERSION = "probe-1";
    private static final GeneratorProbe INSTANCE = new GeneratorProbe();
    private static final Second SECOND = new Second();

    private static final int A0 = 2000, A1 = 2004; // carving area (chunks)
    private static final int AC = 2002; // the one chunk of A whose 5x5 is decorated
    private static final int C = 2201; // centre chunk of the area loaded inside a (simulated) legacy population
    private static final int D = 2301; // centre chunk with a neighbour (D + 1, D) of a newer format
    private static final int E = 2401; // centre chunk whose ore step and first generator fail
    private static final int G = 2501, H = 2510; // decoration of G loads the chunk that completes H's region
    private static final int S = 2601; // strict mode
    private static final int CHAMBER = 20; // chamber floor, above the floor of the world

    private final Map<Long, Integer> terrainCalls = new HashMap<>();
    private int retrogenCalls;
    private int retrogenMismatches;
    private final Set<Long> decorated = new HashSet<>();
    private final List<Long> decorationOrder = new ArrayList<>();
    private int decorateTwice;
    private int decoratedBeforeNeighbourTerrain;
    private int chamberFilledBeforeDecorate;
    private final List<int[]> regionChests = new ArrayList<>();
    private int writeOk, writeRefused, writeWrong;
    private int chestsMissing;
    private int depth, nested;
    private final Map<Long, Integer> onDecorated = new HashMap<>();
    private int onDecoratedTooEarly, onDecoratedInDecoration;
    private final Map<Long, Integer> onLit = new HashMap<>();
    private int onLitUnlit, onLitRingMissing;
    private Boolean worldWriteInLegacyPhase;
    private Boolean newerNeighbourWrite;
    private int strictTerrainWrites, strictTerrainAccepted;
    private final Boolean[] strictDecorate = new Boolean[3];

    private GeneratorProbe() {}

    /** Phase {@code verify}: only the second generator is installed. */
    public static void registerSecondOnly() {
        BellowAPI.registerGenerator(ID2, SECOND, 10);
    }

    public static void register() {
        // registered first, runs second: the order is the priority, not the registration
        BellowAPI.registerGenerator(ID2, SECOND, 10);
        BellowAPI.registerGenerator(ID, INSTANCE);
    }

    private static long key(int cx, int cz) {
        return ChunkCoordIntPair.chunkXZ2Int(cx, cz);
    }

    private static boolean inArea(int cx, int cz) {
        return cx >= A0 && cx <= A1 && cz >= A0 && cz <= A1;
    }

    private static boolean near(int cx, int cz, int x0, int z0) {
        return Math.abs(cx - x0) <= 1 && Math.abs(cz - z0) <= 1;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {
        this.terrainCalls.merge(key(chunk.xPosition, chunk.zPosition), 1, Integer::sum);
        if (retrogen) this.retrogenCalls++;
        if (retrogen != ((IDeepChunk) chunk).bellow$isFromDisk()) this.retrogenMismatches++;
        if (near(chunk.xPosition, chunk.zPosition, S, S)) {
            // a World write from the terrain step: refused in strict mode
            this.strictTerrainWrites++;
            if (chunk.worldObj
                .setBlock((chunk.xPosition << 4) + 1, 100, (chunk.zPosition << 4) + 1, Blocks.glass, 0, 2))
                this.strictTerrainAccepted++;
        }
        if (!inArea(chunk.xPosition, chunk.zPosition)) return;
        for (int y = minY + CHAMBER; y < minY + CHAMBER + 8; y++) {
            ExtendedBlockStorage ebs = BellowAPI.getDeepSection(chunk, y >> 4);
            for (int x = 4; x < 12; x++) for (int z = 4; z < 12; z++) ebs.func_150818_a(x, y & 15, z, Blocks.air);
        }
    }

    @Override
    public void decorate(IDeepRegion region) {
        if (this.depth > 0) this.nested++;
        this.depth++;
        try {
            this.decorateCentre(region, region.centerChunkX(), region.centerChunkZ());
        } finally {
            this.depth--;
        }
    }

    private void decorateCentre(IDeepRegion region, int cx, int cz) {
        if (!this.decorated.add(key(cx, cz))) this.decorateTwice++;
        this.decorationOrder.add(key(cx, cz));
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (!this.terrainCalls.containsKey(key(cx + dx, cz + dz))) this.decoratedBeforeNeighbourTerrain++;
        }
        int minY = region.minY(), bx = cx << 4, bz = cz << 4;
        BellowAPI.getGeneratorData(region.chunkAt(bx, bz), ID)
            .setInteger("probe", cx);

        if (cx == D && cz == D) {
            // the neighbour at +x has deep data of a newer format: read-only
            this.newerNeighbourWrite = region.setBlock(bx + 20, minY + 40, bz + 4, Blocks.glass, 0);
        }
        if (cx == E && cz == E) {
            region.setBlock(bx + 3, minY + 40, bz + 17, Blocks.glass, 0); // into the neighbour at +z
            throw new IllegalStateException("self test: generator failure after writing");
        }
        if (cx == G && cz == G) {
            // a World read past the region loads the last chunk of H's region: H must not be decorated in here
            region.world()
                .getBlock((H + 1) << 4, 10, (G + 1) << 4);
        }
        if (cx == S && cz == S) {
            WorldServer w = region.world();
            this.strictDecorate[0] = w.setBlock(bx + 3, 100, bz + 3, Blocks.glass, 0, 2); // above 0: refused
            this.strictDecorate[1] = w.setBlock(bx + 3, minY + 45, bz + 3, Blocks.glass, 0, 2); // inside: allowed
            this.strictDecorate[2] = w.setBlock(bx + 40, minY + 45, bz + 3, Blocks.glass, 0, 2); // outside: refused
        }
        if (!inArea(cx, cz)) return;
        // the terrain step's carving is intact when decoration starts
        if (countSolid(region, bx, bz, minY) != 0) this.chamberFilledBeforeDecorate++;

        // a chest in each of the 9 chunks, so also in the one whose load completed this region (its tile entities are
        // registered by the end of its onChunkLoad): one position per (chunk, centre) pair, away from the chambers
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int tx = ((cx + dx) << 4) + 2 + 2 * (1 - dx), tz = ((cz + dz) << 4) + 2 + 2 * (1 - dz);
            if (region.setBlock(tx, minY + 50, tz, Blocks.chest, 0))
                this.regionChests.add(new int[] { tx, minY + 50, tz });
        }

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
        // a light source, lit by the light stage
        region.setBlock(bx + 10, y + 1, bz + 10, Blocks.glowstone, 0);
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
        this.onDecorated.merge(key(chunk.xPosition, chunk.zPosition), 1, Integer::sum);
        if (!BellowAPI.isDecorated(chunk)) this.onDecoratedTooEarly++;
        if (DeepDecorator.decorating()) this.onDecoratedInDecoration++;
        if (chunk.xPosition == C && chunk.zPosition == C) {
            int minY = BellowAPI.getMinY(world);
            this.worldWriteInLegacyPhase = world
                .setBlock((C << 4) + 8, minY + 30, (C << 4) + 8, Blocks.lapis_block, 0, 2);
        }
    }

    @Override
    public void onLit(WorldServer world, Chunk chunk) {
        this.onLit.merge(key(chunk.xPosition, chunk.zPosition), 1, Integer::sum);
        if (BellowAPI.isLightPending(chunk)) this.onLitUnlit++;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            if (!world.getChunkProvider()
                .chunkExists(chunk.xPosition + dx, chunk.zPosition + dz)) this.onLitRingMissing++;
        }
    }

    /** The second generator: records which chunks it decorated (it runs after a failing first one in E). */
    static final class Second implements IDeepGenerator {

        final Set<Long> decorated = new HashSet<>();
        final List<String> order = new ArrayList<>();
        final Map<Long, Integer> lit = new HashMap<>();

        @Override
        public void onLit(WorldServer world, Chunk chunk) {
            this.lit.merge(key(chunk.xPosition, chunk.zPosition), 1, Integer::sum);
        }

        @Override
        public void decorate(IDeepRegion region) {
            this.decorated.add(key(region.centerChunkX(), region.centerChunkZ()));
            if (region.centerChunkX() == A0 && region.centerChunkZ() == A0) {
                this.order.add(INSTANCE.decorated.contains(key(A0, A0)) ? "after" : "before");
            }
        }
    }

    private static int countSolid(IDeepRegion region, int bx, int bz, int minY) {
        int solid = 0;
        for (int y = minY + CHAMBER; y < minY + CHAMBER + 8; y++) for (int x = 4; x < 12; x++)
            for (int z = 4; z < 12; z++) if (region.getBlock(bx + x, y, bz + z) != Blocks.air) solid++;
        return solid;
    }

    /** Loads the areas (the world must not have them yet) and checks everything recorded. */
    public static void run(WorldServer world, int minY, BukkitProbe.Check check) {
        GeneratorProbe p = INSTANCE;
        List<String> ids = BellowAPI.getGeneratorIds();
        check.check(
            "generators registered, ordered by priority",
            ids.indexOf(ID) >= 0 && ids.indexOf(ID) < ids.indexOf(ID2),
            "" + ids);
        if (p.terrainCalls.containsKey(key(A0, A0))) {
            check.check("generator probe", true, "already ran in this session (needs a new world)");
            return;
        }
        List<String> recorded = DeepWorlds.recordedGenerators(world);
        check.check(
            "generators of the dimension recorded in akashicbellow.dat",
            Arrays.asList(TestLayer.ID, ID + "@" + VERSION, ID2)
                .equals(recorded),
            "" + recorded);
        DeepStages.drain(world); // what earlier checks queued
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
            "generator decoration step: once, after the terrain of all 9 chunks, never nested",
            notDecorated == 0 && p.decorateTwice == 0 && p.decoratedBeforeNeighbourTerrain == 0 && p.nested == 0,
            "not decorated " + notDecorated
                + ", twice "
                + p.decorateTwice
                + ", before a neighbour's terrain "
                + p.decoratedBeforeNeighbourTerrain
                + ", nested "
                + p.nested);
        check.check(
            "carved space intact when decoration starts",
            p.chamberFilledBeforeDecorate == 0,
            p.chamberFilledBeforeDecorate + " chambers with blocks when decoration started");
        check.check(
            "generators run by priority on each chunk",
            SECOND.order.equals(Collections.singletonList("after")),
            "the second generator ran " + SECOND.order + " the first on chunk " + A0 + "," + A0);

        // the stages after the tick have not run yet: sections wait to be sent (merged per chunk), callbacks wait
        Chunk ac = world.getChunkFromChunkCoords(AC, AC);
        int pendingMask = DeepStages.pendingResend(world, ac);
        int chamberBit = DeepConstants.slotBit((minY + CHAMBER) >> 4);
        int early = 0;
        for (int cx = A0 - 1; cx <= A1 + 1; cx++) for (int cz = A0 - 1; cz <= A1 + 1; cz++) {
            if (p.onDecorated.containsKey(key(cx, cz)) || p.onLit.containsKey(key(cx, cz))) early++;
        }
        check.check(
            "decoration changes wait for the end of the tick, merged per chunk",
            (pendingMask & chamberBit) != 0 && early == 0,
            "sections of " + AC
                + ","
                + AC
                + " to send: "
                + Integer.toBinaryString(pendingMask)
                + "; chunks of the area with a callback before the tick: "
                + early);
        DeepStages.drain(world);

        // after the whole area is decorated: chamber still open, markers in place
        int solid = 0, obsidian = 0, chests = 0, glowstone = 0;
        for (int cx = A0; cx <= A1; cx++) for (int cz = A0; cz <= A1; cz++) {
            int bx = cx << 4, bz = cz << 4;
            for (int y = minY + CHAMBER; y < minY + CHAMBER + 8; y++)
                for (int x = 4; x < 12; x++) for (int z = 4; z < 12; z++) {
                    Block b = world.getBlock(bx + x, y, bz + z);
                    if (b == Blocks.obsidian) obsidian++;
                    else if (b == Blocks.chest) chests++;
                    else if (b == Blocks.glowstone) glowstone++;
                    else if (b != Blocks.air) solid++;
                }
        }
        check.check(
            "carved chamber stays open in the world",
            solid == 0 && obsidian == 25 && chests == 25 && glowstone == 25,
            "25 chambers: " + solid
                + " stray blocks, "
                + obsidian
                + " obsidian, "
                + chests
                + " chests, "
                + glowstone
                + " glowstone");
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
        // the chests placed during decoration tick once: registered with the world once, not again by onChunkLoad
        Map<Object, Integer> ticking = new IdentityHashMap<>();
        for (Object te : world.loadedTileEntityList) ticking.merge(te, 1, Integer::sum);
        int chestsChecked = 0, notOnce = 0;
        for (int[] pos : p.regionChests) {
            Object chest = world.getTileEntity(pos[0], pos[1], pos[2]);
            chestsChecked++;
            if (!(chest instanceof TileEntityChest) || ticking.getOrDefault(chest, 0) != 1) notOnce++;
        }
        check.check(
            "tile entities placed during decoration tick once",
            chestsChecked == 25 * 9 && notOnce == 0,
            chestsChecked + " chests in the 9 chunks of each region, "
                + notOnce
                + " not in the ticking list exactly once");

        Block glass = world.getBlock((A0 << 4) + 17, minY + 40, (A0 << 4) + 1);
        check.check("IDeepRegion writes into a neighbour", glass == Blocks.glass, "" + glass);
        int open = countAir(world, A0 - 1, A0 + 2, minY);
        check.check(
            "chunks outside the generator's area unchanged",
            open == 0,
            open + " air blocks in its chamber band");

        // onDecorated: after the tick, once per decorated chunk, never inside a decoration
        int notOnceCalled = 0;
        for (long k : p.decorated) if (p.onDecorated.getOrDefault(k, 0) != 1) notOnceCalled++;
        check.check(
            "onDecorated after the tick, once per decorated chunk",
            notOnceCalled == 0 && p.onDecoratedTooEarly == 0 && p.onDecoratedInDecoration == 0,
            p.decorated.size() + " decorated chunks, "
                + notOnceCalled
                + " not called exactly once, "
                + p.onDecoratedTooEarly
                + " before the state was set, "
                + p.onDecoratedInDecoration
                + " inside a decoration");

        lightStage(world, minY, check, p, ac);
        check.check(
            "retrogen flag = chunk saved before its deep layer",
            p.retrogenMismatches == 0,
            p.retrogenCalls + " retrogen calls, " + p.retrogenMismatches + " mismatches");

        legacyPhase(world, minY, check, p);
        newerNeighbour(world, minY, check, p);
        failures(world, minY, check);
        nestedLoad(world, check, p);
        strict(world, minY, check, p);

        BellowAPI.resendDeepSections(world, world.getChunkFromChunkCoords(C, C), DeepConstants.slotBit(-1));
        check.check("resendDeepSections with no player watching", true, "");
    }

    private static void lightStage(WorldServer world, int minY, BukkitProbe.Check check, GeneratorProbe p, Chunk ac) {
        // only the chunk whose 5x5 is decorated is lit, from its own sources
        int y = minY + CHAMBER + 1;
        int litValue = world.getSavedLightValue(EnumSkyBlock.Block, (AC << 4) + 10, y, (AC << 4) + 10);
        int nextValue = world.getSavedLightValue(EnumSkyBlock.Block, (AC << 4) + 10, y, (AC << 4) + 9);
        int edgeValue = world.getSavedLightValue(EnumSkyBlock.Block, (A0 << 4) + 10, y, (A0 << 4) + 9);
        boolean pendingCentre = BellowAPI.isLightPending(ac);
        boolean pendingEdge = BellowAPI.isLightPending(world.getChunkFromChunkCoords(A0, A0));
        check.check(
            "light stage lights generator light sources once the 5x5 is decorated",
            litValue == 15 && nextValue == 14 && !pendingCentre && edgeValue == 0 && pendingEdge,
            "glowstone in " + AC
                + ","
                + AC
                + ": "
                + litValue
                + ", next to it "
                + nextValue
                + ", pending "
                + pendingCentre
                + "; next to the one in "
                + A0
                + ","
                + A0
                + " (5x5 not decorated): "
                + edgeValue
                + ", pending "
                + pendingEdge);
        // the World's own light engine agrees with the light stage: recomputing changes nothing
        int changed = 0, checked = 0;
        for (int wy = minY + CHAMBER; wy < minY + CHAMBER + 8; wy++)
            for (int x = 4; x < 12; x++) for (int z = 4; z < 12; z++) {
                int wx = (AC << 4) + x, wz = (AC << 4) + z;
                int before = world.getSavedLightValue(EnumSkyBlock.Block, wx, wy, wz);
                world.updateLightByType(EnumSkyBlock.Block, wx, wy, wz);
                if (world.getSavedLightValue(EnumSkyBlock.Block, wx, wy, wz) != before) changed++;
                checked++;
            }
        check.check(
            "light stage matches the World's light engine",
            changed == 0,
            changed + " of " + checked + " chamber blocks changed when the World recomputed their light");
        int litCalls = 0;
        for (int n : p.onLit.values()) litCalls += n;
        check.check(
            "onLit after the light stage, once, with the 5x5 loaded",
            p.onLit.getOrDefault(key(AC, AC), 0) == 1 && !p.onLit.containsKey(key(A0, A0))
                && p.onLitUnlit == 0
                && p.onLitRingMissing == 0,
            litCalls + " calls ("
                + AC
                + ","
                + AC
                + ": "
                + p.onLit.getOrDefault(key(AC, AC), 0)
                + "), "
                + p.onLitUnlit
                + " before the light, "
                + p.onLitRingMissing
                + " missing neighbours");
        int stages = BellowAPI.getGeneratorStages(ac, ID);
        int stages2 = BellowAPI.getGeneratorStages(ac, ID2);
        int edgeStages = BellowAPI.getGeneratorStages(world.getChunkFromChunkCoords(A0, A0), ID);
        check.check(
            "generator stages and data recorded per chunk",
            stages == 15 && stages2 == 15
                && edgeStages == 7
                && BellowAPI.getGeneratorData(ac, ID)
                    .getInteger("probe") == AC,
            "stages " + stages
                + "/"
                + stages2
                + " at "
                + AC
                + ","
                + AC
                + ", "
                + edgeStages
                + " at "
                + A0
                + ","
                + A0
                + ", data "
                + BellowAPI.getGeneratorData(ac, ID));
    }

    private static void legacyPhase(WorldServer world, int minY, BukkitProbe.Check check, GeneratorProbe p) {
        // inside another chunk's population World writes below 0 are refused to the 1.7.10 generators; the generator
        // callbacks still get to use the World (they leave that phase)
        LegacyGeneration.enter();
        try {
            for (int cx = C - 1; cx <= C + 1; cx++) for (int cz = C - 1; cz <= C + 1; cz++) load(world, cx, cz);
            DeepStages.drain(world);
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
    }

    private static void newerNeighbour(WorldServer world, int minY, BukkitProbe.Check check, GeneratorProbe p) {
        // the neighbour at +x is loaded first and marked as a chunk of a newer deep format (read-only), then the rest
        Chunk newer = load(world, D + 1, D);
        int before = countPlaced(world, D + 1, D, minY);
        DeepDataManager.markNewerForTests(newer, true);
        try {
            for (int cx = D - 1; cx <= D + 1; cx++) for (int cz = D - 1; cz <= D + 1; cz++) load(world, cx, cz);
            int after = countPlaced(world, D + 1, D, minY);
            check.check(
                "a neighbour of a newer format is not written by the decoration",
                p.decorated.contains(key(D, D)) && Boolean.FALSE.equals(p.newerNeighbourWrite) && after == before,
                "decorated " + p.decorated.contains(key(D, D))
                    + ", generator write "
                    + p.newerNeighbourWrite
                    + ", blocks placed in it "
                    + (after - before));
        } finally {
            DeepDataManager.markNewerForTests(newer, false);
        }
        DeepStages.drain(world);
    }

    private static void failures(WorldServer world, int minY, BukkitProbe.Check check) {
        // E: the first generator throws after writing into a neighbour; the second generator still runs, and what was
        // written is saved and sent
        for (int cx = E - 1; cx <= E + 1; cx++) for (int cz = E - 1; cz <= E + 1; cz++) {
            if (cx != E || cz != E + 1) load(world, cx, cz);
        }
        // no drain here: a mod generator populating these chunks may load E, E + 1 itself, decorating E already, and a
        // drain would send the queued sections before they are checked (the glass section is queued by this decoration
        // only, and no tick runs between these loads)
        boolean early = BellowAPI.isDecorated(world.getChunkFromChunkCoords(E, E));
        load(world, E, E + 1); // completes E's region
        Chunk centre = world.getChunkFromChunkCoords(E, E);
        Chunk neighbour = world.getChunkFromChunkCoords(E, E + 1);
        int glassBit = DeepConstants.slotBit((minY + 40) >> 4);
        int sent = DeepStages.pendingResend(world, neighbour);
        boolean glass = world.getBlock((E << 4) + 3, minY + 40, (E << 4) + 17) == Blocks.glass;
        int stages = BellowAPI.getGeneratorStages(centre, ID);
        check.check(
            "a failing generator keeps, saves and sends what was written",
            BellowAPI.isDecorated(centre) && glass
                && (sent & glassBit) != 0
                && neighbour.isModified
                && SECOND.decorated.contains(key(E, E))
                && (stages & DeepConstants.STAGE_DECORATE) != 0,
            "glass " + glass
                + ", neighbour sections queued "
                + Integer.toBinaryString(sent)
                + ", neighbour modified "
                + neighbour.isModified
                + ", second generator ran "
                + SECOND.decorated.contains(key(E, E))
                + ", stages "
                + stages
                + (early ? ", decorated before its region was completed here" : ""));
        DeepStages.drain(world);
    }

    private static void nestedLoad(WorldServer world, BukkitProbe.Check check, GeneratorProbe p) {
        // H's region (H-1..H+1 x G-1..G+1) is loaded except its corner (H+1, G+1); G's decoration loads that corner
        for (int cx = H - 1; cx <= H + 1; cx++) for (int cz = G - 1; cz <= G + 1; cz++) {
            if (cx != H + 1 || cz != G + 1) load(world, cx, cz);
        }
        int nestedBefore = p.nested;
        for (int cx = G - 1; cx <= G + 1; cx++) for (int cz = G - 1; cz <= G + 1; cz++) load(world, cx, cz);
        int g = p.decorationOrder.indexOf(key(G, G)), h = p.decorationOrder.indexOf(key(H, G));
        check.check(
            "a chunk loaded inside a decoration is decorated after it, not inside",
            g >= 0 && h > g && p.nested == nestedBefore && !DeepDecorator.decorating(),
            "G at " + g + ", H at " + h + " in decoration order, nested " + (p.nested - nestedBefore));
        DeepStages.drain(world);
    }

    private static void strict(WorldServer world, int minY, BukkitProbe.Check check, GeneratorProbe p) {
        boolean was = GenConfig.strictGenerators;
        GenConfig.strictGenerators = true;
        int violations = StrictGuard.violations();
        try {
            for (int cx = S - 1; cx <= S + 1; cx++) for (int cz = S - 1; cz <= S + 1; cz++) load(world, cx, cz);
        } finally {
            GenConfig.strictGenerators = was;
        }
        int glassAbove = 0;
        for (int cx = S - 1; cx <= S + 1; cx++) for (int cz = S - 1; cz <= S + 1; cz++) {
            if (world.getBlock((cx << 4) + 1, 100, (cz << 4) + 1) == Blocks.glass) glassAbove++;
        }
        if (world.getBlock((S << 4) + 3, 100, (S << 4) + 3) == Blocks.glass) glassAbove++;
        int refused = StrictGuard.violations() - violations;
        check.check(
            "strict mode refuses World writes the generator contract forbids",
            p.strictTerrainWrites == 9 && p.strictTerrainAccepted == 0
                && Boolean.FALSE.equals(p.strictDecorate[0])
                && Boolean.TRUE.equals(p.strictDecorate[1])
                && Boolean.FALSE.equals(p.strictDecorate[2])
                && refused >= 11
                && glassAbove == 0,
            "terrain writes accepted " + p.strictTerrainAccepted
                + "/"
                + p.strictTerrainWrites
                + ", decorate (Y 100, inside, outside) "
                + Arrays.toString(p.strictDecorate)
                + ", refused "
                + refused
                + ", glass above 0: "
                + glassAbove);
        DeepStages.drain(world);
    }

    /** Phase {@code verify} (fresh JVM, only the second generator registered): what the full phase left on disk. */
    public static void verify(WorldServer world, int minY, BukkitProbe.Check check) {
        Chunk ac = load(world, AC, AC);
        Chunk a0 = load(world, A0, A0);
        int lit = world.getSavedLightValue(EnumSkyBlock.Block, (AC << 4) + 10, minY + CHAMBER + 1, (AC << 4) + 10);
        boolean pending = BellowAPI.isLightPending(a0);
        int stages2 = BellowAPI.getGeneratorStages(a0, ID2);
        check.check(
            "generator records and data survive a restart without the generator",
            BellowAPI.getGeneratorStages(ac, ID) == 15 && BellowAPI.getGeneratorStages(a0, ID) == 7
                && BellowAPI.getGeneratorData(ac, ID)
                    .getInteger("probe") == AC
                && !BellowAPI.getGeneratorIds()
                    .contains(ID),
            "stages " + BellowAPI.getGeneratorStages(ac, ID)
                + " / "
                + BellowAPI.getGeneratorStages(a0, ID)
                + ", data "
                + BellowAPI.getGeneratorData(ac, ID));
        check.check(
            "light stage state survives a restart",
            lit == 15 && !BellowAPI.isLightPending(ac) && pending && stages2 == 7,
            "light " + lit + ", pending " + BellowAPI.isLightPending(ac) + " / " + pending + ", stages " + stages2);
        List<String> recorded = DeepWorlds.recordedGenerators(world);
        check.check(
            "a changed generator list is recorded (and logged)",
            Arrays.asList(TestLayer.ID, ID2)
                .equals(recorded),
            "" + recorded);

        // the 5x5 around 2000,2000 decorated (its ring 1998.. needs 1997.. loaded): the light and onLit due since the
        // full phase run now, for the generator that is installed
        DeepStages.drain(world);
        int litBefore = SECOND.lit.getOrDefault(key(A0, A0), 0);
        for (int cx = A0 - 3; cx <= A0 + 3; cx++) for (int cz = A0 - 3; cz <= A0 + 3; cz++) load(world, cx, cz);
        DeepStages.drain(world);
        int edge = world.getSavedLightValue(EnumSkyBlock.Block, (A0 << 4) + 10, minY + CHAMBER + 1, (A0 << 4) + 9);
        check.check(
            "stages still due after a restart run once the chunks around load",
            litBefore == 0 && SECOND.lit.getOrDefault(key(A0, A0), 0) == 1
                && !BellowAPI.isLightPending(a0)
                && edge == 14
                && BellowAPI.getGeneratorStages(a0, ID2) == 15
                && BellowAPI.getGeneratorStages(a0, ID) == 7,
            "onLit " + litBefore
                + " -> "
                + SECOND.lit.getOrDefault(key(A0, A0), 0)
                + ", pending "
                + BellowAPI.isLightPending(a0)
                + ", light next to the glowstone "
                + edge
                + ", stages "
                + BellowAPI.getGeneratorStages(a0, ID2)
                + " (absent generator: "
                + BellowAPI.getGeneratorStages(a0, ID)
                + ")");
    }

    private static Chunk load(WorldServer world, int cx, int cz) {
        return world.getChunkProvider()
            .loadChunk(cx, cz);
    }

    /** Blocks other than the test layer's stone, bedrock and air below 0 (what a decoration placed). */
    private static int countPlaced(WorldServer world, int cx, int cz, int minY) {
        int n = 0;
        Chunk c = world.getChunkFromChunkCoords(cx, cz);
        for (int y = minY; y < 0; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            Block b = c.getBlock(x, y, z);
            if (b.getMaterial() == Material.air || b == Blocks.bedrock || b == Blocks.stone) continue;
            if (b == Blocks.obsidian || b == Blocks.chest || b == Blocks.glass || b == Blocks.glowstone) continue;
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
