package com.akashiic.bellow.api;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * A generator that shapes the deep layer ({@code minY <= y < 0}) together with Bellow, registered with
 * {@link BellowAPI#registerGenerator}. Every method runs on the server thread, for dimensions with deep layers only,
 * and has an empty default. Generators run in registration order; an exception is logged and the chunk goes on.
 * <p>
 * Order for one chunk, mirroring 1.18 (carvers, then features):
 * <ol>
 * <li>Bellow fills the deep layer (deep stone, bedrock floor) and replaces the legacy bedrock.</li>
 * <li>{@link #generateTerrain} of every generator.</li>
 * <li>Once the chunk and its 8 neighbours have their terrain: Bellow's ores, tuff and gravel (veins that discard on
 * air respect what step 2 carved), then {@link #decorate} of every generator.</li>
 * <li>The chunk is marked decorated and saved that way; changed sections are sent again to players who already
 * received them; then {@link #onDecorated}.</li>
 * </ol>
 * Each step runs once per chunk, ever. Chunks whose deep layer was generated before a generator was installed are
 * never handed to it, so it cannot cut through what players built there.
 */
public interface IDeepGenerator {

    /**
     * Terrain of one chunk, right after Bellow filled it, from inside {@code Chunk.onChunkLoad}.
     * <p>
     * Contract: touch this chunk only (no neighbour, no {@code World} block or tile entity access, no entities), only
     * {@code minY..-1}, through {@link BellowAPI#getDeepSection} or {@link IDeepChunk#bellow$getDeepSections()}. Blocks
     * only: no tile entities, no light or neighbour updates (the layer is unlit stone at this point; blocks that emit
     * light are lit later, see {@link #onDecorated}). Must be a function of the seed and the chunk position, so chunk
     * borders match whatever order chunks are generated in.
     *
     * @param retrogen true when the chunk was saved before it had a deep layer (a chunk from an explored world that
     *                 gets its deep layer now); its blocks at {@code y >= 0} may be player-built
     */
    default void generateTerrain(Chunk chunk, int minY, long seed, boolean retrogen) {}

    /**
     * Features of the centre chunk of a 3x3 region, after Bellow's ores. Origins belong in the centre chunk; writes
     * may reach the 8 neighbours (all have their terrain) through {@link IDeepRegion#setBlock}, which only writes
     * {@code minY..-1} and sends the changes to players. No light updates here either.
     * <p>
     * When a generator is added to a world that already has deep layers, the chunks at the edge of what was generated
     * before (terrain done, not yet decorated) reach this step without having gone through this generator's
     * {@link #generateTerrain}: decorate what is found rather than assuming it.
     */
    default void decorate(IDeepRegion region) {}

    /**
     * After the chunk was marked decorated and its changed sections were sent. The world is fully usable here: this is
     * the place to light blocks placed without light (for instance with {@code World.func_147451_t}, which needs the
     * chunks within 17 blocks loaded), or to schedule block ticks.
     */
    default void onDecorated(WorldServer world, Chunk chunk) {}
}
