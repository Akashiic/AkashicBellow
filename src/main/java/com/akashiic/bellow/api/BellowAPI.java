package com.akashiic.bellow.api;

import java.util.List;

import net.minecraft.util.LongHashMap;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.gen.DeepGenerators;
import com.akashiic.bellow.mixin.early.common.PlayerManagerAccessor;
import com.akashiic.bellow.net.IDeepPlayerInstance;

/**
 * Stable entry points for other mods (deep cave generators, map mods, etc.).
 */
public final class BellowAPI {

    /**
     * Version of this API, raised whenever something is added. 2: deep generators ({@link #registerGenerator}) and
     * {@link #resendDeepSections}. Read at runtime: call {@link #apiVersion()} (a mod compiled against version 2 that
     * runs with version 1 gets a {@code NoSuchMethodError} it can catch, instead of a value copied in at compile time).
     */
    public static int apiVersion() {
        return 2;
    }

    /** Same as {@link #apiVersion()}; not a compile-time constant, so javac does not copy it into other mods. */
    public static final int API_VERSION = apiVersion();

    private BellowAPI() {}

    /** Lowest valid block Y (inclusive). 0 for worlds without deep layers. */
    public static int getMinY(World world) {
        return world == null ? 0 : ((IDeepWorld) world).bellow$getMinY();
    }

    public static boolean hasDeepLayers(World world) {
        return getMinY(world) < 0;
    }

    /**
     * Returns the storage of a deep section ({@code minY >> 4 <= sectionY < 0}) or {@code null}; sections below the
     * world floor (dormant data the world itself never reads) are not exposed. Do not cache the result across ticks:
     * chunks may reload.
     */
    public static ExtendedBlockStorage getDeepSection(Chunk chunk, int sectionY) {
        int slot = DeepConstants.slot(sectionY);
        if (slot < 0 || sectionY << 4 < getMinY(chunk.worldObj)) return null;
        ExtendedBlockStorage[] deep = ((IDeepChunk) chunk).bellow$peekDeepSections();
        return deep == null ? null : deep[slot];
    }

    /**
     * Registers a deep generator under a unique id (conventionally {@code modid:name}). Allowed until the first server
     * starts: register during preInit, init or postInit. Generators run in registration order; the list is logged.
     *
     * @throws IllegalArgumentException for a duplicate id
     * @throws IllegalStateException    after registration closed
     */
    public static void registerGenerator(String id, IDeepGenerator generator) {
        DeepGenerators.register(id, generator);
    }

    /** Ids of the registered deep generators, in the order they run. */
    public static List<String> getGeneratorIds() {
        return DeepGenerators.ids();
    }

    /**
     * Sends deep sections of a chunk again to the players that already received it, for blocks written straight into
     * the sections (writes through {@code World.setBlock} are sent by the world itself, and
     * {@link IDeepRegion#setBlock}
     * is sent when decoration ends). Players that receive the chunk later get it whole anyway. Does not mark the chunk
     * for saving: call {@link Chunk#setChunkModified()} too.
     *
     * @param slotMask bit {@link DeepConstants#slot slot(sectionY)} set for each section to send
     */
    public static void resendDeepSections(WorldServer world, Chunk chunk, int slotMask) {
        if (slotMask == 0) return;
        LongHashMap instances = ((PlayerManagerAccessor) world.getPlayerManager()).bellow$getPlayerInstances();
        long key = (long) chunk.xPosition + 2147483647L | (long) chunk.zPosition + 2147483647L << 32;
        Object instance = instances.getValueByKey(key);
        if (instance != null) ((IDeepPlayerInstance) instance).bellow$resendDeep(slotMask);
    }
}
