package com.akashiic.bellow.gen;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;

/**
 * Light stage of the deep layer: the block light of what deep generators placed (lava, glowing plants, crystals),
 * which the generation steps write without light ({@link com.akashiic.bellow.api.IDeepRegion#setBlock}).
 * <p>
 * A chunk is lit once the 5x5 chunks around it are loaded and decorated ({@link DeepStages}): from then on no
 * decoration writes into the 3x3 its light can reach (a decoration writes at most one chunk away from its centre), so
 * one flood fill from the light sources of the chunk gives final values. Light 15 travels at most 14 blocks, so the
 * fill
 * never leaves the 3x3. The fill applies the rule of {@code World.computeLightValue} ({@code max(emission, neighbour -
 * max(1, opacity))}, opaque emitters let light in) and only raises values, so the fills of all chunks add up to the
 * light of all their sources in any order, and values the World already computed (player-placed torches) stay. Only
 * {@code minY..-1} is lit: the 1.7.10 world above 0 is never touched.
 */
final class DeepLight {

    /** Per block: whether its light value or opacity depends on the position (a subclass overrides the Forge hook). */
    private static final Map<Block, Integer> DYNAMIC = new IdentityHashMap<>();
    private static final int DYNAMIC_LIGHT = 1;
    private static final int DYNAMIC_OPACITY = 2;

    private static final int[] DX = { 0, 0, 0, 0, -1, 1 };
    private static final int[] DY = { -1, 1, 0, 0, 0, 0 };
    private static final int[] DZ = { 0, 0, -1, 1, 0, 0 };

    private final WorldServer world;
    private final Chunk[] chunks;
    private final int originX;
    private final int originZ;
    private final int minY;
    /** Sections of the 3x3, null where a chunk has none (or is read-only: deep data of a newer format). */
    private final ExtendedBlockStorage[][] sections;
    private final int[] touched = new int[9];

    private DeepLight(WorldServer world, Chunk[] chunks, int cx, int cz, int minY) {
        this.world = world;
        this.chunks = chunks;
        this.originX = (cx - 1) << 4;
        this.originZ = (cz - 1) << 4;
        this.minY = minY;
        int count = -minY >> 4;
        this.sections = new ExtendedBlockStorage[9][count];
        for (int i = 0; i < 9; i++) {
            for (int s = 0; s < count; s++) this.sections[i][s] = BellowAPI.getDeepSection(chunks[i], (minY >> 4) + s);
        }
    }

    /**
     * Lights the sources of {@code chunks[4]} over the 3x3 {@code chunks} and reports the changed sections.
     *
     * @return the number of light values raised
     */
    static int light(WorldServer world, Chunk[] chunks, int minY, DeepStages.Resend resend) {
        DeepLight fill = new DeepLight(world, chunks, chunks[4].xPosition, chunks[4].zPosition, minY);
        int raised = fill.run();
        for (int i = 0; i < 9; i++) {
            if (fill.touched[i] == 0) continue;
            chunks[i].setChunkModified();
            resend.add(chunks[i], fill.touched[i]);
        }
        return raised;
    }

    private ExtendedBlockStorage section(int lx, int y, int lz) {
        return this.sections[(lz >> 4) * 3 + (lx >> 4)][(y - this.minY) >> 4];
    }

    private int index(int lx, int y, int lz) {
        return ((y - this.minY) * 48 + lz) * 48 + lx;
    }

