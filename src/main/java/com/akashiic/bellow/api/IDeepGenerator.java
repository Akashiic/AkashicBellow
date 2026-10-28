package com.akashiic.bellow.api;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * A generator that shapes the deep layer ({@code minY <= y < 0}) together with Bellow, registered with
 * {@link BellowAPI#registerGenerator}. Every method runs on the server thread, for dimensions with deep layers only,
 * and has an empty default. Generators run by priority, then id; an exception is logged and the chunk goes on (a stack
 * overflow or another {@code VirtualMachineError} is not caught: it crashes the server with a report, because the
 * frames it unwound may have been nested chunk loads).
 * <p>
 * Order for one chunk, mirroring 1.18 (carvers, then features, then light):
 * <ol>
 * <li>Bellow fills the deep layer (deep stone, bedrock floor) and replaces the legacy bedrock.</li>
 * <li>{@link #generateTerrain} of every generator.</li>
 * <li>Once the chunk and its 8 neighbours have their terrain: Bellow's ores, tuff and gravel (veins that discard on
 * air respect what step 2 carved), then {@link #decorate} of every generator. The chunk is marked decorated.</li>
 * <li>After the world tick: the changed sections are sent again to players who already received them, then
 * {@link #onDecorated}.</li>
 * <li>After the world tick, once the 5x5 chunks around it are loaded and decorated: Bellow lights what the generators
 * placed (block light of every light source of the chunk, below 0), then {@link #onLit}.</li>
 * </ol>
 * Each step runs once per chunk, ever: what each generator did is saved with the chunk
 * ({@link BellowAPI#getGeneratorStages}), so a step that could not run before the chunk was unloaded (or the server
 * stopped) runs when it loads again. Chunks whose deep layer was generated before a generator was installed are never
 * handed to it, so it cannot cut through what players built there.
 */
public interface IDeepGenerator {

    /**
     * Whether this generator works in a world (a dimension with deep layers). Asked once per world, when it is first
     * generated in. Default: every such world.
     *
     * @since API 3
     */
    default boolean appliesTo(WorldServer world) {
        return true;
    }

    /**
     * Version of what this generator produces (its settings included, when they change the shape of the world).
     * Recorded per dimension and per decorated chunk; Bellow warns when the generators of a dimension change between
     * sessions. Default: none.
     *
     * @since API 3
     */
    default String version() {
        return "";
    }

    /**
     * Terrain of one chunk, right after Bellow filled it, from inside {@code Chunk.onChunkLoad}.
     * <p>
     * Contract: touch this chunk only (no neighbour, no {@code World} block or tile entity access, no entities), only
     * {@code minY..-1}, through {@link BellowAPI#getDeepSection} (every section of that range exists at this point).
     * Blocks only: no tile entities, no light or neighbour updates (the layer is unlit stone at this point; light
     * sources are lit by the light stage, see {@link #onLit}). Carve what is still the fill
     * ({@link BellowAPI#isDeepFill}, {@link BellowAPI#isBaseStone}): on retrogen, a deep layer may already hold blocks
     * players or other mods placed. Must be a function of the seed and the chunk position, so chunk borders match
     * whatever order chunks are generated in.
     *
     * @param retrogen true when the chunk was saved before it had a deep layer (a chunk from an explored world that
     *                 gets its deep layer now); its blocks at {@code y >= 0} may be player-built
     */
    default void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {}

    /**
     * Features of the centre chunk of a 3x3 region, after Bellow's ores. Origins belong in the centre chunk; writes
     * may reach the 8 neighbours (all have their terrain) through {@link IDeepRegion#setBlock},
     * {@link IDeepRegion#placeOre} and {@link IDeepRegion#placeBlob}, which only write {@code minY..-1} and send the
     * changes to players. No light updates here either; no World writes (World reads past the region load chunks).
     * <p>
     * "After Bellow's ores" covers the veins of this chunk and of neighbours decorated before it; a neighbour decorated
     * later places its own veins, which may reach into this chunk (as in 1.18). Veins never replace air.
     * <p>
     * A neighbour decorated before ({@link IDeepRegion#genStateAt}) may be old and lived in. When a generator is added
     * to a world that already has deep layers, the chunks at the edge of what was generated before (terrain done, not
     * yet decorated) reach this step without having gone through this generator's {@link #generateTerrain}
     * ({@link BellowAPI#getGeneratorStages} without {@link DeepConstants#STAGE_TERRAIN}): decorate what is found rather
     * than assuming it.
     */
    default void decorate(IDeepRegion region) {}

    /**
     * After the chunk was decorated and its changed sections sent, from the end of a world tick (outside any chunk
     * load). The chunk is loaded; its neighbours usually are, but check ({@code chunkExists}) before relying on them:
     * World access to an unloaded chunk loads or generates it (which can decorate more chunks, and call this again).
     * Light of World writes is updated only when the chunks within 17 blocks are loaded: place light sources in
     * {@link #onLit}, or through {@link IDeepRegion#setBlock} and let the light stage light them.
     */
    default void onDecorated(WorldServer world, Chunk chunk) {}

    /**
     * After Bellow's light stage lit the chunk (the block light of every light source in its deep layer, spread over
     * the 3x3 around it), from the end of a world tick. At this point the 5x5 chunks around it are loaded and
     * decorated: World writes in this chunk update light and notify neighbours normally (a fluid that should start
     * flowing, a block that needs a neighbour update), and no decoration will write into the 3x3 around it any more.
     * Only for chunks this generator decorated.
     *
     * @since API 3
     */
    default void onLit(WorldServer world, Chunk chunk) {}
}
