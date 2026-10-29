package com.akashiic.bellow.gen;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.akashiic.bellow.AkashicBellow;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Blocks the core needs below Y 0, resolved after every mod registered its blocks: the hosts of the vein primitives
 * ({@link com.akashiic.bellow.api.IDeepRegion#placeOre}) and the deepslate variants of ores from Et Futurum's
 * {@code DeepslateOreRegistry}. Et Futurum is optional and accessed by registry name and reflection only. What the deep
 * layer is made of is decided by its generators, not here.
 */
public final class DeepBlocks {

    /** Et Futurum's deepslate, or null. A deep host, and what replaces the legacy bedrock at Y 0..4 (else stone). */
    public static Block deepslate;
    /** Null when Et Futurum's tuff is not available. */
    public static Block tuff;

    private static Method getOre;
    private static Method mappingObject;
    private static Method mappingMeta;
    private static final Map<Long, BlockAndMeta> DEEP_ORE_CACHE = new HashMap<>();

    private DeepBlocks() {}

    public static void resolve() {
        deepslate = GameRegistry.findBlock("etfuturum", "deepslate");
        tuff = GameRegistry.findBlock("etfuturum", "tuff");
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
            "Deep layer blocks: deepslate={}, tuff={}, deepslate ore registry={}",
            deepslate != null,
            tuff != null,
            getOre != null);
    }

    /** Hosts that take the deepslate variant of an ore: Et Futurum's deepslate and tuff. */
    public static boolean isDeepHost(Block block, int meta) {
        return block != null && (block == deepslate && meta == 0 || block == tuff);
    }

    /** Hosts that take the ore itself: plain stone. */
    public static boolean isStoneHost(Block block, int meta) {
        return block == Blocks.stone && meta == 0;
    }

    /** What replaces the legacy bedrock at Y 0..4: deepslate when Et Futurum provides it, else stone. */
    public static Block legacyReplacement() {
        return deepslate != null ? deepslate : Blocks.stone;
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