    private int run() {
        int raised = 0;
        int[] queue = new int[4096];
        int head = 0, tail = 0;
        // sources of the centre chunk (local x/z 16..31 of the 48 x 48 region)
        for (int y = this.minY; y < 0; y++) {
            for (int lz = 16; lz < 32; lz++) {
                for (int lx = 16; lx < 32; lx++) {
                    ExtendedBlockStorage ebs = this.section(lx, y, lz);
                    if (ebs == null) continue;
                    Block b = ebs.getBlockByExtId(lx & 15, y & 15, lz & 15);
                    if (b == Blocks.air) continue;
                    int light = emission(b, this.world, this.originX + lx, y, this.originZ + lz);
                    if (light > 0 && this.raise(ebs, lx, y, lz, light)) {
                        raised++;
                        if (tail == queue.length) queue = Arrays.copyOf(queue, queue.length * 2);
                        queue[tail++] = this.index(lx, y, lz);
                    }
                }
            }
        }
        while (head < tail) {
            int p = queue[head++];
            int lx = p % 48, lz = p / 48 % 48, y = p / (48 * 48) + this.minY;
            int value = this.section(lx, y, lz)
                .getExtBlocklightValue(lx & 15, y & 15, lz & 15);
            if (value <= 1) continue;
            for (int side = 0; side < 6; side++) {
                int nx = lx + DX[side], ny = y + DY[side], nz = lz + DZ[side];
                if (nx < 0 || nz < 0 || nx >= 48 || nz >= 48 || ny < this.minY || ny >= 0) continue;
                ExtendedBlockStorage ebs = this.section(nx, ny, nz);
                if (ebs == null) continue;
                Block b = ebs.getBlockByExtId(nx & 15, ny & 15, nz & 15);
                int wx = this.originX + nx, wz = this.originZ + nz;
                int opacity = opacity(b, this.world, wx, ny, wz);
                // World.computeLightValue: an opaque block that emits light lets light in
                if (opacity >= 15 && b != Blocks.air && emission(b, this.world, wx, ny, wz) > 0) opacity = 1;
                if (opacity < 1) opacity = 1;
                if (opacity >= 15) continue;
                int next = value - opacity;
                if (next > 0 && this.raise(ebs, nx, ny, nz, next)) {
                    raised++;
                    if (tail == queue.length) {
                        // reuse the consumed head before growing
                        System.arraycopy(queue, head, queue, 0, tail - head);
                        tail -= head;
                        head = 0;
                        if (tail == queue.length) queue = Arrays.copyOf(queue, queue.length * 2);
                    }
                    queue[tail++] = this.index(nx, ny, nz);
                }
            }
        }
        return raised;
    }

    private boolean raise(ExtendedBlockStorage ebs, int lx, int y, int lz, int value) {
        if (ebs.getExtBlocklightValue(lx & 15, y & 15, lz & 15) >= value) return false;
        ebs.setExtBlocklightValue(lx & 15, y & 15, lz & 15, value);
        this.touched[(lz >> 4) * 3 + (lx >> 4)] |= DeepConstants.slotBit(y >> 4);
        return true;
    }

    // ------------------------------------------------------------------ light properties

    private static int dynamic(Block b) {
        Integer v = DYNAMIC.get(b);
        if (v == null) {
            int flags = 0;
            if (overrides(b.getClass(), "getLightValue")) flags |= DYNAMIC_LIGHT;
            if (overrides(b.getClass(), "getLightOpacity")) flags |= DYNAMIC_OPACITY;
            DYNAMIC.put(b, v = flags);
        }
        return v;
    }

    /** Whether a block class overrides a positional Forge hook (Forge names: the same in production). */
    private static boolean overrides(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Block.class; k = k.getSuperclass()) {
            try {
                k.getDeclaredMethod(name, IBlockAccess.class, int.class, int.class, int.class);
                return true;
            } catch (NoSuchMethodException ignored) {
                // keep looking up the hierarchy
            } catch (LinkageError e) {
                return true; // cannot tell: ask the block every time
            }
        }
        return false;
    }

    static int emission(Block b, WorldServer world, int x, int y, int z) {
        return (dynamic(b) & DYNAMIC_LIGHT) != 0 ? b.getLightValue(world, x, y, z) : b.getLightValue();
    }

    static int opacity(Block b, WorldServer world, int x, int y, int z) {
        return (dynamic(b) & DYNAMIC_OPACITY) != 0 ? b.getLightOpacity(world, x, y, z) : b.getLightOpacity();
    }
}
