package com.akashiic.bellow.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.BiomeDictionary;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.gen.ore.DeepFeature;
import com.akashiic.bellow.gen.ore.DeepFeatures;
import com.akashiic.bellow.gen.ore.OreFeature;
import com.akashiic.bellow.storage.IDeepChunkInternal;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * DECORATED stage. A chunk is decorated once it and its 8 neighbours are loaded and have their deep terrain (the 3x3
 * neighbourhood of 1.18 feature placement). Placement origins are inside the centre chunk; the largest vein (tuff,
 * size 64) reaches at most 12.5 blocks out, so it always stays inside the region and nothing is ever clipped
 * horizontally.
 */
public final class DeepDecorator {

    private static List<Resolved> features = new ArrayList<>();

    private DeepDecorator() {}

    /** A feature whose blocks exist in this instance. */
    private static final class Resolved {

        final DeepFeature feature;
        final int index;
        final Block block;
        final int meta;

        Resolved(DeepFeature feature, int index, Block block, int meta) {
            this.feature = feature;
            this.index = index;
            this.block = block;
            this.meta = meta;
        }
    }

    /** Call after {@link DeepBlocks#resolve()}. */
    public static void resolve() {
        List<Resolved> out = new ArrayList<>();
        List<DeepFeature> all = DeepFeatures.OVERWORLD;
        for (int i = 0; i < all.size(); i++) {
            DeepFeature f = all.get(i);
            String[] id = f.block.split(":");
            Block b = GameRegistry.findBlock(id[0], id[1]);
            if (b == null) {
                AkashicBellow.LOG.info("Deep feature {} disabled: block {} is not registered", f.name, f.block);
                continue;
            }
            out.add(new Resolved(f, i, b, f.meta));
        }
        features = out;
    }

    /** Self test only: the ore step fails after placing its veins. */
    public static boolean failOresForTests;

    /** Decorations running now (main thread): more than 0 while one runs, and a chunk it loads would nest another. */
    private static int depth;
    /** Chunk loads that happened during a decoration, tried once it ends. */
    private static final ArrayDeque<Chunk> deferred = new ArrayDeque<>();

    /**
     * Try every chunk whose 3x3 neighbourhood may have just been completed by {@code loaded}. The stage runs when there
     * is something to place: ores, or a registered deep generator.
     * <p>
     * Not reentrant: a chunk loaded from inside a decoration (a generator reading the World past its region) is only
     * queued, and its neighbourhoods are tried when the outer decoration has finished. Decorations never nest, so
     * neither does the stack depth they add to a chunk load.
     */
    public static void tryAround(WorldServer world, Chunk loaded, int minY) {
        if (!GenConfig.ores && !DeepGenerators.any()) return;
        if (depth > 0) {
            deferred.add(loaded);
            return;
        }
        depth++;
        try {
            around(world, loaded, minY);
            Chunk next;
            while ((next = deferred.poll()) != null) {
                WorldServer w = (WorldServer) next.worldObj;
                // still the loaded chunk of its position (it may have been unloaded since)
                if (!w.getChunkProvider()
                    .chunkExists(next.xPosition, next.zPosition)
                    || w.getChunkProvider()
                        .provideChunk(next.xPosition, next.zPosition) != next)
                    continue;
                around(w, next, BellowAPI.getMinY(w));
            }
        } finally {
            depth--;
            // only left over when a fatal error unwinds from a decoration
            if (depth == 0) deferred.clear();
        }
    }

    /** Whether a decoration is running (the self test checks that nested loads are deferred). */
    public static boolean decorating() {
        return depth > 0;
    }

