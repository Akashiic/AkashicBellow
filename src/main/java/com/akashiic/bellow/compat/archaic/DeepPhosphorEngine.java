package com.akashiic.bellow.compat.archaic;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.embeddedt.archaicfix.lighting.world.lighting.LightingEngine;

import com.akashiic.bellow.api.BellowAPI;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Phosphor lighting for worlds with deep layers.
 * <p>
 * Same algorithm as ArchaicFix's {@link LightingEngine} (a port of Phosphor), with one change: ArchaicFix packs Y into
 * 8 bits ({@code y << 52}), which cannot represent Y below 0. Here positions are packed as
 * 
 * <pre>
 *   bits  0..25  z + 2^25
 *   bits 26..51  x + 2^25
 *   bits 52..61  y + 512     (10 bits: -512 .. 511)
 * </pre>
 * 
 * and out-of-world neighbours are detected by decoding and range checking against {@code [minY, 255]}. Initial
 * brightenings are kept in one queue per light level instead of storing the level in the high bits.
 * <p>
 * Extends ArchaicFix's engine only so that it can be stored in the field ArchaicFix merges into {@link World}; none
 * of the superclass state is used.
 */
public final class DeepPhosphorEngine extends LightingEngine {

    private static final int MAX_SCHEDULED = 1 << 22;

    private final World world;
    private final int minY;
    private final Thread owner = Thread.currentThread();
    private final ReentrantLock lock = new ReentrantLock();

    private final LongQueue[] scheduled = { new LongQueue(), new LongQueue() };
    private final LongQueue[] initialBrightenings = newLevelQueues();
    private final LongQueue initialDarkenings = new LongQueue();
    private final LongQueue[] darkenings = newLevelQueues();
    private final LongQueue[] brightenings = newLevelQueues();
    private boolean updating;

    // cursor
    private long cur;
    private int curX, curY, curZ;
    private Chunk curChunk;
    private long curChunkId = -1L;
    private boolean neighborsValid;
    private final Neighbor[] neighbors = new Neighbor[6];

    public DeepPhosphorEngine(World world) {
        super(world);
        this.world = world;
        this.minY = BellowAPI.getMinY(world);
        for (int i = 0; i < 6; i++) this.neighbors[i] = new Neighbor();
    }

    private static LongQueue[] newLevelQueues() {
        LongQueue[] q = new LongQueue[16];
        for (int i = 0; i < 16; i++) q[i] = new LongQueue();
        return q;
    }

    // ------------------------------------------------------------------ ILightingEngine

