package com.akashiic.bellow.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.BiomeDictionary;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.GenConfig;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;
import com.akashiic.bellow.gen.ore.DeepFeature;
import com.akashiic.bellow.gen.ore.DeepFeatures;
import com.akashiic.bellow.gen.ore.OreFeature;
import com.akashiic.bellow.mixin.early.common.PlayerManagerAccessor;
import com.akashiic.bellow.net.IDeepPlayerInstance;

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

    /** Try every chunk whose 3x3 neighbourhood may have just been completed by {@code loaded}. */
    public static void tryAround(WorldServer world, Chunk loaded, int minY) {
        if (!GenConfig.ores) return;
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
        decorate(world, region, cx, cz, minY);
        ((IDeepChunk) centre).bellow$setGenState(DeepConstants.GEN_DECORATED);
        centre.setChunkModified();
    }

    static void decorate(WorldServer world, Chunk[] region, int cx, int cz, int minY) {
        DeepRegion view = new DeepRegion(region, cx, cz, minY);
        long seed = world.getSeed();
        Random random = new Random();
        int baseX = cx << 4;
        int baseZ = cz << 4;
        Chunk centre = region[4];

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

        for (int i = 0; i < 9; i++) {
            int slots = view.touchedSlots(i);
            if (slots == 0) continue;
            Chunk c = view.chunk(i);
            c.setChunkModified();
            resendToWatchers(world, c, slots);
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

    /** Players that already received the chunk get the changed deep sections again. */
    private static void resendToWatchers(WorldServer world, Chunk chunk, int slots) {
        LongHashMap instances = ((PlayerManagerAccessor) world.getPlayerManager()).bellow$getPlayerInstances();
        long key = (long) chunk.xPosition + 2147483647L | (long) chunk.zPosition + 2147483647L << 32;
        Object instance = instances.getValueByKey(key);
        if (instance != null) ((IDeepPlayerInstance) instance).bellow$resendDeep(slots);
    }
}
