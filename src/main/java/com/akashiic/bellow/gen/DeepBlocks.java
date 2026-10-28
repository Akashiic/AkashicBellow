package com.akashiic.bellow.gen;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.akashiic.bellow.AkashicBellow;
import com.akashiic.bellow.GenConfig;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Blocks used by the deep layer, resolved after every mod registered its blocks. Et Futurum Requiem is optional and
 * accessed by registry name and reflection only.
 */
public final class DeepBlocks {

    public static Block fill = Blocks.stone;
    public static int fillMeta;
    /** Null when Et Futurum's tuff is not available. */
    public static Block tuff;
    /** Null when Et Futurum's copper is not available. */
    public static Block copperOre;

    private static Method getOre;
    private static Method mappingObject;
    private static Method mappingMeta;
    private static final Map<Long, BlockAndMeta> DEEP_ORE_CACHE = new HashMap<>();

    private DeepBlocks() {}

    public static void resolve() {
        String spec = GenConfig.deepBlock == null ? "auto" : GenConfig.deepBlock.trim();
        Block efrDeepslate = GameRegistry.findBlock("etfuturum", "deepslate");
        if ("auto".equalsIgnoreCase(spec)) {
            fill = efrDeepslate != null ? efrDeepslate : Blocks.stone;
            fillMeta = 0;
        } else {
            String[] parts = spec.split(":");
            Block b = parts.length >= 2 ? GameRegistry.findBlock(parts[0], parts[1]) : null;
            if (b == null) {
                AkashicBellow.LOG.error("worldgen.deepBlock '{}' not found; using minecraft:stone", spec);
                b = Blocks.stone;
            }
            fill = b;
            fillMeta = 0;
            if (parts.length >= 3) {
                try {
                    fillMeta = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    AkashicBellow.LOG.error("worldgen.deepBlock '{}': metadata is not a number; using 0", spec);
                }
            }
            if (fillMeta < 0) { // EndlessIDs allows metadata above 15, so only negative values are rejected
                AkashicBellow.LOG.error("worldgen.deepBlock '{}': negative metadata; using 0", spec);
                fillMeta = 0;
            }
        }
        tuff = GameRegistry.findBlock("etfuturum", "tuff");
        copperOre = GameRegistry.findBlock("etfuturum", "copper_ore");
        try {
            Class<?> registry = Class.forName("ganymedes01.etfuturum.api.DeepslateOreRegistry");
            Class<?> mapping = Class.forName("ganymedes01.etfuturum.api.mappings.RegistryMapping");
            getOre = registry.getMethod("getOre", Block.class, int.class);
            mappingObject = mapping.getMethod("getObject");
            mappingMeta = mapping.getMethod("getMeta");
        } catch (ReflectiveOperationException | LinkageError e) {
            getOre = null;
        }
        DEEP_ORE_CACHE.clear();
        AkashicBellow.LOG.info(
            "Deep layer: fill={}:{}, tuff={}, copper={}, deepslate ore registry={}",
            Block.blockRegistry.getNameForObject(fill),
            fillMeta,
            tuff != null,
            copperOre != null,
            getOre != null);
    }

    /** True for blocks that count as deepslate for ore selection (the deep fill when it is not stone, and tuff). */
    public static boolean isDeepHost(Block block, int meta) {
        return block == fill && meta == fillMeta && block != Blocks.stone || block == tuff && tuff != null;
    }

    /** Stone, including a configured stone fill with another metadata (which is not a deep host). */
    public static boolean isStoneHost(Block block, int meta) {
        return block == Blocks.stone && (meta == 0 || block == fill && meta == fillMeta);
    }

    /** Deepslate variant of an ore from Et Futurum's {@code DeepslateOreRegistry}, or the ore itself. */
    public static synchronized BlockAndMeta deepVariant(Block ore, int meta) {
        long key = (long) Block.getIdFromBlock(ore) << 32 | meta & 0xFFFFFFFFL;
        BlockAndMeta cached = DEEP_ORE_CACHE.get(key);
        if (cached != null) return cached;
        BlockAndMeta result = new BlockAndMeta(ore, meta);
        if (getOre != null) {
            try {
                Object mapping = getOre.invoke(null, ore, meta);
                if (mapping != null) {
                    Block deep = (Block) mappingObject.invoke(mapping);
                    if (deep != null) result = new BlockAndMeta(deep, (Integer) mappingMeta.invoke(mapping));
                }
            } catch (ReflectiveOperationException e) {
                AkashicBellow.LOG.warn(
                    "Et Futurum deepslate ore lookup failed for {}:{}",
                    Block.blockRegistry.getNameForObject(ore),
                    meta,
                    e);
            }
        }
        DEEP_ORE_CACHE.put(key, result);
        return result;
    }

    public static final class BlockAndMeta {

        public final Block block;
        public final int meta;

        public BlockAndMeta(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }
    }
}
