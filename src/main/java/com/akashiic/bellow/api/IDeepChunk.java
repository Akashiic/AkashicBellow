package com.akashiic.bellow.api;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Implemented by {@link net.minecraft.world.chunk.Chunk} through a mixin. Read-only: the deep state of a chunk changes
 * only through Bellow (generation stages, {@link IDeepRegion}, {@code World} writes).
 * <p>
 * Deep sections live outside the vanilla {@code storageArrays}: index
 * {@code (y >> 4) + DeepConstants.MAX_DEEP_SECTIONS}
 * for {@code y < 0}. The array is allocated lazily and always has {@link DeepConstants#MAX_DEEP_SECTIONS} slots so that
 * storage never depends on the (lazily resolved) floor of the world. Slots below the world floor are kept dormant and
 * saved back untouched.
 */
public interface IDeepChunk {

    /**
     * The raw slot array, null when the chunk never had deep data. Includes the dormant slots below the floor: prefer
     * {@link BellowAPI#getDeepSection}, which hides them.
     */
    ExtendedBlockStorage[] bellow$peekDeepSections();

    /**
     * One of {@link DeepConstants#GEN_NONE}, {@link DeepConstants#GEN_TERRAIN}, {@link DeepConstants#GEN_DECORATED},
     * or a later stage: compare with {@code >=} ({@link BellowAPI#isDecorated}).
     */
    byte bellow$getGenState();

    /** True when the chunk was read from disk (as opposed to freshly generated) during this session. */
    boolean bellow$isFromDisk();
}