    @Override
    public void scheduleLightUpdate(EnumSkyBlock type, int x, int y, int z) {
        if (y < this.minY || y > 255) return;
        this.acquire();
        try {
            LongQueue q = this.scheduled[type.ordinal()];
            q.add(LightPacking.encode(x, y, z));
            if (q.size() >= MAX_SCHEDULED) this.processLightUpdatesForType(type);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void processLightUpdates() {
        this.processLightUpdatesForType(EnumSkyBlock.Sky);
        this.processLightUpdatesForType(EnumSkyBlock.Block);
    }

    @Override
    public void processLightUpdatesForType(EnumSkyBlock type) {
        if (this.world.isRemote && !isClientThread()) return;
        LongQueue q = this.scheduled[type.ordinal()];
        if (q.isEmpty()) return;
        this.acquire();
        try {
            this.process(type, q);
        } finally {
            this.lock.unlock();
        }
    }

    @SideOnly(Side.CLIENT)
    private static boolean isClientThread() {
        return Minecraft.getMinecraft()
            .func_152345_ab();
    }

    private void acquire() {
        if (!this.lock.tryLock()) {
            if (Thread.currentThread() != this.owner) {
                com.akashiic.bellow.AkashicBellow.LOG.warn(
                    "Lighting of {} accessed from thread '{}' (owner '{}')",
                    this.world.provider.getDimensionName(),
                    Thread.currentThread()
                        .getName(),
                    this.owner.getName());
            }
            this.lock.lock();
        }
    }

    // ------------------------------------------------------------------ algorithm (Phosphor)

    private void process(EnumSkyBlock type, LongQueue queue) {
        if (this.updating) throw new IllegalStateException("Already processing light updates");
        this.updating = true;
        this.curChunkId = -1L;
        this.world.theProfiler.startSection("lighting");
        this.world.theProfiler.startSection("checking");

        for (int i = 0, n = queue.size(); i < n; i++) {
            this.moveCursor(queue.get(i));
            if (this.curChunk == null) continue;
            int oldLight = this.cursorLight(type);
            int newLight = this.computeCursorLight(type);
            if (oldLight < newLight) {
                this.initialBrightenings[newLight].add(this.cur);
            } else if (oldLight > newLight) {
                this.initialDarkenings.add(this.cur);
            }
        }
        queue.clear();

        for (int level = 15; level > 0; level--) {
            LongQueue q = this.initialBrightenings[level];
            for (int i = 0, n = q.size(); i < n; i++) {
                this.moveCursor(q.get(i));
                if (this.curChunk != null && level > this.cursorLight(type))
                    this.enqueueBrightening(this.cur, this.curX, this.curY, this.curZ, level, this.curChunk, type);
            }
            q.clear();
        }

        for (int i = 0, n = this.initialDarkenings.size(); i < n; i++) {
            this.moveCursor(this.initialDarkenings.get(i));
            if (this.curChunk == null) continue;
            int oldLight = this.cursorLight(type);
            if (oldLight != 0)
                this.enqueueDarkening(this.cur, this.curX, this.curY, this.curZ, oldLight, this.curChunk, type);
        }
        this.initialDarkenings.clear();
        this.world.theProfiler.endSection();

        for (int curLight = 15; curLight >= 0; curLight--) {
            this.world.theProfiler.startSection("darkening");
            LongQueue dq = this.darkenings[curLight];
            for (int i = 0; i < dq.size(); i++) {
                this.moveCursor(dq.get(i));
                if (this.curChunk == null || this.cursorLight(type) >= curLight) continue;
                Block state = this.curChunk.getBlock(this.curX & 15, this.curY, this.curZ & 15);
                int luminosity = this.luminosity(state, type);
                int opacity = luminosity >= 14 ? 1 : this.opacity(state, this.curX, this.curY, this.curZ);
                if (this.computeLight(luminosity, opacity, type) < curLight) {
                    int newLight = luminosity;
                    this.fetchNeighbors(type);
                    for (Neighbor nb : this.neighbors) {
                        if (nb.chunk == null || nb.light == 0) continue;
                        Block nState = nb.chunk.getBlock(nb.x & 15, nb.y, nb.z & 15);
                        if (curLight - this.opacity(nState, nb.x, nb.y, nb.z) >= nb.light) {
                            this.enqueueDarkening(nb.key, nb.x, nb.y, nb.z, nb.light, nb.chunk, type);
                        } else {
                            newLight = Math.max(newLight, nb.light - opacity);
                        }
                    }
                    this.enqueueBrightening(this.cur, this.curX, this.curY, this.curZ, newLight, this.curChunk, type);
                } else {
                    this.enqueueBrightening(this.cur, this.curX, this.curY, this.curZ, curLight, this.curChunk, type);
                }
            }
            dq.clear();

            this.world.theProfiler.endStartSection("brightening");
            LongQueue bq = this.brightenings[curLight];
            for (int i = 0; i < bq.size(); i++) {
                this.moveCursor(bq.get(i));
                if (this.curChunk == null || this.cursorLight(type) != curLight) continue;
                this.world.func_147479_m(this.curX, this.curY, this.curZ);
                if (curLight > 1) this.spread(curLight, type);
            }
            bq.clear();
            this.world.theProfiler.endSection();
        }

        this.world.theProfiler.endSection();
        this.updating = false;
    }

    private void spread(int curLight, EnumSkyBlock type) {
        this.fetchNeighbors(type);
        for (Neighbor nb : this.neighbors) {
            if (nb.chunk == null) continue;
            Block nState = nb.chunk.getBlock(nb.x & 15, nb.y, nb.z & 15);
            int newLight = curLight - this.opacity(nState, nb.x, nb.y, nb.z);
            if (newLight > nb.light) this.enqueueBrightening(nb.key, nb.x, nb.y, nb.z, newLight, nb.chunk, type);
        }
    }

    private int computeCursorLight(EnumSkyBlock type) {
        Block state = this.curChunk.getBlock(this.curX & 15, this.curY, this.curZ & 15);
        int luminosity = this.luminosity(state, type);
        int opacity = luminosity >= 14 ? 1 : this.opacity(state, this.curX, this.curY, this.curZ);
        return this.computeLight(luminosity, opacity, type);
    }

    private int computeLight(int luminosity, int opacity, EnumSkyBlock type) {
        if (luminosity >= 15 - opacity) return luminosity;
        int newLight = luminosity;
        this.fetchNeighbors(type);
        for (Neighbor nb : this.neighbors) {
            if (nb.chunk != null) newLight = Math.max(nb.light - opacity, newLight);
        }
        return newLight;
    }

    private void enqueueBrightening(long key, int x, int y, int z, int light, Chunk chunk, EnumSkyBlock type) {
        this.brightenings[light].add(key);
        chunk.setLightValue(type, x & 15, y, z & 15, light);
    }

    private void enqueueDarkening(long key, int x, int y, int z, int light, Chunk chunk, EnumSkyBlock type) {
        this.darkenings[light].add(key);
        chunk.setLightValue(type, x & 15, y, z & 15, 0);
    }

    private void fetchNeighbors(EnumSkyBlock type) {
        if (this.neighborsValid) return;
        this.neighborsValid = true;
        for (int i = 0; i < 6; i++) {
            Neighbor nb = this.neighbors[i];
            long key = this.cur + LightPacking.NEIGHBOR_SHIFTS[i];
            nb.key = key;
            nb.x = LightPacking.decodeX(key);
            nb.y = LightPacking.decodeY(key);
            nb.z = LightPacking.decodeZ(key);
            if (nb.y < this.minY || nb.y > 255) {
                nb.chunk = null;
                continue;
            }
            nb.chunk = LightPacking.chunkId(key) == this.curChunkId ? this.curChunk
                : this.loadedChunk(nb.x >> 4, nb.z >> 4);
            nb.light = nb.chunk == null ? 0 : storedLight(nb.chunk, nb.x, nb.y, nb.z, type);
        }
    }

    private void moveCursor(long key) {
        this.cur = key;
        this.neighborsValid = false;
        this.curX = LightPacking.decodeX(key);
        this.curY = LightPacking.decodeY(key);
        this.curZ = LightPacking.decodeZ(key);
        long id = LightPacking.chunkId(key);
        if (id != this.curChunkId) {
            this.curChunk = this.loadedChunk(this.curX >> 4, this.curZ >> 4);
            this.curChunkId = id;
        }
    }

    private int cursorLight(EnumSkyBlock type) {
        return storedLight(this.curChunk, this.curX, this.curY, this.curZ, type);
    }

    private int luminosity(Block state, EnumSkyBlock type) {
        if (type == EnumSkyBlock.Sky) {
            return this.curChunk.canBlockSeeTheSky(this.curX & 15, this.curY, this.curZ & 15)
                ? EnumSkyBlock.Sky.defaultLightValue
                : 0;
        }
        return MathHelper.clamp_int(state.getLightValue(this.world, this.curX, this.curY, this.curZ), 0, 15);
    }

    private int opacity(Block state, int x, int y, int z) {
        return MathHelper.clamp_int(state.getLightOpacity(this.world, x, y, z), 1, 15);
    }

    private Chunk loadedChunk(int chunkX, int chunkZ) {
        IChunkProvider provider = this.world.getChunkProvider();
        return provider.chunkExists(chunkX, chunkZ) ? provider.provideChunk(chunkX, chunkZ) : null;
    }

    /** Light stored in the section, the same rule as ArchaicFix's {@code getCachedLightFor} but deep aware. */
    static int storedLight(Chunk chunk, int x, int y, int z, EnumSkyBlock type) {
        ExtendedBlockStorage ebs = y >= 0 ? chunk.getBlockStorageArray()[y >> 4]
            : BellowAPI.getDeepSection(chunk, y >> 4);
        if (ebs == null) {
            // Above the terrain a missing section is open sky; below Y 0 there is never sky.
            return type == EnumSkyBlock.Sky && y >= 0 && chunk.canBlockSeeTheSky(x & 15, y, z & 15)
                ? type.defaultLightValue
                : 0;
        }
        if (type == EnumSkyBlock.Sky)
            return chunk.worldObj.provider.hasNoSky ? 0 : ebs.getExtSkylightValue(x & 15, y & 15, z & 15);
        return type == EnumSkyBlock.Block ? ebs.getExtBlocklightValue(x & 15, y & 15, z & 15) : type.defaultLightValue;
    }

    private static final class Neighbor {

        Chunk chunk;
        int light;
        long key;
        int x, y, z;
    }

    /** Growable list of longs; iteration by index tolerates appends to other queues. */
    static final class LongQueue {

        private long[] data = new long[64];
        private int size;

        void add(long v) {
            if (this.size == this.data.length) this.data = Arrays.copyOf(this.data, this.size << 1);
            this.data[this.size++] = v;
        }

        long get(int i) {
            return this.data[i];
        }

        int size() {
            return this.size;
        }

        boolean isEmpty() {
            return this.size == 0;
        }

        void clear() {
            this.size = 0;
            if (this.data.length > 1 << 16) this.data = new long[64];
        }
    }
}