    private static void around(WorldServer world, Chunk loaded, int minY) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                tryDecorate(world, loaded, loaded.xPosition + dx, loaded.zPosition + dz, minY);
            }
        }
    }

    private static Chunk loaded(WorldServer world, Chunk hint, int x, int z) {
        if (hint.xPosition == x && hint.zPosition == z) return hint;
        IChunkProvider provider = world.getChunkProvider();
        return provider.chunkExists(x, z) ? provider.provideChunk(x, z) : null;
    }

    private static void tryDecorate(WorldServer world, Chunk hint, int cx, int cz, int minY) {
        Chunk centre = loaded(world, hint, cx, cz);
        if (centre == null || ((IDeepChunk) centre).bellow$getGenState() != DeepConstants.GEN_TERRAIN) return;
        Chunk[] region = new Chunk[9];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Chunk c = loaded(world, hint, cx + dx, cz + dz);
                if (c == null || ((IDeepChunk) c).bellow$getGenState() < DeepConstants.GEN_TERRAIN) return;
                region[(dz + 1) * 3 + dx + 1] = c;
            }
        }
        DeepRegion view = new DeepRegion(world, world.getSeed(), region, cx, cz, minY);
        // claimed before decorating: a chunk load from inside the stage that completes this neighbourhood again must
        // not
        // decorate it a second time. The claim stands whatever happens below (a retry would place the same veins twice)
        ((IDeepChunkInternal) centre).bellow$setGenState(DeepConstants.GEN_DECORATED);
        boolean generators = DeepGenerators.anyFor(world);
        try {
            if (GenConfig.ores) {
                try {
                    placeFeatures(world, view, world.getSeed(), cx, cz, minY);
                    if (failOresForTests) throw new IllegalStateException("self test: ore step failure");
                } catch (Throwable e) {
                    DeepGenerators.rethrowFatal(e);
                    // what was placed is kept, the generators still run, the chunk loads normally
                    AkashicBellow.LOG.error("Deep ores of chunk [{}, {}] failed; keeping what was placed", cx, cz, e);
                }
            }
            // after the ores, like 1.18 features after its ore features; their writes are sent with the ores'
            DeepGenerators.decorate(view, centre);
        } finally {
            // whatever was written is saved and sent, even when a step failed
            centre.setChunkModified();
            for (int i = 0; i < 9; i++) {
                int slots = view.touchedSlots(i);
                if (slots == 0) continue;
                Chunk c = view.chunk(i);
                c.setChunkModified();
                DeepStages.resend(world, c, slots);
            }
            if (generators) {
                // what generators placed is lit once the chunks around are decorated too (see DeepStages)
                ((IDeepChunkInternal) centre).bellow$setLightPending(true);
                DeepStages.queue(world, centre);
            }
        }
    }

    private static void placeFeatures(WorldServer world, DeepRegion view, long seed, int cx, int cz, int minY) {
        Random random = new Random();
        int baseX = cx << 4;
        int baseZ = cz << 4;
        Chunk centre = view.chunk(4);

        for (Resolved r : features) {
            DeepFeature f = r.feature;
            random.setSeed(PositionalRandom.featureSeed(seed, cx, cz, r.index));
            int attempts = f.attempts(random, minY, GenConfig.scaleBottomAnchoredCounts, GenConfig.oreCountMultiplier);
            if (attempts == 0) continue;
            if (f.kind == DeepFeature.Kind.BLOB) {
                view.useBlob(r.block, r.meta);
            } else {
                DeepBlocks.BlockAndMeta deep = DeepBlocks.deepVariant(r.block, r.meta);
                view.useOre(r.block, r.meta, deep.block, deep.meta);
            }
            for (int a = 0; a < attempts; a++) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = f.height.sample(random, minY);
                if (f.mountainsOnly
                    && !isMountain(centre.getBiomeGenForWorldCoords(x & 15, z & 15, world.getWorldChunkManager())))
                    continue;
                // Like 1.18, every origin is placed and blocks are clipped (DeepRegion writes only minY..-1, Y >= 0
                // belongs to the 1.7.10 generators of the pack); only veins that cannot reach that band are skipped.
                if (!reachesDeepBand(y, f.size, minY)) continue;
                OreFeature.place(random, x, y, z, f.size, f.discardOnAir, view);
            }
        }
    }

    /** Whether a vein of {@code size} with its origin at {@code y} can put blocks in {@code [minY, -1]}. */
    static boolean reachesDeepBand(int y, int size, int minY) {
        int reach = OreFeature.verticalReach(size);
        return y - reach <= -1 && y + reach >= minY;
    }

    private static boolean isMountain(BiomeGenBase biome) {
        return biome != null && BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.MOUNTAIN);
    }
}
