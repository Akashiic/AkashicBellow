package com.akashiic.bellow.api;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Implemented by {@link net.minecraft.world.chunk.Chunk} through a mixin.
 * <p>
 * Deep sections live outside the vanilla {@code storageArrays}: index
 * {@code (y >> 4) + DeepConstants.MAX_DEEP_SECTIONS}
 * for {@code y < 0}. The array is allocated lazily and always has {@link DeepConstants#MAX_DEEP_SECTIONS} slots so that
 * storage never depends on the (lazily resolved) floor of the world. Slots below the world floor are kept dormant and
 * saved back untouched.
 */
public interface IDeepChunk {

    /** Never null; allocates on first call. */
    ExtendedBlockStorage[] bellow$getDeepSections();

    /** May be null when the chunk never had deep data. */
    ExtendedBlockStorage[] bellow$peekDeepSections();

    void bellow$setDeepSections(ExtendedBlockStorage[] sections);

    /**
     * One of {@link DeepConstants#GEN_NONE}, {@link DeepConstants#GEN_TERRAIN}, {@link DeepConstants#GEN_DECORATED}.
     */
    byte bellow$getGenState();

    void bellow$setGenState(byte state);

    /** True when the chunk was read from disk (as opposed to freshly generated) during this session. */
    boolean bellow$isFromDisk();

    void bellow$setFromDisk(boolean fromDisk);
}
